package com.trendpulse.processors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroModule;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.TrendStatsInfo;
import com.trendpulse.schema.WindowStats;
import com.trendpulse.schema.EventType;
import com.trendpulse.schema.Point;
import com.trendpulse.schema.TileStats;
import com.trendpulse.schema.ZoomStats;


public class TrendStatsRouter extends KeyedProcessFunction<CharSequence, TrendEvent, TrendEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new AvroModule())
        .addMixIn(Point.class, IgnoreSchemaPropertyConfig.class);
    
    private final OutputTag<Tuple3<CharSequence, String, String>> timeseriesOutput;
    private final OutputTag<Tuple3<CharSequence, String, String>> tileOutput;
    
    public TrendStatsRouter() {
        this.timeseriesOutput = new OutputTag<>("timeseries") {};
        this.tileOutput = new OutputTag<>("tiles") {};
    }

    public OutputTag<Tuple3<CharSequence, String, String>> getTimeseriesOutput() {
        return this.timeseriesOutput;
    }

    public OutputTag<Tuple3<CharSequence, String, String>> getTileOutput() {
        return this.tileOutput;
    }
    
    @Override
    public void processElement(TrendEvent event, Context ctx, Collector<TrendEvent> out) throws Exception {
        
        if (event.getEventType() != EventType.TREND_STATS) {
            return;
        }

        String trendId = event.getTrendId().toString();
        TrendStatsInfo eventInfo = (TrendStatsInfo) event.getInfo();
        WindowStats windowStats = eventInfo.getStats();
        String timestamp = Instant.ofEpochSecond(windowStats.getWindowStart()).toString();
        
        ObjectNode timeseriesItem = objectMapper.createObjectNode();
        timeseriesItem.put("timestamp", timestamp);
        timeseriesItem.put("count", windowStats.getCount());

        String timeSeriesPath = Paths.get(trendId, "timeseries.json").toString();
        ctx.output(timeseriesOutput, new Tuple3<>(event.getTopic(), timeSeriesPath, timeseriesItem.toString() + "\n"));
        
        List<ZoomStats> geoStats = windowStats.getGeoStats();
        for (ZoomStats zoomStats : geoStats) {
            int zoom = zoomStats.getZoom();
            for (TileStats tile : zoomStats.getStats()) {
                int tileX = tile.getTileX();
                int tileY = tile.getTileY();
                
                String tilePath = String.format("%s/timeseries/%s/%d/%d_%d.json",
                    trendId, timestamp, zoom, tileX, tileY);
                String tileData = objectMapper.writeValueAsString(tile.getSampledPoints());
                
                ctx.output(tileOutput, new Tuple3<>(event.getTopic(), tilePath, tileData));
            }
        }
    }
}

abstract class IgnoreSchemaPropertyConfig {
    @JsonIgnore
    abstract void getSpecificData();
 }