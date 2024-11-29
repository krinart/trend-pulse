import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.Cluster;


public class TrendDetector implements Serializable {
    private Map<String, Trend> trends;
    private List<InputMessage> unmatchedMessages;
    private long lastClusteringTime;
    private int unProcessedMessages;
    private transient PythonServiceClient pythonClient;
    private transient TfidfKeywordExtractor keywordExtractor;

    private final double CLUSTERING_EPS = 0.7;
    private final int MIN_CLUSTER_SIZE = 3;

    private static final double SIMILARITY_THRESHOLD = 0.8;
    private static final int CLUSTERING_INTERVAL_SECONDS = 60;
    private static final int UNPROCESSED_MESSAGES_THRESHOLD = 20;

    public TrendDetector() {
        this.trends = new HashMap<>();
        this.unmatchedMessages = new ArrayList<>();
        this.lastClusteringTime = System.currentTimeMillis();
        this.unProcessedMessages = 0;
        initTransients();
    }

    private void initTransients() {
        this.pythonClient = new PythonServiceClient("localhost", "9999");
        this.keywordExtractor = new TfidfKeywordExtractor();
    }

    private void readObject(java.io.ObjectInputStream in) 
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransients();
    }

    public ProcessingResult processMessage(InputMessage message, long currentTime) {
        ProcessingResult result = new ProcessingResult();
        
        try {
            this.prepareMessage(message);    
        } catch (IOException | InterruptedException e) {

        }

        if (message.getEmbedding() != null) {
            String matchedTrendId = matchToTrend(message);
            if (matchedTrendId != null) {
                updateTrend(trends.get(matchedTrendId), message, currentTime);
            }
        }

        unmatchedMessages.add(message);
        unProcessedMessages ++;
        
        boolean timeThresholdMet = (currentTime - lastClusteringTime) > CLUSTERING_INTERVAL_SECONDS * 1000;
        boolean countThresholdMet = unProcessedMessages >= UNPROCESSED_MESSAGES_THRESHOLD;

        if (timeThresholdMet || countThresholdMet) {
            List<Trend> newTrends = detectNewTrends(currentTime);
            result.setNewTrends(newTrends);
            lastClusteringTime = currentTime;
            unProcessedMessages = 0;
        }
        
        return result;
    }

    private void prepareMessage(InputMessage message) throws IOException, InterruptedException {
        double[] messageEmbedding = pythonClient.getEmbedding(message.getText());
        message.setEmbedding(messageEmbedding);
    }

    private String matchToTrend(InputMessage message) {
        for (Map.Entry<String, Trend> entry : trends.entrySet()) {
            double similarity = cosineSimilarity(message.getEmbedding(), entry.getValue().getCentroid());
            if (similarity > SIMILARITY_THRESHOLD) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void updateTrend(Trend trend, InputMessage message, long timestamp) {
        // trend.getMessages().add(message);
        // trend.setLastUpdate(timestamp);
        // // trend.incrementMatchedCount();

        // float[] messageEmbedding = pythonClient.getEmbedding(message.getText());
        // updateCentroid(trend, messageEmbedding);
        
        // // Update keywords periodically
        // if (trend.getMessages().size() % 10 == 0) {
        //     List<String> newKeywords = pythonClient.extractKeywords(trend.getMessages());
        //     trend.setKeywords(newKeywords);
        // }
    }

    private List<Trend> detectNewTrends(long currentTime) {
        List<Trend> newTrends = new ArrayList<>();
        
        List<MessagePoint> points = new ArrayList<>();
        for (InputMessage message : unmatchedMessages) {
            points.add(new MessagePoint(message));
        }

        DBSCANClusterer<MessagePoint> dbscan = new DBSCANClusterer<>(
            CLUSTERING_EPS, 
            MIN_CLUSTER_SIZE,
            (p1, p2) -> {
                // Custom distance metric using cosine similarity
                double[] vec1 = p1;
                double[] vec2 = p2;
                
                double dotProduct = 0.0;
                double norm1 = 0.0;
                double norm2 = 0.0;
                
                for (int i = 0; i < vec1.length; i++) {
                    dotProduct += vec1[i] * vec2[i];
                    norm1 += vec1[i] * vec1[i];
                    norm2 += vec2[i] * vec2[i];
                }
                
                double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
                return 1.0 - cosineSimilarity;  // Convert to distance
            }
        );
        
        List<Cluster<MessagePoint>> clusters = dbscan.cluster(points);
        
        // Convert clusters to trends
        for (Cluster<MessagePoint> cluster : clusters) {
            List<InputMessage> clusterMessages = cluster.getPoints().stream()
                .map(MessagePoint::getMessage)
                .collect(Collectors.toList());
                
            // Calculate centroid
            double[] centroid = calculateCentroid(cluster.getPoints());
            
            // Extract keywords
            List<String> keywords = keywordExtractor.extractKeywords(clusterMessages);
            
            String trendId = "trend_" + currentTime + "_" + String.join("_", keywords);
            Trend newTrend = new Trend(
                trendId, 
                keywords, 
                clusterMessages, 
                centroid, 
                currentTime
            );
            
            trends.put(trendId, newTrend);
            newTrends.add(newTrend);
        }
        
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
        
        // double[] result = new double[dimensions];
        // for (int i = 0; i < dimensions; i++) {
        //     result[i] = (float) centroid[i];
        // }
        
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
        private List<Trend> newTrends = new ArrayList<>();
        
        public List<Trend> getNewTrends() { return newTrends; }
        public void setNewTrends(List<Trend> newTrends) { this.newTrends = newTrends; }
    }
}