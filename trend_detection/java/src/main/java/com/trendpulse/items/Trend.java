package com.trendpulse.items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.Serializable;

public class Trend implements Serializable {
    private String id;
    private List<String> keywords;
    private long createdAt;
    private long lastUpdate;
    private Set<Message> messages;
    private double[] centroid;
    private int originalMessagesCount;
    private int matchedMessagesCount;

    public Trend(String id, List<String> keywords, List<Message> messages, 
                double[] centroid, long currentTime) {
        this.id = id;
        this.keywords = keywords;
        this.messages = new HashSet<>(messages);
        this.centroid = centroid;
        this.createdAt = currentTime;
        this.lastUpdate = currentTime;
        this.originalMessagesCount = messages.size();
        this.matchedMessagesCount = 0;
    }

    // Getters
    public String getId() { return id; }
    public List<String> getKeywords() { return keywords; }
    public double[] getCentroid() { return centroid; }
    public long getLastUpdate() { return lastUpdate; }
    public Set<Message> getMessages() { return messages; }

    // Setters
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public void setCentroid(double[] centroid) { this.centroid = centroid; }
    public void updateMessages(List<Message> messages) {
        this.messages.addAll(messages);
    }
    public void addMessage(Message message) {
        this.messages.add(message);
    }
}