package com.trendpulse.items;

import java.time.OffsetDateTime;


public class Message {
    private String topic;
    private long timestamp;
    private double lon;
    private double lat;
    private String text;
    private String preProcessedText;
    private int locationID;

    private double[] embedding;
    private OffsetDateTime datetime;
    private String clusterTrendId;

    private int dTrendId;
    private int dLocationId;

    public int getDTrendId() { return dTrendId; }
    public void setDTrendId(int dTrendId) { this.dTrendId = dTrendId; }
    
    public int getDLocationId() { return dLocationId; }
    public void setDLocationId(int dLocationId) { this.dLocationId = dLocationId; }

    public int getLocationId() { return locationID; }
    public void setLocationId(int locationID) { this.locationID = locationID; }
    
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    
    public long getTimestamp() { return timestamp; }
    
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double[] getEmbedding() { return embedding; }
    public void setEmbedding(double[] embedding) { this.embedding = embedding; }

    public String getPreProcessedText() { return preProcessedText; }
    public void setPreProcessedText(String preProcessedText) { this.preProcessedText = preProcessedText; }

    public OffsetDateTime getDatetime() { return datetime; }
    public void setDatetime(OffsetDateTime datetime) { 
        this.datetime = datetime; 
        this.timestamp = datetime.toInstant().toEpochMilli();
    }

    public String getClusterTrendId() { return clusterTrendId; }
    public void setClusterTrendId(String trendId) { this.clusterTrendId = trendId; }
}