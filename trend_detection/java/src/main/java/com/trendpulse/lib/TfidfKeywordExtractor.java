package com.trendpulse.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import com.trendpulse.items.InputMessage;

public class TfidfKeywordExtractor  {
    private static final int TOP_KEYWORDS = 10;
    
    // Helper class to store document term frequencies
    private static class TermFrequencies {
        Map<String, Integer> frequencies = new HashMap<>();
        int totalTerms = 0;
        
        void addTerm(String term) {
            frequencies.merge(term, 1, Integer::sum);
            totalTerms++;
        }
        
        double getTermFrequency(String term) {
            return (double) frequencies.getOrDefault(term, 0) / totalTerms;
        }
    }
    
    public List<String> extractKeywords(List<InputMessage> messages) {
        // Step 1: Tokenize all messages and build document frequencies
        List<TermFrequencies> documentFrequencies = new ArrayList<>();
        Map<String, Integer> documentCount = new HashMap<>();
        Set<String> allTerms = new HashSet<>();
        
        for (InputMessage message : messages) {
            TermFrequencies tf = tokenizeAndCount(message.getPreProcessedText());
            documentFrequencies.add(tf);
            
            // Update document count for each unique term
            tf.frequencies.keySet().forEach(term -> {
                documentCount.merge(term, 1, Integer::sum);
                allTerms.add(term);
            });
        }
        
        // Step 2: Calculate TF-IDF scores for each term in each document
        int totalDocuments = messages.size();
        Map<String, Double> aggregatedScores = new HashMap<>();
        
        for (String term : allTerms) {
            double idf = calculateIDF(totalDocuments, documentCount.getOrDefault(term, 0));
            
            // Calculate and sum TF-IDF scores across all documents
            double totalScore = 0.0;
            for (TermFrequencies tf : documentFrequencies) {
                double tfScore = tf.getTermFrequency(term);
                totalScore += tfScore * idf;
            }
            
            aggregatedScores.put(term, totalScore);
        }
        
        // Step 3: Sort and return top keywords
        return aggregatedScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(TOP_KEYWORDS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private TermFrequencies tokenizeAndCount(String text) {
        TermFrequencies tf = new TermFrequencies();
        
        // Simple tokenization and cleaning
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .split("\\s+");
            
        // Count term frequencies, excluding stop words
        for (String word : words) {
            if (!word.isEmpty() && !isStopWord(word)) {
                tf.addTerm(word);
            }
        }
        
        return tf;
    }
    
    private double calculateIDF(int totalDocuments, int termDocumentCount) {
        return Math.log((double) totalDocuments / (1 + termDocumentCount));
    }
    
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "were", "will", "with", "this", "but", "they",
        "have", "had", "what", "when", "where", "who", "which", "why", "how"
    );
    
    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }
}