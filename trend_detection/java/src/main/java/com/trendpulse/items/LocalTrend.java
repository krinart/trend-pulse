package com.trendpulse.items;

import java.time.Instant;
import java.util.*;

import com.trendpulse.schema.WindowStats;

public class LocalTrend implements Trend {
    private String id;
    private String name;
    private String topic;
    private List<String> keywords;
    private double[] centroid;
    private Integer locationId;
    private List<String> sampleMessages;
    private Set<LocalTrend> similarTrends = new HashSet<>();
    private Map<Instant, WindowStats> windowStats = new HashMap<>();

    public LocalTrend(String id, String name, String topic, List<String> keywords, double[] centroid, Integer locationId, List<String> sampleMessages) {
        this.id = id;
        this.name = name;
        this.topic = topic;
        this.keywords = keywords;
        this.centroid = centroid;
        this.locationId = locationId;
        this.sampleMessages = sampleMessages;

        if (this.topic == null) {
            throw new IllegalStateException("Local trend has null topic");
        }
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String getTopic() { return topic; }
    @Override public List<String> getKeywords() { return keywords; }
    @Override public double[] getCentroid() { return centroid; }
    
    public Integer getLocationId() { return locationId; }

    public Set<LocalTrend> getSimilarTrends() { return similarTrends; }
    public List<String> getSampleMessages() { return sampleMessages; }
    public void addSimilarTrend(LocalTrend trend) { this.similarTrends.add(trend); }

    public Collection<WindowStats> getWindowStatsAll() {
        return windowStats.values();
    }

    public WindowStats getWindowStats(Instant ts) {
        return windowStats.get(ts);
    }

    public void setWindowStats(Instant ts, WindowStats windowStat) {
        this.windowStats.put(ts, windowStat);
    }
}