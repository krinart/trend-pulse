package com.trendpulse.processors;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.TrendStatsInfo;
import com.trendpulse.schema.TrendActivatedInfo;
import com.trendpulse.items.GlobalTrend;
import com.trendpulse.items.LocalTrend;
import com.trendpulse.items.Trend;

public class TrendManagementProcessor extends ProcessFunction<TrendEvent, String> {
    private static final double SIMILARITY_THRESHOLD = 0.8; // Cosine similarity threshold
    private static String AZURE_OPENAI_ENDPOINT = "https://my-first-open-ai-service.openai.azure.com/";
    private static String AZURE_OPENAI_KEY = "uClNQwvESsEPxSFhKKonjSfIa8KDKUsyzLo7wl0rHzSpTI2qd40fJQQJ99AKACYeBjFXJ3w3AAABACOGgkTy";
    private static final OpenAIClient client = new OpenAIClientBuilder()
        .endpoint(AZURE_OPENAI_ENDPOINT)
        .credential(new AzureKeyCredential(AZURE_OPENAI_KEY))
        .buildClient();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, LocalTrend> localTrends = new HashMap<>();
    private final Map<String, GlobalTrend> globalTrends = new HashMap<>();
    
    @Override
    public void processElement(TrendEvent event, Context ctx, Collector<String> out) throws Exception {
        switch (event.getEventType()) {
            case TREND_ACTIVATED : {
                processTrendActivated(event, out);
                break;
            }
            case TREND_STATS: {
                processTrendStats(event, out);
                break;
            }
            case TREND_DEACTIVATED: {
                processTrendDeactivated(event, out);
                break;
            }
        }
    }

    private void processTrendActivated(TrendEvent event, Collector<String> out) throws Exception {
        String trendId = event.getTrendId().toString();
        TrendActivatedInfo eventInfo = (TrendActivatedInfo) event.getInfo();

        // Initialize new local trend
        LocalTrend newTrend = initializeLocalTrend(eventInfo, trendId, event.getLocationId());
        // System.out.println("----------------------------------------");
        System.out.println("Incoming trend: " + newTrend.getName());
        
        // First check global trends
        List<TrendWithSimilarity> similarGlobalTrends = findSimilarTrends(
            new ArrayList<>(globalTrends.values()), 
            newTrend
        );
        
        if (!similarGlobalTrends.isEmpty() && similarGlobalTrends.get(0).similarity >= SIMILARITY_THRESHOLD) {
            // System.out.println("Matched existing global trend");
            GlobalTrend mostSimilarGlobalTrend = (GlobalTrend) similarGlobalTrends.get(0).trend;
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
            // System.out.println("New global trend initialized");
            matchingLocalTrends.add(newTrend);
            initializeGlobalTrend(matchingLocalTrends);
        } else {
            // System.out.println("Stored as a local trend");
            // Store as local trend
            localTrends.put(trendId, newTrend);
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

        // Sort by similarity in descending order
        similarTrends.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        return similarTrends;
    }

    private LocalTrend initializeLocalTrend(TrendActivatedInfo eventInfo, String trendId, int locationId) {
        // List<String> keywords = Arrays.asList(
        //     objectMapper.convertValue(eventInfo.get("keywords"), String[].class));
        // double[] centroid = objectMapper.convertValue(eventInfo.get("centroid"), double[].class);
        // List<String> sampleMessages = Arrays.asList(objectMapper.convertValue(eventInfo.get("sampleMessages"), String[].class));
        
        List<String> keywords = eventInfo.getKeywords().stream().map(k -> k.toString()).collect(Collectors.toList());
        double[] centroid = eventInfo.getCentroid().stream().mapToDouble(Double::doubleValue).toArray();;
        List<String> sampleMessages = eventInfo.getSampleMessages().stream().map(k -> k.toString()).collect(Collectors.toList());        

        String name = generateTrendName(keywords, sampleMessages);

        return new LocalTrend(trendId, name, keywords, centroid, locationId, sampleMessages);
    }

    private void initializeGlobalTrend(Set<LocalTrend> trends) {
        String globalTrendId = UUID.randomUUID().toString();
        GlobalTrend globalTrend = new GlobalTrend(globalTrendId, trends);
        globalTrends.put(globalTrendId, globalTrend);
        
        System.out.println("New global trend: " + trends.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));

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

    private void processTrendStats(TrendEvent event, Collector<String> out) {
        // Implementation pending
    }

    private void processTrendDeactivated(TrendEvent event, Collector<String> out) {
        // Implementation pending
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