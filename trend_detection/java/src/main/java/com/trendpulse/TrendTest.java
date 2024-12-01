package com.trendpulse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendpulse.items.MessagePoint;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;

public class TrendTest {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<TrendRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader("/Users/viktor/workspace/ds2/trend_detection/debug/debug.jsonl"))) {
        String line;
        while ((line = reader.readLine()) != null) {
            TrendRecord record = mapper.readValue(line, TrendRecord.class);
            records.add(record);
        }

        DBSCANClusterer<TrendRecord> dbscan = new DBSCANClusterer<>(
            0.7, 
            10,
            (p1, p2) -> {
                // Custom distance metric using cosine similarity

                double sum = 0.0;
                for (int i = 0; i < p1.length; i++) {
                    sum += Math.pow(p1[i] - p2[i], 2);
                }
                return Math.sqrt(sum);

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
        
        List<Cluster<TrendRecord>> clusters = dbscan.cluster(records);

        System.out.println(clusters.size());

        for (Cluster cluster: clusters) {
            System.out.println(cluster.getPoints().size());
        }
    }

    }
}


class TrendRecord implements Clusterable {
    @JsonProperty("d_trend_id")
    private int d_trend_id;
    @JsonProperty("d_location_id")
    private int d_location_id;
    private String topic;
    private String text;
    private int id;
    private String text_processed;
    private List<Double> embeddings;
    
    @Override
    public double[] getPoint() {
        return embeddings.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public int getD_trend_id() { return d_trend_id; }
    public void setD_trend_id(int d_trend_id) { this.d_trend_id = d_trend_id; }
    
    public int getD_location_id() { return d_location_id; }
    public void setD_location_id(int d_location_id) { this.d_location_id = d_location_id; }
    
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getText_processed() { return text_processed; }
    public void setText_processed(String text_processed) { this.text_processed = text_processed; }
    
    public List<Double> getEmbeddings() { return embeddings; }
    public void setEmbeddings(List<Double> embeddings) { this.embeddings = embeddings; }
}