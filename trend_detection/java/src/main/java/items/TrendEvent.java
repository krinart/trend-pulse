public class TrendEvent {
    private String eventType;  // TREND_CREATED or TREND_STATS
    private String trendId;
    private int locationId;
    private String eventInfo;  // JSON string with event details

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