package com.trendpulse.items;

import java.util.*;

public class LocalTrend implements Trend {
    private String id;
    private String name;
    private List<String> keywords;
    private double[] centroid;
    private Integer locationId;
    private List<String> sampleMessages;
    private Set<LocalTrend> similarTrends;

    public LocalTrend(String id, String name, List<String> keywords, double[] centroid, Integer locationId, List<String> sampleMessages) {
        this.id = id;
        this.name = name;
        this.keywords = keywords;
        this.centroid = centroid;
        this.locationId = locationId;
        this.sampleMessages = sampleMessages;
        this.similarTrends = new HashSet<>();
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public List<String> getKeywords() { return keywords; }
    @Override public double[] getCentroid() { return centroid; }
    @Override public Integer getLocationId() { return locationId; }

    public Set<LocalTrend> getSimilarTrends() { return similarTrends; }
    public List<String> getSampleMessages() { return sampleMessages; }
    public void addSimilarTrend(LocalTrend trend) { this.similarTrends.add(trend); }
}