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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendpulse.items.TrendEvent;

public class TrendManagementProcessor extends ProcessFunction<TrendEvent, String> {
    private static final double SIMILARITY_THRESHOLD = 0.8; // Cosine similarity threshold
    private static String AZURE_OPENAI_ENDPOINT = "https://my-first-open-ai-service.openai.azure.com/";
    private static String AZURE_OPENAI_KEY = "uClNQwvESsEPxSFhKKonjSfIa8KDKUsyzLo7wl0rHzSpTI2qd40fJQQJ99AKACYeBjFXJ3w3AAABACOGgkTy";
    private static final OpenAIClient client = new OpenAIClientBuilder()
        .endpoint(AZURE_OPENAI_ENDPOINT)
        .credential(new AzureKeyCredential(AZURE_OPENAI_KEY))
        .buildClient();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, Trend> localTrends = new HashMap<>();
    private final Map<String, GlobalTrend> globalTrends = new HashMap<>();
    
    private final Map<String, Set<String>> similarTrends = new HashMap<>();

    @Override
    public void processElement(TrendEvent event, Context ctx, Collector<String> out) throws Exception {
        String trendId = event.getTrendId();
        JsonNode eventInfo = objectMapper.readTree(event.getEventInfo());
        
        if (event.getEventType().equals(TrendEvent.TREND_ACTIVATED)) {
            String[] keywords = objectMapper.convertValue(eventInfo.get("keywords"), String[].class);
            String[] sampleMessages = objectMapper.convertValue(eventInfo.get("sampleMessages"), String[].class);
            double[] centroid = objectMapper.convertValue(eventInfo.get("centroid"), double[].class);

            Trend newTrend = new Trend(trendId, centroid, keywords, sampleMessages, event.getLocationId());
            generateTrendName(newTrend);

            System.out.println(localTrends.size() + " - Trend(" + trendId + ", "+ newTrend.locationId +") name: " + newTrend.name);

            findAndUpdateSimilarTrends(newTrend);
            
            localTrends.put(trendId, newTrend);
            
            checkAndUpdateGlobalStatus(newTrend);
            
            // out.collect(String.format("Trend processed - ID: %s, Name: %s, Status: %s", 
            //     trendId, newTrend.name, newTrend.isGlobal ? "GLOBAL" : "LOCAL"));
        }
    }

    private void findAndUpdateSimilarTrends(Trend newTrend) {
        Set<String> matchingTrends = new HashSet<>();
        
        for (Trend existingTrend : localTrends.values()) {
            double similarity = calculateCosineSimilarity(newTrend.centroid, existingTrend.centroid);

            System.out.println(String.format("Similarity: \"%s\" - \"%s\": %f", newTrend.name, existingTrend.name, similarity));

            if (existingTrend.locationId != newTrend.locationId && 
                similarity >= SIMILARITY_THRESHOLD) {
                
                matchingTrends.add(existingTrend.trendId);
                
                System.out.println(
                    String.format("Matching trends: \"%s\"(%d) - \"%s\"(%d)", 
                        newTrend.getName(),
                        newTrend.getLocationId(),
                        existingTrend.getName(),
                        existingTrend.getLocationId()
                    ));

                // Update similarities for existing trend
                similarTrends.computeIfAbsent(existingTrend.trendId, k -> new HashSet<>())
                            .add(newTrend.trendId);
            }
        }
        
        if (!matchingTrends.isEmpty()) {
            similarTrends.put(newTrend.trendId, matchingTrends);
        }
    }

    private void checkAndUpdateGlobalStatus(Trend trend) {
        Set<String> matches = similarTrends.getOrDefault(trend.trendId, new HashSet<>());
        
        if (matches.size() >= 2) { // We need 2 matches to have 3 total similar trends
            
            System.out.println("NEW GLOBAL TREND");
            
            // Mark all similar trends as global
            trend.setGlobal(true);
            matches.forEach(matchId -> {
                Trend matchingTrend = localTrends.get(matchId);
                if (matchingTrend != null) {
                    matchingTrend.setGlobal(true);
                }
            });
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

    private void generateTrendName(Trend trend) {
        String keywordsStr = trend.keywords.stream()
            .limit(5)
            .collect(Collectors.joining(", "));
        
        // Get up to 10 sample messages
        String messagesStr = trend.sampleMessages.stream()
            .limit(10)
            .map(msg -> "- " + msg)
            .collect(Collectors.joining("\n"));
        
        // Create the system prompt with actual data
        String systemPrompt = "Generate a short name for the trend that people currently discuss on social media based on the following information:\n\n" + 
                            "top 5 keywords: " + keywordsStr + "\n\n" + 
                            "10 sampled messages:\n" + messagesStr + "\n\n" +
                            "Requirements for the name:\n" +
                            "1. Maximum 3 words\n" +
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
            generatedName = generatedName.replaceAll("[\"']", "")  // Remove quotes
                                       .replaceAll("\\s+", " ")    // Normalize spaces
                                       .trim();                    // Remove leading/trailing spaces
            
            // Set the generated name
            trend.setName(generatedName);
            
        } catch (Exception e) {
            // If name generation fails, create a fallback name from keywords
            String fallbackName = trend.keywords.stream()
                .limit(3)
                .collect(Collectors.joining("_"));
            trend.setName(fallbackName);
            
            // Log the error
            System.err.println("Failed to generate trend name: " + e.getMessage());
        }
    }

    private class Trend {
        String trendId;
        String name;
        double[] centroid;
        List<String> keywords;
        List<String> sampleMessages;
        int locationId;
        boolean isGlobal;

        public Trend(String trendId, double[] centroid, String[] keywords, String[] sampleMessages, int locationId) {
            this.trendId = trendId;
            this.centroid = centroid;
            this.keywords = Arrays.asList(keywords);
            this.sampleMessages = Arrays.asList(sampleMessages);
            this.locationId = locationId;
            this.isGlobal = false;
        }

        public String getName() { return name; }
        public int getLocationId() { return locationId; }

        public void setName(String name) { this.name = name;  }
        public void setGlobal(boolean global) { this.isGlobal = global; }
    }

    private class GlobalTrend extends Trend {
        public GlobalTrend(String trendId, double[] centroid, String[] keywords, String[] sampleMessages, int locationId) {
            super(trendId, centroid, keywords, sampleMessages, locationId);
        }
    }
}