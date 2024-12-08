package com.trendpulse.processors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;

import com.trendpulse.items.GlobalTrend;
import com.trendpulse.items.LocalTrend;
import com.trendpulse.items.Trend;
import com.trendpulse.schema.*;

public class TrendManagementProcessor extends KeyedProcessFunction<CharSequence, TrendEvent, TrendEvent> {
    private static final double SIMILARITY_THRESHOLD = 0.8; // Cosine similarity threshold
    private static String AZURE_OPENAI_ENDPOINT = "https://my-first-open-ai-service.openai.azure.com/";
    private static String AZURE_OPENAI_KEY = "uClNQwvESsEPxSFhKKonjSfIa8KDKUsyzLo7wl0rHzSpTI2qd40fJQQJ99AKACYeBjFXJ3w3AAABACOGgkTy";
    private transient OpenAIClient client;

    private final Map<String, LocalTrend> localTrends = new HashMap<>();
    private final Map<String, GlobalTrend> globalTrends = new HashMap<>();
    
    // localTrendId -> globalTrend 
    private final Map<String, GlobalTrend> localMergedTrends = new HashMap<>();
    
    int trendStatsWindowMinutes;
    private transient ListState<Long> scheduledWindows;

    public TrendManagementProcessor(int trendStatsWindowMinutes) {
        this.trendStatsWindowMinutes = trendStatsWindowMinutes;
    }

    @Override
    public void open(Configuration conf) throws Exception {
        this.scheduledWindows = getRuntimeContext().getListState(
            new ListStateDescriptor<>("scheduled_windows", Long.class)
        );

        client = new OpenAIClientBuilder()
            .endpoint(AZURE_OPENAI_ENDPOINT)
            .credential(new AzureKeyCredential(AZURE_OPENAI_KEY))
            .buildClient();
    }

    @Override
    public void processElement(TrendEvent event, Context ctx, Collector<TrendEvent> out) throws Exception {
        switch (event.getEventType()) {
            case TREND_ACTIVATED : {
                processTrendActivated(event, ctx, out);
                break;
            }
            case TREND_STATS: {
                processTrendStats(event, ctx, out);
                break;
            }
            case TREND_DEACTIVATED: {
                processTrendDeactivated(event, ctx, out);
                break;
            }
        }
    }

    private void processTrendActivated(TrendEvent event, Context ctx, Collector<TrendEvent> out) throws Exception {
        String trendId = event.getTrendId().toString();
        TrendActivatedInfo eventInfo = (TrendActivatedInfo) event.getInfo();
        String topic = event.getTopic().toString();

        // Initialize new local trend
        LocalTrend newTrend = initializeLocalTrend(eventInfo, topic, trendId, ((LocalTrendInfo) event.getTrendInfo()).getLocationId());
        // System.out.println("----------------------------------------");
        // System.out.println(Thread.currentThread().getName() + " - " + ctx.getCurrentKey() +  " - Incoming trend(" + newTrend.getId() + "): " + newTrend.getName() + " | time: " + Instant.ofEpochMilli(ctx.timerService().currentWatermark()).toString());
        System.out.println("Incoming trend(" + newTrend.getId() + "): " + newTrend.getName() + " | time: " + Instant.ofEpochMilli(ctx.timerService().currentWatermark()).toString());
        
        // First check global trends
        List<TrendWithSimilarity> similarGlobalTrends = findSimilarTrends(
            new ArrayList<>(globalTrends.values()), 
            newTrend
        );
        
        if (!similarGlobalTrends.isEmpty() && similarGlobalTrends.get(0).similarity >= SIMILARITY_THRESHOLD) {
            // System.out.println("Matched existing global trend");
            GlobalTrend mostSimilarGlobalTrend = (GlobalTrend) similarGlobalTrends.get(0).trend;
            localMergedTrends.put(newTrend.getId(), mostSimilarGlobalTrend);
            mostSimilarGlobalTrend.addLocalTrend(newTrend);
            return;
        }

        // Then check local trends
        List<TrendWithSimilarity> similarLocalTrends = findSimilarTrends(
            new ArrayList<>(localTrends.values()), 
            newTrend
        );
        
        // Filter by threshold and convert to LocalTrend objects
        Set<LocalTrend> matchingLocalTrends = new HashSet<>();
        for (TrendWithSimilarity similar : similarLocalTrends) {
            if (similar.similarity >= SIMILARITY_THRESHOLD) {
                matchingLocalTrends.add((LocalTrend) similar.trend);
            }
        }

        // System.out.println("Matching existing local trends: " + matchingLocalTrends.size());

        // Update similar trends relationships
        for (LocalTrend similarTrend : matchingLocalTrends) {
            newTrend.addSimilarTrend(similarTrend);
            similarTrend.addSimilarTrend(newTrend);
        }

        // If enough similar trends, create new global trend
        if (matchingLocalTrends.size() >= 2) { // 2 similar trends + new trend = 3 total
            System.out.println("New global trend initialized: " + Instant.ofEpochMilli(ctx.timerService().currentWatermark()));
            matchingLocalTrends.add(newTrend);
            initializeGlobalTrend(matchingLocalTrends);
        } else {
            // System.out.println("Stored as a local trend");
            // Store as local trend
            localTrends.put(trendId, newTrend);
        }
    }

