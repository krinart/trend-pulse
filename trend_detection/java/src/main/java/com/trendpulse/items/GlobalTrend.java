package com.trendpulse.items;

import java.util.*;

public class GlobalTrend implements Trend {
    private String id;
    private String name;
    private Set<LocalTrend> localTrends;
    private List<String> keywords;
    private double[] centroid;
    private Integer locationId;

    public GlobalTrend(String id, Set<LocalTrend> localTrends) {
        this.id = id;
        this.localTrends = localTrends;
        
        // Combine keywords from all local trends
        this.keywords = new ArrayList<>();
        for (LocalTrend trend : localTrends) {
            this.keywords.addAll(trend.getKeywords());
        }

        // Calculate average centroid and coordinates
        this.centroid = calculateAverageCentroid();
    }

    private double[] calculateAverageCentroid() {
        if (localTrends.isEmpty()) return new double[0];
        
        LocalTrend firstTrend = localTrends.iterator().next();
        double[] avgCentroid = new double[firstTrend.getCentroid().length];
        
        for (LocalTrend trend : localTrends) {
            double[] trendCentroid = trend.getCentroid();
            for (int i = 0; i < avgCentroid.length; i++) {
                avgCentroid[i] += trendCentroid[i];
            }
        }
        
        for (int i = 0; i < avgCentroid.length; i++) {
            avgCentroid[i] /= localTrends.size();
        }
        
        return avgCentroid;
    }

        public void addLocalTrend(LocalTrend newTrend) {
        this.localTrends.add(newTrend);
        this.keywords.addAll(newTrend.getKeywords());
        this.centroid = calculateAverageCentroid();
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public List<String> getKeywords() { return keywords; }
    @Override public double[] getCentroid() { return centroid; }
    @Override public Integer getLocationId() { return locationId; }

    public Set<LocalTrend> getLocalTrends() { return localTrends; }
    public void setName(String name) { this.name = name; }
}
