package com.trendpulse.processors;

import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import com.trendpulse.schema.TrendDataWrittenEvent;

public class TrendsJsonSink extends ProcessFunction<TrendDataWrittenEvent, Void> {

    @Override
    public void processElement(TrendDataWrittenEvent value, ProcessFunction<TrendDataWrittenEvent, Void>.Context ctx,
            Collector<Void> out) throws Exception {
        
    }
}
