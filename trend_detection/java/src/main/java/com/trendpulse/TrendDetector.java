package com.trendpulse;

import java.util.*;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.Cluster;

import com.trendpulse.items.TrendDetected;
import com.trendpulse.items.InputMessage;
import com.trendpulse.items.Message;
import com.trendpulse.items.MessagePoint;
import com.trendpulse.lib.PythonServiceClient;
import com.trendpulse.lib.TfidfKeywordExtractor;


public class TrendDetector {
    private Map<String, TrendDetected> trends;
    private Set<Message> clusteredMessages;
    private List<Message> unmatchedMessages;
    private long lastClusteringTime;
    private int unProcessedMessages;
    private String socketFilePath;
    private Integer locationID;
    private String topic;
    private PythonServiceClient pythonClient;
    private TfidfKeywordExtractor keywordExtractor;
    
    private int trendStatsWindowMinutes;

    public static final double CLUSTERING_EPS = 0.7;
    public static final int MIN_CLUSTER_SIZE = 10;

    public static final double SIMILARITY_THRESHOLD = 0.8;
    public static final int CLUSTERING_INTERVAL_SECONDS = 60;
    public static final int UNPROCESSED_MESSAGES_THRESHOLD = 20;
    public static final int KEEP_UNMATCHED_MESSAGES_MINUTES = 10;
    public static final int KEEP_TRENS_ALIVE_MINUTES = 120;

    public TrendDetector(Integer locationID, String topic, String socketFilePath, int trendStatsWindowMinutes) {
        this.locationID = locationID;
        this.topic = topic;
        this.socketFilePath = socketFilePath;
        this.trends = new HashMap<>();
        this.clusteredMessages = new HashSet<>();
        this.unmatchedMessages = new ArrayList<>();
        this.lastClusteringTime = 0;
        this.unProcessedMessages = 0;
        this.pythonClient = new PythonServiceClient(this.socketFilePath);
        this.keywordExtractor = new TfidfKeywordExtractor();
        this.trendStatsWindowMinutes = trendStatsWindowMinutes;
    }

    public ProcessingResult processMessage(InputMessage inputMessage, long currentTime) {
        ProcessingResult result = new ProcessingResult();

        Message message = this.initializeMessage(inputMessage);

        boolean matchedExistingTrend = false;
        if (message.getEmbedding() != null) {
            String matchedTrendId = matchToTrend(message);
            if (matchedTrendId != null) {
                matchedExistingTrend = true;
                updateTrend(trends.get(matchedTrendId), message, currentTime);
            }
        }

        if (!matchedExistingTrend) {
            unmatchedMessages.add(message);
            unProcessedMessages ++;
        }
        
        if (currentTime < 0) {
            return result;
        }

        boolean timeThresholdMet = (currentTime - lastClusteringTime) >= CLUSTERING_INTERVAL_SECONDS * 1000;
        boolean countThresholdMet = unProcessedMessages >= UNPROCESSED_MESSAGES_THRESHOLD;

        if (timeThresholdMet || countThresholdMet) {
            // System.out.println("currentTime: " + currentTime + " | lastClusteringTime: " + lastClusteringTime + " | unmatchedMessages: " + unmatchedMessages.size());
            // System.out.println("timeThresholdMet: " + timeThresholdMet + " | countThresholdMet: " + countThresholdMet);

            cleanupOldMessages(currentTime);
            
            result.setDeActivatedTrends(cleanupOldTrends(currentTime));
            
            result.setActivatedTrends(detectNewTrends(currentTime));
            
            lastClusteringTime = currentTime;
            unProcessedMessages = 0;
        }
        
        return result;
    }

    public List<TrendDetected> getTrends() { return new ArrayList<>(trends.values()); }

    private void cleanupOldMessages(long currentTime) {
        if (currentTime < 0 || unmatchedMessages.size() == 0) return;

        // System.out.println("cleanupOldMessages diff sec: " + ((currentTime - unmatchedMessages.get(0).getTimestamp()) / 1000));

        long cutoffTime = currentTime - (KEEP_UNMATCHED_MESSAGES_MINUTES * 60 * 1000);
        // Only cleanup unmatched messages - clustered ones stay until trend retirement

        long initSize = unmatchedMessages.size();
        unmatchedMessages.removeIf(message -> message.getTimestamp() < cutoffTime);
        long endSize = unmatchedMessages.size();
        // System.out.println("cleanupOldMessages(" + currentTime + "): " + (initSize -endSize));
    }

