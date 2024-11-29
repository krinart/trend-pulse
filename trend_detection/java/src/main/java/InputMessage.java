import com.fasterxml.jackson.annotation.JsonProperty;

public class InputMessage {
    private String topic;
    private String timestamp;
    private double lon;
    private double lat;
    private String text;

    @JsonProperty("d_trend_id")
    private int d_trend_id;
    @JsonProperty("d_location_id")
    private int d_location_id;

    public int getDTrendId() { return d_trend_id; }
    public void setDTrendId(int d_trend_id) { this.d_trend_id = d_trend_id; }
    
    public int getDLocationId() { return d_location_id; }
    public void setDLocationId(int d_location_id) { this.d_location_id = d_location_id; }
    
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
}