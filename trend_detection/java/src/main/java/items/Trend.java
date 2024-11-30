import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class Trend implements Serializable {
    private String id;
    private List<String> keywords;
    private long createdAt;
    private long lastUpdate;
    private List<InputMessage> messages;
    private double[] centroid;
    private int originalMessagesCount;
    private int matchedMessagesCount;

    public Trend(String id, List<String> keywords, List<InputMessage> messages, 
                double[] centroid, long currentTime) {
        this.id = id;
        this.keywords = keywords;
        this.messages = new ArrayList<>(messages);
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
    public List<InputMessage> getMessages() { return messages; }

    // Setters
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public void setCentroid(double[] centroid) { this.centroid = centroid; }
}