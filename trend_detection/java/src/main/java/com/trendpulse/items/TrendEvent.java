package com.trendpulse.items;

public class TrendEvent {
    
public static String TREND_CREATED = "TREND_CREATED";
    public static String TREND_STATS = "TREND_STATS";
    
    private String eventType;
    private String trendId;
    private int locationId;
    private String eventInfo;

    public TrendEvent(String eventType, String trendId, int locationId, String eventInfo) {
    	this.eventType = eventType;
    	this.trendId = trendId;
    	this.locationId = locationId;
    	this.eventInfo = eventInfo;
    }

    public String getEventType() { return eventType; }
    public String getTrendId() { return trendId; }
    public int getLocationId() { return locationId; }
    public String getEventInfo() { return eventInfo; }
}