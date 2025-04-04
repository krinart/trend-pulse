package com.trendpulse.processors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroModule;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendpulse.schema.*;


public class TrendStatsRouter extends KeyedProcessFunction<CharSequence, TrendEvent, TrendEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(TrendStatsRouter.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new AvroModule())
        .addMixIn(Point.class, IgnoreSchemaPropertyConfig.class);
    
    private final OutputTag<TrendDataEvent> timeseriesOutput;
    private final OutputTag<TrendDataEvent> tileOutput;
    
    public TrendStatsRouter() {
        this.timeseriesOutput = new OutputTag<>("timeseries") {};
        this.tileOutput = new OutputTag<>("tiles") {};
    }

    public OutputTag<TrendDataEvent> getTimeseriesOutput() {
        return this.timeseriesOutput;
    }

    public OutputTag<TrendDataEvent> getTileOutput() {
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
        
        // LOG.info("Routing trend: {}", event.getTrendId());

        // System.out.println("Routed - trend: " + trendId + " | timestamp: " + timestamp );

        ObjectNode timeseriesItem = objectMapper.createObjectNode();
        timeseriesItem.put("timestamp", timestamp);
        timeseriesItem.put("count", windowStats.getCount());

        Object trendInfo = event.getTrendInfo();
        String prefix;

        if (trendInfo instanceof LocalTrendInfo) {
            prefix = String.valueOf(((LocalTrendInfo)trendInfo).getLocationId());
        } else {
            // prefix = ((GlobalTrendInfo) trendInfo).getLocations().stream().map(l -> String.valueOf(l.getLocationId())).collect(Collectors.joining(", "));
            prefix = "global";
        }

        String timeSeriesPath = Paths.get(prefix + "__" + trendId, "timeseries.json").toString();
        // LOG.info("TrendStatsRouter: {}", timeSeriesPath);
        // System.out.println("timeSeriesPath: " + timeSeriesPath);
        TrendDataEvent timeseriesDataEvent = new TrendDataEvent(
            event.getTrendId(), 
            windowStats.getWindowStart(), 
            timeSeriesPath,
            timeseriesItem.toString() + "\n",
            TrendDataType.DATA_TYPE_TIMESERIES
        );

        // ctx.output(timeseriesOutput, new Tuple3<>(event.getTrendId(), timeSeriesPath, timeseriesItem.toString() + "\n"));
        ctx.output(timeseriesOutput, timeseriesDataEvent);

        List<ZoomStats> geoStats = windowStats.getGeoStats();
        for (ZoomStats zoomStats : geoStats) {
            int zoom = zoomStats.getZoom();
            for (TileStats tile : zoomStats.getStats()) {
                int tileX = tile.getTileX();
                int tileY = tile.getTileY();
                
                String tilePath = String.format("%s/timeseries/%s/%d/%d_%d.json",
                    prefix + "__" + trendId, timestamp, zoom, tileX, tileY);
                String tileData = objectMapper.writeValueAsString(tile.getSampledPoints());
                
                // LOG.info("TrendStatsRouter: {}", tilePath);
                // System.out.println("tilePath: " + tilePath);
                TrendDataEvent geoDataEvent = new TrendDataEvent(
                    event.getTrendId(), 
                    windowStats.getWindowStart(), 
                    tilePath,
                    tileData,
                    TrendDataType.DATA_TYPE_GEO
                );
                
                ctx.output(tileOutput, geoDataEvent);
            }
        }

        out.collect(TrendEvent.newBuilder()
            .setEventType(EventType.TREND_TILE_INDEX)
            .setTrendId(trendId)
            .setTrendType(event.getTrendType())
            .setTopic(event.getTopic())
            .setTrendInfo(event.getTrendInfo())
            .setInfo(getTileIndex(windowStats.getGeoStats()))
            .build()
        );
    }

    private TileIndexInfo getTileIndex(List<ZoomStats> zoomStats) {
        List<ZoomInfo> zoomInfoList = zoomStats.stream()
            .<ZoomInfo>map(zoomStat -> 
                ZoomInfo.newBuilder()
                    .setZoom(String.valueOf(zoomStat.getZoom()))
                    .setTiles(
                        zoomStat.getStats().stream()
                            .map(tileStat -> tileStat.getTileX() + "_" + tileStat.getTileY())
                            .sorted()
                            .collect(Collectors.toList())
                    )
                    .build()
            )
            .sorted((a, b) -> Integer.compare(
                Integer.parseInt(a.getZoom().toString()), 
                Integer.parseInt(b.getZoom().toString())
            ))
            .collect(Collectors.toList());

        return TileIndexInfo.newBuilder()
            .setTileIndex(zoomInfoList)
            .build();
    }
}

abstract class IgnoreSchemaPropertyConfig {
    @JsonIgnore
    abstract void getSpecificData();
 }