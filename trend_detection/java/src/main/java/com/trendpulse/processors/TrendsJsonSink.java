package com.trendpulse.processors;

import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;

import com.trendpulse.schema.TrendDataWrittenEvent;
import com.trendpulse.schema.TrendEvent;

public class TrendsJsonSink extends CoProcessFunction<TrendEvent, TrendDataWrittenEvent, Void> {

    @Override
    public void processElement1(TrendEvent value,
            CoProcessFunction<TrendEvent, TrendDataWrittenEvent, Void>.Context ctx, Collector<Void> out)
            throws Exception {
        System.out.println("TrendsJsonSink." + value.getEventType()+": " + value.getTrendId());
    }

    @Override
    public void processElement2(TrendDataWrittenEvent value,
            CoProcessFunction<TrendEvent, TrendDataWrittenEvent, Void>.Context ctx, Collector<Void> out)
            throws Exception {
        System.out.println("TrendsJsonSink.written: " + value.getTrendId() + " - " + " -  " + value.getDataType() + " - " + value.getTimestamp());
    }

    
}
