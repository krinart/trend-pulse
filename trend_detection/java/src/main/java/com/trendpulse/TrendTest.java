package com.trendpulse;

import com.trendpulse.schema.EventType;
import com.trendpulse.schema.TrendEvent;

public class TrendTest {
    public static void main(String[] args) throws Exception {
        new TrendEvent(
                EventType.TREND_STATS,
                "id",
                null,
                null,
                null
            );
    }

}