    private List<TrendDetected> cleanupOldTrends(long currentTime) {
        List<TrendDetected> deActivatedTrends = new ArrayList<TrendDetected>();

        if (currentTime < 0 || trends.size() == 0) return deActivatedTrends;

        long cutoffTime = currentTime - (KEEP_TRENS_ALIVE_MINUTES * 60 * 1000);

        for (TrendDetected trend : trends.values()) {

            if (trend.getLastUpdate() < cutoffTime) {
                // Set<Message>s = new HashSet<Message>(clusteredMessages);
                // s.retainAll(trend.getMessages());
                // System.out.println(this.locationID + " Remove trend with " + trend.getMessages().size() + " messages | clustered: " + s.size());
                System.out.println("Remove trend:  " + trend.getId() + " - " + (cutoffTime - trend.getLastUpdate()));
                clusteredMessages.removeAll(trend.getMessages());
                unmatchedMessages.removeAll(trend.getMessages());
                deActivatedTrends.add(trend);
            }
        }

        for (TrendDetected trend: deActivatedTrends) {
            trends.remove(trend.getId());
        }

        return deActivatedTrends;
    }

    private Message initializeMessage(InputMessage im) {
        Message message = new Message();
        message.setText(im.getText());
        message.setTopic(im.getTopic());
        message.setDatetime(im.getDatetime());
        message.setLat(im.getLat());
        message.setLon(im.getLon());
        message.setDLocationId(im.getDLocationId());
        message.setDTrendId(im.getDTrendId());
        message.setId(im.getId());
        
        try {
            PythonServiceClient.EmbeddingResponse response = pythonClient.getEmbedding(im.getText());
            message.setEmbedding(response.getEmbedding());
            message.setPreProcessedText(response.getProcessedText());
        } catch (IOException e) {
            System.out.println("Failed to prepare message: " + e.toString());
            e.printStackTrace(System.err);
            if (e.getCause() != null) {
                System.err.println("Caused by: ");
                e.getCause().printStackTrace(System.err);
            }
        }
        
        return message;
    }

