package com.trendpulse.items;

import java.util.List;

public interface Trend {
    String getId();
    String getName();
    String getTopic();
    List<String> getKeywords();
    double[] getCentroid();
    Integer getLocationId();
}