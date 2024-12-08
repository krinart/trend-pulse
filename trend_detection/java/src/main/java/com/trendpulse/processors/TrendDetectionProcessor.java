package com.trendpulse.processors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.trendpulse.TrendDetector;
import com.trendpulse.items.InputMessage;
import com.trendpulse.items.TrendDetected;
import com.trendpulse.lib.TimeUtils;
import com.trendpulse.lib.TrendNameGenerator;
import com.trendpulse.schema.*;


public class TrendDetectionProcessor extends KeyedProcessFunction<Tuple2<Integer, String>, InputMessage, TrendEvent> {

    private static final long serialVersionUID = 1L;

    private final String socketPath;
    private final int trendStatsWindowMinutes;
    private transient Map<Tuple2<Integer, String>, TrendDetector> trendDetectorsMap;
    private transient ListState<Long> scheduledWindows;

    private transient TrendNameGenerator trendNameGenerator;

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

        trendNameGenerator = new TrendNameGenerator();
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

        // if (locationId != 16) {
        //     return;
        // }

        if (message.getDTrendId() != 1) {
            return;
        }

        if (!trendDetectorsMap.containsKey(ctx.getCurrentKey())) {
            trendDetectorsMap.put(ctx.getCurrentKey(), new TrendDetector(locationId, topic, socketPath, trendStatsWindowMinutes));
        }

        TrendDetector trendDetector = this.trendDetectorsMap.get(ctx.getCurrentKey());
        TrendDetector.ProcessingResult result = trendDetector.processMessage(message, ctx.timestamp());
        
        String events = "";

        if (result != null ) {
            for (TrendDetected trend : result.getActivatedTrends()) {
                events += " TREND_ACTIVATED ";

                List<CharSequence> keywords = new ArrayList<>(trend.getKeywords());
                List<CharSequence> sampleMessages = trend.getMessages().stream().limit(10).map(m -> m.getText()).collect(Collectors.toList());

                TrendEvent event = new TrendEvent(
                    EventType.TREND_ACTIVATED,
                    trend.getId(),
                    TrendType.TREND_TYPE_LOCAL,
                    trend.getTopic(),
                    new LocalTrendInfo(trend.getLocationId()),
                    new TrendActivatedInfo(
                        trendNameGenerator.generateTrendName(keywords, sampleMessages),
                        keywords,
                        Arrays.asList(ArrayUtils.toObject(trend.getCentroid())),
                        sampleMessages
                    ));
                
                out.collect(event);
            }
        }

        for (TrendDetected trend : result.getDeActivatedTrends()) {
            
            events += " TREND_DEACTIVATED ";
            TrendEvent event = new TrendEvent(
                EventType.TREND_DEACTIVATED,
                trend.getId(),
                TrendType.TREND_TYPE_LOCAL,
                trend.getTopic(),
                new LocalTrendInfo(trend.getLocationId()),
                null
            );
            
            out.collect(event);
        }

        // Files.write(
        //     // Paths.get("local_messages_5_min.csv"),
        //     Paths.get("kafka_messages_4_partitions.csv"),
        //     String.format(
        //         "%d, %d, %d, %s\n", 
        //         ctx.timestamp(), 
        //         ctx.timerService().currentWatermark(), 
        //         (ctx.timestamp() - ctx.timerService().currentWatermark()) / 1000 / 60,
        //         events
        //     ).getBytes(),
        //     StandardOpenOption.CREATE,
        //     StandardOpenOption.APPEND
        // );
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

        Instant windowEnd = Instant.ofEpochMilli(timestamp);
        Instant windowStart = windowEnd.minus(trendStatsWindowMinutes, ChronoUnit.MINUTES);

        TrendDetector trendDetector = trendDetectorsMap.get(ctx.getCurrentKey());
        
        if (trendDetector != null) {
            for (TrendDetected trend : trendDetector.getTrends()) {

                // Do not emit stats too early
                if (windowEnd.toEpochMilli() < trend.getCreatedAt()) {
                    continue;
                }

                TrendEvent event = new TrendEvent(
                    EventType.TREND_STATS,
                    trend.getId(),
                    TrendType.TREND_TYPE_LOCAL,
                    trend.getTopic(),
                    new LocalTrendInfo(trend.getLocationId()),
                    new TrendStatsInfo(trend.getStats().getWindowStats(windowStart))
                );

                out.collect(event);
            }
        }
    }
}