package com.trendpulse.processors;

import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;

import com.trendpulse.schema.TrendActivatedInfo;
import com.trendpulse.schema.TrendDataWrittenEvent;
import com.trendpulse.schema.TrendEvent;

public class TrendsJsonSink extends CoProcessFunction<TrendEvent, TrendDataWrittenEvent, Void> {

    @Override
    public void processElement1(TrendEvent event, Context ctx, Collector<Void> out) throws Exception {
        // System.out.println("TrendsJsonSink.TrendEvent: " + " - " + event.getTrendId() + " - " + event.getEventType());
        
        switch (event.getEventType()) {
            case TREND_ACTIVATED : {
                processTrendActivated(event, ctx, out);
                break;
            }
            case TREND_TILE_INDEX: {
                processTileIndex(event, ctx, out);
                break;
            }
        }      
    }

    @Override
    public void processElement2(TrendDataWrittenEvent value, Context ctx, Collector<Void> out) throws Exception {
        
    }

    private void processTrendActivated(TrendEvent event, Context ctx, Collector<Void> out) throws Exception {
        TrendActivatedInfo eventInfo = (TrendActivatedInfo) event.getInfo();
        System.out.println("TrendsJsonSink.processTrendActivated: " + eventInfo.getName() + " - " + event.getTrendType());
    }

    private void processTileIndex(TrendEvent event, Context ctx, Collector<Void> out) throws Exception {
            
    }

}