    private void processTrendStats(TrendEvent event, Context ctx, Collector<TrendEvent> out) throws Exception {
        String trendId = event.getTrendId().toString();
        TrendStatsInfo eventInfo = (TrendStatsInfo) event.getInfo();
        
        WindowStats windowStats = eventInfo.getStats();
        Instant windowStart = Instant.ofEpochSecond(windowStats.getWindowStart());

        scheduleWindowEndCallback(ctx, windowStart);

        if (localMergedTrends.containsKey(trendId)) {
            localMergedTrends.get(trendId).addLocalTrendWindowStats(windowStart, windowStats);
            return;
        }

        if (localTrends.containsKey(trendId)) {
            localTrends.get(trendId).setWindowStats(windowStart, windowStats);
            return;
        }

        System.out.println(Thread.currentThread().getName() + " - " + ctx.getCurrentKey() +  " - Unknown trend id: " + trendId);
    }

    private void processTrendDeactivated(TrendEvent event, Context ctx, Collector<TrendEvent> out) {
    }

    private void scheduleWindowEndCallback(Context ctx, Instant windowStart) throws Exception {

        Instant windowEnd = windowStart.plus(trendStatsWindowMinutes, ChronoUnit.MINUTES);

        if (windowEnd.toEpochMilli() < ctx.timerService().currentWatermark()) {
            return;
        }

        long windowEndMillis = windowEnd.toEpochMilli();

        // Check if window end is already scheduled
        boolean isScheduled = false;
        for (Long scheduled : scheduledWindows.get()) {
            if (scheduled == windowEndMillis) {
                isScheduled = true;
                break;
            }
        }

        if (!isScheduled) {
            // System.out.println(Thread.currentThread().getName() + " " + ctx.getCurrentKey() + " Scheduled: " + windowStart.toString());
            // System.out.println("Scheduled: " + windowStart.toString());
            ctx.timerService().registerEventTimeTimer(windowEndMillis);
            scheduledWindows.add(windowEndMillis);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TrendEvent> out) throws Exception {
        // Remove this timestamp from scheduled windows
        List<Long> currentScheduled = new ArrayList<>();
        scheduledWindows.get().forEach(currentScheduled::add);
        currentScheduled.remove(timestamp);
        scheduledWindows.clear();
        
        currentScheduled.forEach(t -> {
            try {
                scheduledWindows.add(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Instant windowEnd = Instant.ofEpochMilli(timestamp);
        Instant windowStart = windowEnd.minus(trendStatsWindowMinutes, ChronoUnit.MINUTES);

        // System.out.println(Thread.currentThread().getName() + " " + ctx.getCurrentKey() + " onTimer: " + windowStart.toString());
        // System.out.println(" onTimer: " + windowStart.toString());

        for (GlobalTrend trend : globalTrends.values()) {

            if (!trend.getTopic().contentEquals(ctx.getCurrentKey())) {
                continue;
            }

            WindowStats windowStats = trend.getWindowStats(windowStart);
            if (windowStats == null) {
                continue;
            }

            // System.out.println(Thread.currentThread().getName() + " " + ctx.getCurrentKey() + " Emitted - trend: " + trend.getId() + " | timestamp: " + windowStart.toString() );
            // System.out.println("Emitted - trend: " + trend.getId() + " | timestamp: " + windowStart.toString() );

            TrendEvent event = new TrendEvent(
                EventType.TREND_STATS,
                trend.getId(),
                trend.getTopic(),
                new GlobalTrendInfo(trend.getLocationIds().stream().map(l -> new Location(l)).collect(Collectors.toList())),
                new TrendStatsInfo(windowStats)
            );

            out.collect(event);
        }
    }

    private static class TrendWithSimilarity {
        final Trend trend;
        final double similarity;

        TrendWithSimilarity(Trend trend, double similarity) {
            this.trend = trend;
            this.similarity = similarity;
        }
    }

    private List<TrendWithSimilarity> findSimilarTrends(List<? extends Trend> trends, Trend targetTrend) {
        List<TrendWithSimilarity> similarTrends = new ArrayList<>();
        
        for (Trend trend : trends) {
            double similarity = calculateCosineSimilarity(targetTrend.getCentroid(), trend.getCentroid());
            similarTrends.add(new TrendWithSimilarity(trend, similarity));
        }
        
        similarTrends.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        return similarTrends;
    }

    private LocalTrend initializeLocalTrend(TrendActivatedInfo eventInfo, String topic, String trendId, int locationId) {
        List<String> keywords = eventInfo.getKeywords().stream().map(k -> k.toString()).collect(Collectors.toList());
        double[] centroid = eventInfo.getCentroid().stream().mapToDouble(Double::doubleValue).toArray();;
        List<String> sampleMessages = eventInfo.getSampleMessages().stream().map(k -> k.toString()).collect(Collectors.toList());        

        String name = locationId + "__" + generateTrendName(keywords, sampleMessages);

        return new LocalTrend(trendId, name, topic, keywords, centroid, locationId, sampleMessages);
    }

    private void initializeGlobalTrend(Set<LocalTrend> trends) {
        String globalTrendId = UUID.randomUUID().toString();
        GlobalTrend globalTrend = new GlobalTrend(globalTrendId, trends);
        globalTrends.put(globalTrendId, globalTrend);

        for (LocalTrend localTrend : trends) {
            localMergedTrends.put(localTrend.getId(), globalTrend);
        }
        
        // System.out.println("New global trend: " + trends.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));

        // Remove local trends that are now part of global trend
        for (LocalTrend trend : trends) {
            localTrends.remove(trend.getId());
        }
    }

    private double calculateCosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String generateTrendName(List<String> keywords, List<String> sampleMessages) {
        String keywordsStr = keywords.stream()
            .limit(5)
            .collect(Collectors.joining(", "));
        
        // Get up to 10 sample messages
        String messagesStr = sampleMessages.stream()
            .limit(10)
            .map(msg -> "- " + msg)
            .collect(Collectors.joining("\n"));
        
        // Create the system prompt with actual data
        String systemPrompt = "Generate a short name for the trend that people currently discuss on social media based on the following information:\n\n" + 
                            "top 5 keywords: " + keywordsStr + "\n\n" + 
                            "10 sampled messages:\n" + messagesStr + "\n\n" +
                            "Requirements for the name:\n" +
                            "1. Maximum 2-3 words (decide which one works best)\n" +
                            "2. Should be descriptive but concise\n" +
                            "3. Should capture the main topic or sentiment\n" +
                            "4. Return ONLY the name, no explanations or quotes";

        String userPrompt = "Generate the trend name:";

        List<ChatRequestMessage> messages = Arrays.asList(
            new ChatRequestSystemMessage(systemPrompt),
            new ChatRequestUserMessage(userPrompt));
        
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
            .setTemperature(1.2)
            .setMaxTokens(50)
            .setN(1);
    
        try {
            // Make the request
            ChatCompletions response = client.getChatCompletions(
                "gpt-35-turbo", 
                options
            );
    
            // Extract and clean the generated name
            String generatedName = response.getChoices().get(0).getMessage().getContent();
            
            // Clean up the name (remove quotes, extra spaces, newlines)
            generatedName = generatedName.replaceAll("[\"'.,]", "")  // Remove quotes/punctiation
                                       .replaceAll("\\s+", " ")    // Normalize spaces
                                       .trim();                    // Remove leading/trailing spaces
            
            // Set the generated name
            return generatedName;
            
        } catch (Exception e) {
            // If name generation fails, create a fallback name from keywords
            String fallbackName = keywords.stream()
                .limit(3)
                .collect(Collectors.joining("_"));
            
                // Log the error
            System.err.println("Failed to generate trend name: " + e.getMessage());

            return fallbackName;
        }
    }

    
}