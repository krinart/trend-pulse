package com.trendpulse.lib;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;

public class TrendNameGenerator {

    private static String AZURE_OPENAI_ENDPOINT = "https://my-first-open-ai-service.openai.azure.com/";
    private transient OpenAIClient client;

    public TrendNameGenerator() {
        String azureKey = System.getenv("AZURE_OPENAI_KEY");
        if (azureKey == null) {
            throw new IllegalStateException("AZURE_OPENAI_KEY is required");
        }

        client = new OpenAIClientBuilder()
            .endpoint(AZURE_OPENAI_ENDPOINT)
            .credential(new AzureKeyCredential(azureKey))
            .buildClient();
    }

    public String generateTrendName(List<CharSequence> keywords, List<CharSequence> sampleMessages) {
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
