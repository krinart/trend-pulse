package com.trendpulse.lib;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.io.*;

public class PythonServiceClient {
    private final File socketFile;
    private final ObjectMapper mapper;

    public PythonServiceClient(String socketFilePath) {
        this.socketFile = new File(socketFilePath);
        this.mapper = new ObjectMapper();
    }

    public EmbeddingResponse getEmbedding(String text) throws IOException {
        long startTime = System.currentTimeMillis();

        Map<String, String> request = Map.of("text", text);
        
        try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
            socket.connect(AFUNIXSocketAddress.of(socketFile));
            
            // Send request
            try (OutputStream out = socket.getOutputStream()) {
                mapper.writeValue(out, request);
                out.flush();
                socket.shutdownOutput(); // Important: signals end of request
            }
            
            // Read response
            try (InputStream in = socket.getInputStream()) {
                Map<String, Object> response = mapper.readValue(in, Map.class);
                
                long endTime = System.currentTimeMillis();
                // System.out.println("Total socket call time: " + (endTime - startTime) + "ms");

                if (response.containsKey("error")) {
                    throw new IOException("Server error: " + response.get("error"));
                }
                
                List<Double> embeddings = (List<Double>) response.get("embedding");
                String processedText = (String) response.get("processed_text");
                
                double[] embeddingArray = embeddings.stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                
                return new EmbeddingResponse(embeddingArray, processedText);
            }
        }
    }

    // Response class to hold both embedding and processed text
    public static class EmbeddingResponse {
        private final double[] embedding;
        private final String processedText;

        public EmbeddingResponse(double[] embedding, String processedText) {
            this.embedding = embedding;
            this.processedText = processedText;
        }

        public double[] getEmbedding() {
            return embedding;
        }

        public String getProcessedText() {
            return processedText;
        }
    }
}