package com.trendpulse.processors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroModule;

import java.time.Instant;
import java.util.List;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.TrendStatsInfo;
import com.trendpulse.schema.WindowStats;
import com.trendpulse.schema.Point;
import com.trendpulse.schema.TileStats;
import com.trendpulse.schema.ZoomStats;


public class TrendStatsRouter extends ProcessFunction<TrendEvent, TrendEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new AvroModule())
        .addMixIn(Point.class, IgnoreSchemaPropertyConfig.class);
    
    private final OutputTag<Tuple2<String, String>> timeseriesOutput;
    private final OutputTag<Tuple2<String, String>> tileOutput;
    
    public TrendStatsRouter() {
        this.timeseriesOutput = new OutputTag<>("timeseries") {};
        this.tileOutput = new OutputTag<>("tiles") {};
    }

    public OutputTag<Tuple2<String, String>> getTimeseriesOutput() {
        return this.timeseriesOutput;
    }

    public OutputTag<Tuple2<String, String>> getTileOutput() {
        return this.tileOutput;
    }
    
    @Override
    public void processElement(TrendEvent value, Context ctx, Collector<TrendEvent> out) throws Exception {
        
        String trendId = value.getTrendId().toString();
        TrendStatsInfo eventInfo = (TrendStatsInfo) value.getInfo();
        WindowStats windowStats = eventInfo.getStats();
        String timestamp = Instant.ofEpochSecond(windowStats.getWindowStart()).toString();
        
        ObjectNode timeseriesItem = objectMapper.createObjectNode();
        timeseriesItem.put("timestamp", timestamp);
        timeseriesItem.put("count", windowStats.getCount());
        
        ctx.output(timeseriesOutput, new Tuple2<>(trendId, timeseriesItem.toString()));
        
        List<ZoomStats> geoStats = windowStats.getGeoStats();
        for (ZoomStats zoomStats : geoStats) {
            int zoom = zoomStats.getZoom();
            for (TileStats tile : zoomStats.getStats()) {
                int tileX = tile.getTileX();
                int tileY = tile.getTileY();
                
                String tilePath = String.format("%s/timeseries/%s/%d/%d_%d.json",
                    trendId, timestamp, zoom, tileX, tileY);
                String tileData = objectMapper.writeValueAsString(tile.getSampledPoints());
                
                ctx.output(tileOutput, new Tuple2<>(tilePath, tileData));
            }
        }
    }
}

abstract class IgnoreSchemaPropertyConfig {
    @JsonIgnore
    abstract void getSpecificData();
 }