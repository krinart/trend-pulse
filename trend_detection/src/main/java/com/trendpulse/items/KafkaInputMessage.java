package com.trendpulse.items;

public class KafkaInputMessage {
    private String topic;
    private String timestamp;
    private double lon;
    private double lat;
    private String text;
    private int id;
    private int d_trend_id;
    private int d_location_id;

    // Getters and setters matching exactly the JSON field names
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getD_trend_id() { return d_trend_id; }
    public void setD_trend_id(int d_trend_id) { this.d_trend_id = d_trend_id; }
    
    public int getD_location_id() { return d_location_id; }
    public void setD_location_id(int d_location_id) { this.d_location_id = d_location_id; }
}