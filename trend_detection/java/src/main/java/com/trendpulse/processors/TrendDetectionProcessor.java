package com.trendpulse.processors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.trendpulse.TrendDetector;
import com.trendpulse.items.InputMessage;
import com.trendpulse.items.TrendDetected;
import com.trendpulse.lib.TimeUtils;
import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.TrendStatsInfo;
import com.trendpulse.schema.EventType;
import com.trendpulse.schema.TrendActivatedInfo;


public class TrendDetectionProcessor extends KeyedProcessFunction<Tuple2<Integer, String>, InputMessage, TrendEvent> {

    private static final long serialVersionUID = 1L;

    private final String socketPath;
    private final int trendStatsWindowMinutes;
    private transient Map<Integer, TrendDetector> trendDetectorsMap;
    private transient ListState<Long> scheduledWindows;

    public TrendDetectionProcessor(String socketPath, int trendStatsWindowMinutes) {
        this.socketPath = socketPath;
        this.trendStatsWindowMinutes = trendStatsWindowMinutes;
    }

    @Override
    public void open(Configuration conf) throws Exception {
        this.trendDetectorsMap = new HashMap<>();
        this.scheduledWindows = getRuntimeContext().getListState(
            new ListStateDescriptor<>("scheduled_windows", Long.class)
        );
    }

    private void scheduleWindowEndCallback(Context ctx, OffsetDateTime datetime) throws Exception {
        Instant windowStart = TimeUtils.timestampToWindowStart(datetime.toInstant(), trendStatsWindowMinutes);
        Instant windowEnd = windowStart.plus(trendStatsWindowMinutes, ChronoUnit.MINUTES);
        long windowEndMillis = windowEnd.toEpochMilli();

        // Check if window end is already scheduled
        boolean isScheduled = false;
        for (Long scheduled : scheduledWindows.get()) {
            if (scheduled == windowEndMillis) {
                isScheduled = true;
                break;
            }
        }

        if (!isScheduled) {
            ctx.timerService().registerEventTimeTimer(windowEndMillis);
            scheduledWindows.add(windowEndMillis);
        }
    }

    @Override
    public void processElement(InputMessage message, Context ctx, Collector<TrendEvent> out) 
            throws Exception {
                
        Integer locationId = ctx.getCurrentKey().f0;
        String topic = ctx.getCurrentKey().f1;
        scheduleWindowEndCallback(ctx, message.getDatetime());

        if (locationId != 3) {
            return;
        }

        if (message.getDTrendId() != 1) {
            return;
        }

        if (!trendDetectorsMap.containsKey(locationId)) {
            trendDetectorsMap.put(locationId, new TrendDetector(locationId, topic, socketPath, trendStatsWindowMinutes));
        }

        TrendDetector trendDetector = this.trendDetectorsMap.get(locationId);
        TrendDetector.ProcessingResult result = trendDetector.processMessage(message, ctx.timestamp());
        
        if (result != null ) {
            for (TrendDetected trend : result.getActivatedTrends()) {
                // Map<String, Object> eventInfo = new HashMap<>();
                // eventInfo.put("keywords", trend.getKeywords());
                // eventInfo.put("centroid", trend.getCentroid());
                // eventInfo.put(
                //     "sampleMessages", 
                //     trend.getMessages().stream().limit(10).map(m -> m.getText()).collect(Collectors.toList()));

                
                // Map<String, Object> debug = new HashMap<>();
                // debug.put("location_ids", trend.getDLocationIds());
                // debug.put("trend_ids", trend.getDebugTrendIds());
                // eventInfo.put("debug", debug);

                TrendEvent event = new TrendEvent(
                    EventType.TREND_ACTIVATED,
                    trend.getId(),
                    locationId,
                    topic,
                    new TrendActivatedInfo(
                        new ArrayList<>(trend.getKeywords()),
                        Arrays.asList(ArrayUtils.toObject(trend.getCentroid())),
                        trend.getMessages().stream().limit(10).map(m -> m.getText()).collect(Collectors.toList())
                    ))
                ;
                
                out.collect(event);
            }
        }

        for (TrendDetected trend : result.getDeActivatedTrends()) {
            TrendEvent event = new TrendEvent(
                EventType.TREND_DEACTIVATED,
                trend.getId(),
                locationId,
                topic,
                null
            );
            
            out.collect(event);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TrendEvent> out) throws Exception {
        // Remove this timestamp from scheduled windows
        List<Long> currentScheduled = new ArrayList<>();
        scheduledWindows.get().forEach(currentScheduled::add);
        currentScheduled.remove(timestamp);
        scheduledWindows.clear();
        
        currentScheduled.forEach(t -> {
            try {
                scheduledWindows.add(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Integer locationId = ctx.getCurrentKey().f0;
        String topic = ctx.getCurrentKey().f1;
        Instant windowEnd = Instant.ofEpochMilli(timestamp);
        Instant windowStart = windowEnd.minus(trendStatsWindowMinutes, ChronoUnit.MINUTES);

        TrendDetector trendDetector = trendDetectorsMap.get(locationId);
        
        if (trendDetector != null) {
            for (TrendDetected trend : trendDetector.getTrends()) {

                // Do not emit stats too early
                if (windowEnd.toEpochMilli() < trend.getCreatedAt()) {
                    continue;
                }

                // Map<String, Object> eventInfo = new HashMap<>();
                // eventInfo.put("window_start", windowStart.toString());
                // eventInfo.put("window_end", windowEnd.toString());
                // eventInfo.put("window_stats", trend.getStats().getWindowStats(windowStart));

                // Map<String, Object> debug = new HashMap<>();
                // debug.put("location_ids", trend.getDebugLocationIds());
                // debug.put("trend_ids", trend.getDebugTrendIds());
                // eventInfo.put("debug", debug);

                TrendEvent event = new TrendEvent(
                    EventType.TREND_STATS,
                    trend.getId(),
                    locationId,
                    topic,
                    new TrendStatsInfo(trend.getStats().getWindowStats(windowStart))
                );

                out.collect(event);
            }
        }
    }
}