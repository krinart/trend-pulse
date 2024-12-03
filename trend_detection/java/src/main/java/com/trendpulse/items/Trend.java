package com.trendpulse.items;

import java.util.List;

public interface Trend {
    String getId();
    String getName();
    List<String> getKeywords();
    double[] getCentroid();
    Integer getLocationId();

    // WindowStats getWindowStats(Instant ts);
    // public void setWindowStats(Instant ts, WindowStats windowStat);
}