package com.trendpulse.items;

import java.util.*;
import java.util.stream.Collectors;
import java.io.Serializable;

import com.trendpulse.lib.TrendStatsGrid;

public class TrendDetected implements Serializable {
    private String id;
    private List<String> keywords;
    private long createdAt;
    private long lastUpdate;
    private Set<Message> messages;
    private double[] centroid;
    private int originalMessagesCount;
    private int matchedMessagesCount;
    
    private TrendStatsGrid stats;

    private Map<Integer, Integer> debugLocationsMap;
    private Map<Integer, Integer> debugTrendsMap;

    public TrendDetected(String id, List<String> keywords, List<Message> messages, 
                double[] centroid, long currentTime, int statsWindowMinutes) {
        this.id = id;
        this.keywords = keywords;
        this.messages = new HashSet<>(messages);
        this.centroid = centroid;
        this.createdAt = currentTime;
        this.lastUpdate = currentTime;
        this.originalMessagesCount = messages.size();
        this.matchedMessagesCount = 0;

        this.stats = new TrendStatsGrid(statsWindowMinutes);

        this.debugLocationsMap = new HashMap<>();
        this.debugTrendsMap = new HashMap<>();

        for (Message m: messages) {
            this.updateDebugInfo(m);
            this.stats.addPoint(m.getDatetime().toInstant(), m.getLat(), m.getLon());
        }
    }

    // Getters
    public String getId() { return id; }
    public List<String> getKeywords() { return keywords; }
    public double[] getCentroid() { return centroid; }
    public long getLastUpdate() { return lastUpdate; }
    public Set<Message> getMessages() { return messages; }
    public TrendStatsGrid getStats() { return stats; }
    public long getCreatedAt() { return createdAt; }

    // Setters
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public void setCentroid(double[] centroid) { this.centroid = centroid; }
    public void updateMessages(List<Message> messages) {
        this.messages.addAll(messages);
        for (Message m: messages) {
            this.updateDebugInfo(m);
            this.stats.addPoint(m.getDatetime().toInstant(), m.getLat(), m.getLon());
        }
    }
    public void addMessage(Message message) {
        this.messages.add(message);
        this.updateDebugInfo(message);
        this.stats.addPoint(message.getDatetime().toInstant(), message.getLat(), message.getLon());
    }

    private void updateDebugInfo(Message m) {
        // System.out.println("qwe: " + m.getDLocationId() + "" + m.getDTrendId());

        Integer locationID = m.getDLocationId();
        Integer trendID = m.getDTrendId();

        if (!this.debugLocationsMap.containsKey(locationID)) {
            this.debugLocationsMap.put(locationID, 0);
        }
        this.debugLocationsMap.put(locationID, this.debugLocationsMap.get(locationID)+1);

        if (!this.debugTrendsMap.containsKey(trendID)) {
            this.debugTrendsMap.put(trendID, 0);
        }
        this.debugTrendsMap.put(trendID, this.debugTrendsMap.get(trendID)+1);
    }

    public String getDebugInfo() {
        return "locations: [" + 
           debugLocationsMap.entrySet().stream()
               .map(e -> e.getKey() + ":" + e.getValue())
               .collect(Collectors.joining(",")) + 
           "], trends: [" + 
           debugTrendsMap.entrySet().stream()
               .map(e -> e.getKey() + ":" + e.getValue())
               .collect(Collectors.joining(",")) + 
           "]";
    }
}