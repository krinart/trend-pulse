package com.trendpulse.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import com.trendpulse.items.TrendEvent;

public class TrendStatsRouter extends ProcessFunction<TrendEvent, TrendEvent> {
    
    private static final String TREND_STATS = "TREND_STATS";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
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
        if (!TREND_STATS.equals(value.getEventType())) {
            return;
        }
        
        String trendId = value.getTrendId();
        JsonNode eventInfo = objectMapper.readTree(value.getEventInfo());
        String timestamp = eventInfo.get("window_start").asText();
        
        ObjectNode timeseriesItem = objectMapper.createObjectNode();
        timeseriesItem.put("timestamp", timestamp);
        timeseriesItem.put("count", eventInfo.get("window_stats").get("count").asInt());
        
        ctx.output(timeseriesOutput, new Tuple2<>(trendId, timeseriesItem.toString()));
        
        JsonNode geoStats = eventInfo.get("window_stats").get("geo_stats");
        for (JsonNode zoomStats : geoStats) {
            int zoom = zoomStats.get("zoom").asInt();
            for (JsonNode tile : zoomStats.get("stats")) {
                int tileX = tile.get("tile_x").asInt();
                int tileY = tile.get("tile_y").asInt();
                
                String tilePath = String.format("%s/timeseries/%s/%d/%d_%d.json",
                    trendId, timestamp, zoom, tileX, tileY);
                String tileData = tile.get("sampled_points").toString();
                
                ctx.output(tileOutput, new Tuple2<>(tilePath, tileData));
            }
        }
    }
}