    private String matchToTrend(Message message) {
        for (Map.Entry<String, TrendDetected> entry : trends.entrySet()) {
            double similarity = cosineSimilarity(message.getEmbedding(), entry.getValue().getCentroid());
            if (similarity > SIMILARITY_THRESHOLD) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void updateTrend(TrendDetected trend, Message message, long timestamp) {
        trend.addMessage(message);
        trend.setLastUpdate(timestamp);
        // trend.incrementMatchedCount();

        // float[] messageEmbedding = pythonClient.getEmbedding(message.getText());
        // updateCentroid(trend, messageEmbedding);
        
        // // Update keywords periodically
        // if (trend.getMessages().size() % 10 == 0) {
        //     List<String> newKeywords = pythonClient.extractKeywords(trend.getMessages());
        //     trend.setKeywords(newKeywords);
        // }
    }

    private void writeIds(List<Message> messages, String filePath) {

        String fullPath = "/Users/viktor/workspace/ds2/trend_detection/debug/" + filePath;

        System.out.println(filePath + " - " + messages.size());

        try (FileWriter writer = new FileWriter(fullPath)) {
            String ids = messages.stream()
                .map(m -> String.valueOf(m.getId()))
                .collect(Collectors.joining(","));
            writer.write(ids);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<TrendDetected> detectNewTrends(long currentTime) {
        long startTime = System.currentTimeMillis();
        List<TrendDetected> newTrends = new ArrayList<>();
        
        // if (unmatchedMessages.size() > 0) {
        //     Long minTimestamp = unmatchedMessages.stream().map(m -> m.getTimestamp()).min(Long::compareTo).get();
        //     System.out.println(" detectNewTrends diff sec: " + ((currentTime - minTimestamp) / 1000));
        // }

        List<MessagePoint> points = new ArrayList<>();
        for (Message message : unmatchedMessages) {
            points.add(new MessagePoint(message));
        }
        for (Message message : clusteredMessages) {
            points.add(new MessagePoint(message));
        }

        String debugStr = "clustered: " + clusteredMessages.size() + " unmatched: " + unmatchedMessages.size();
        // System.out.println(" detectNewTrends - clustered: " + clusteredMessages.size() + " unmatched: " + unmatchedMessages.size());

        DBSCANClusterer<MessagePoint> dbscan = new DBSCANClusterer<>(
            CLUSTERING_EPS, 
            MIN_CLUSTER_SIZE,
            (p1, p2) -> {
                double sum = 0.0;
                for (int i = 0; i < p1.length; i++) {
                    sum += Math.pow(p1[i] - p2[i], 2);
                }
                return Math.sqrt(sum);

                // // Custom distance metric using cosine similarity
                // double[] vec1 = p1;
                // double[] vec2 = p2;
                
                // double dotProduct = 0.0;
                // double norm1 = 0.0;
                // double norm2 = 0.0;
                
                // for (int i = 0; i < vec1.length; i++) {
                //     dotProduct += vec1[i] * vec2[i];
                //     norm1 += vec1[i] * vec1[i];
                //     norm2 += vec2[i] * vec2[i];
                // }
                
                // double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
                // return 1.0 - cosineSimilarity;  // Convert to distance
            }
        );
        
        List<Cluster<MessagePoint>> clusters = dbscan.cluster(points);
        
        // Convert clusters to trends
        for (Cluster<MessagePoint> cluster : clusters) {
            List<Message> clusterMessages = cluster.getPoints().stream()
                .map(MessagePoint::getMessage)
                .collect(Collectors.toList());
                
            // Set<Integer> debugTrendIDS = clusterMessages.stream().map(m -> m.getDTrendId()).collect(Collectors.toSet());
            // if (debugTrendIDS.size() > 1) {
            //     String asd = debugTrendIDS.stream().map(v -> String.valueOf(v)).collect(Collectors.joining("_"));
            //     String filename = "" + System.currentTimeMillis() + "-" + asd;
            //     writeIds(clusterMessages, filename);
            // }

        //     Map<Integer, Integer> debugTrendsMap = new HashMap<>();
        //     for (Message m: clusterMessages) {
        //         Integer trendID = m.getDTrendId();

        //         if (!debugTrendsMap.containsKey(trendID)) {
        //             debugTrendsMap.put(trendID, 0);
        //         }
        //         debugTrendsMap.put(trendID, debugTrendsMap.get(trendID)+1);
        //     }
        //     System.out.println("Cluster: trends: [" + 
        //         debugTrendsMap.entrySet().stream()
        //             .map(e -> e.getKey() + ":" + e.getValue())
        //             .collect(Collectors.joining(",")) + 
        //    "]");

            // Calculate centroid
            double[] centroid = calculateCentroid(cluster.getPoints());
            
            // Extract keywords
            List<String> keywords = keywordExtractor.extractKeywords(clusterMessages);
            
            // Check if this cluster matches any existing trend
            boolean matchedExisting = false;
            for (TrendDetected existingTrend : trends.values()) {
                double similarity = cosineSimilarity(centroid, existingTrend.getCentroid());
                if (similarity > SIMILARITY_THRESHOLD) {
                    matchedExisting = true;
                    existingTrend.updateMessages(clusterMessages);
                    break;
                }
            }

            if (!matchedExisting) {
                String trendId = "trend_" + currentTime + "_" + String.join("_", keywords);
                TrendDetected newTrend = new TrendDetected(
                    trendId, 
                    keywords, 
                    clusterMessages, 
                    centroid, 
                    currentTime,
                    this.trendStatsWindowMinutes
                );

                clusteredMessages.addAll(clusterMessages);
                unmatchedMessages.removeAll(clusterMessages);

                trends.put(trendId, newTrend);
                newTrends.add(newTrend);

            }
        }
        
        long endTime = System.currentTimeMillis();
        // System.out.println("detectNewTrends("+locationID+", " + debugStr + ") - " + (endTime - startTime) + "ms");

        return newTrends;
    }

    private double[] calculateCentroid(List<MessagePoint> points) {
        int dimensions = points.get(0).getPoint().length;
        double[] centroid = new double[dimensions];
        
        for (MessagePoint point : points) {
            double[] embedding = point.getPoint();
            for (int i = 0; i < dimensions; i++) {
                centroid[i] += embedding[i];
            }
        }
        
        for (int i = 0; i < dimensions; i++) {
            centroid[i] /= points.size();
        }
        
        return centroid;
    }

    // private void updateCentroid(Trend trend, float[] newEmbedding) {
    //     double[] oldCentroid = trend.getCentroid();
    //     int n = trend.getMessages().size();
    //     double[] newCentroid = new float[oldCentroid.length];
        
    //     for (int i = 0; i < oldCentroid.length; i++) {
    //         newCentroid[i] = (oldCentroid[i] * n + newEmbedding[i]) / (n + 1);
    //     }
        
    //     trend.setCentroid(newCentroid);
    // }

    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
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

    // Class to hold processing results
    public static class ProcessingResult {
        private List<TrendDetected> activatedTrends = new ArrayList<>();
        private List<TrendDetected> deActivatedTrends = new ArrayList<>();
        
        public List<TrendDetected> getActivatedTrends() { return activatedTrends; }
        public void setActivatedTrends(List<TrendDetected> newTrends) { this.activatedTrends = newTrends; }

        public List<TrendDetected> getDeActivatedTrends() { return deActivatedTrends; }
        public void setDeActivatedTrends(List<TrendDetected> deActivatedTrends) { this.deActivatedTrends = deActivatedTrends; }
    }
}