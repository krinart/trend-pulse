package com.trendpulse.processors;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.Map;
import java.util.HashMap;

import com.trendpulse.TrendDetector;
import com.trendpulse.items.InputMessage;
import com.trendpulse.items.TrendEvent;
import com.trendpulse.items.Trend;

public class TrendDetectionProcessor extends KeyedProcessFunction<Integer, InputMessage, TrendEvent> {

    private static final long serialVersionUID = 1L;

    private final String socketPath;
    private transient TrendDetector detector;

    private transient Map<Integer, TrendDetector> trendDetectorsMap;

    public TrendDetectionProcessor(String socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public void open(Configuration conf) throws Exception {
        this.trendDetectorsMap = new HashMap<>();
    }

    @Override
    public void processElement(InputMessage message, Context ctx, Collector<TrendEvent> out) 
            throws Exception {
                
        Integer locationID = ctx.getCurrentKey();
        long timestamp = ctx.timerService().currentWatermark();

        if (!trendDetectorsMap.containsKey(locationID)) {
            trendDetectorsMap.put(locationID, new TrendDetector(socketPath));
        }

        TrendDetector trendDetector = this.trendDetectorsMap.get(locationID);

        TrendDetector.ProcessingResult result = trendDetector.processMessage(message, timestamp);
        
        if (result != null && result.getNewTrends() != null) {
            for (Trend trend : result.getNewTrends()) {
                Map<String, Object> eventInfo = new HashMap<>();
                eventInfo.put("keywords", trend.getKeywords());
                
                TrendEvent event = new TrendEvent(
                    "TREND_CREATED",
                    trend.getKeywords().toString() + trend.getDebugInfo(),
                    locationID,
                    "" + ctx.timerService().currentWatermark()
                );
                
                out.collect(event);
            }
        }
    }
    
    @Override
    public void close() throws Exception {
        if (detector != null) {
            detector = null;
        }
        super.close();
    }
}