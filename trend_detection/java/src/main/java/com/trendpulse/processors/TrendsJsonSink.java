package com.trendpulse.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;

import com.trendpulse.schema.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.io.Serializable;

public class TrendsJsonSink extends CoProcessFunction<TrendEvent, TrendDataWrittenEvent, Void> 
        implements CheckpointedFunction {
    
    private transient ObjectMapper objectMapper;
    private transient ArrayNode trendsArray;
    private transient Map<String, Set<String>> trendTimeseriesPoints;
    private final String outputPath;
    private static final String OUTPUT_FILENAME = "trends.json";
    
    private transient ListState<TrendState> trendStateListState;
    
    public TrendsJsonSink() {
        this.outputPath = "./output";
    }
    
    private static class TrendState implements Serializable {
        public String trendJson; 
        public Set<String> timeseriesPoints;
        
        public TrendState(String trendJson, Set<String> timeseriesPoints) {
            this.trendJson = trendJson;
            this.timeseriesPoints = timeseriesPoints;
        }
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        ListStateDescriptor<TrendState> descriptor = 
            new ListStateDescriptor<>("trends-state", TrendState.class);
        trendStateListState = context.getOperatorStateStore().getListState(descriptor);
        
        objectMapper = new ObjectMapper();
        trendsArray = objectMapper.createArrayNode();
        trendTimeseriesPoints = new HashMap<>();
        
        if (context.isRestored()) {
            for (TrendState state : trendStateListState.get()) {
                ObjectNode trendNode = (ObjectNode) objectMapper.readTree(state.trendJson);
                trendsArray.add(trendNode);
                trendTimeseriesPoints.put(trendNode.get("id").asText(), state.timeseriesPoints);
            }
        }
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        trendStateListState.clear();
        
        for (int i = 0; i < trendsArray.size(); i++) {
            ObjectNode trendNode = (ObjectNode) trendsArray.get(i);
            String trendId = trendNode.get("id").asText();
            Set<String> timeseriesPoints = trendTimeseriesPoints.getOrDefault(trendId, new TreeSet<>());
            
            trendStateListState.add(new TrendState(
                trendNode.toString(),
                timeseriesPoints
            ));
        }
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Files.createDirectories(Paths.get(outputPath));
    }

    @Override
    public void processElement1(TrendEvent event, Context ctx, Collector<Void> out) throws Exception {
        switch (event.getEventType()) {
            case TREND_ACTIVATED:
                processTrendActivated(event, ctx);
                break;
            case TREND_DEACTIVATED:
                processTrendDeactivated(event, ctx);
                break;
            case TREND_TILE_INDEX:
                processTileIndex(event, ctx);
                break;
        }
        
        writeToFile();
    }

    @Override
    public void processElement2(TrendDataWrittenEvent event, Context ctx, Collector<Void> out) throws Exception {
        String trendId = event.getTrendId().toString();
        String timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString();
        
        Set<String> timeseriesPoints = trendTimeseriesPoints.computeIfAbsent(trendId, k -> new TreeSet<>());
        timeseriesPoints.add(timestamp);
        
        updateTrendTimeseriesPoints(trendId, timeseriesPoints);
        
        writeToFile();
    }

    private void processTrendActivated(TrendEvent event, Context ctx) throws Exception {
        TrendActivatedInfo eventInfo = (TrendActivatedInfo) event.getInfo();
        String trendId = event.getTrendId().toString();
        
        ObjectNode trendNode = objectMapper.createObjectNode();
        trendNode.put("id", trendId);
        trendNode.put("name", eventInfo.getName().toString().replaceAll("\\s+", ""));
        trendNode.put("is_active", true);
        trendNode.put("started_at", OffsetDateTime.now().toString());
        trendNode.putNull("expired_at");
        trendNode.put("is_global", event.getTrendType() == TrendType.TREND_TYPE_GLOBAL);
        trendNode.put("topic", event.getTopic().toString());
        
        ArrayNode locationsArray = objectMapper.createArrayNode();
        if (event.getTrendInfo() instanceof GlobalTrendInfo) {
            GlobalTrendInfo globalInfo = (GlobalTrendInfo) event.getTrendInfo();
            globalInfo.getLocations().forEach(location -> {
                ObjectNode locationNode = objectMapper.createObjectNode();
                locationNode.put("locationId", location.getLocationId());
                locationsArray.add(locationNode);
            });
        } else if (event.getTrendInfo() instanceof LocalTrendInfo) {
            LocalTrendInfo localInfo = (LocalTrendInfo) event.getTrendInfo();
            ObjectNode locationNode = objectMapper.createObjectNode();
            locationNode.put("locationId", localInfo.getLocationId());
            locationsArray.add(locationNode);
        }
        trendNode.set("locations", locationsArray);

        String baseFilename = eventInfo.getName().toString().toLowerCase().replaceAll("\\s+", "-");
        trendNode.put("heatmap_filename", baseFilename);
        trendNode.put("timeseries_url", String.format("assets/data/v23/%s/timeseries.json", baseFilename));
        trendNode.put("tile_dir", String.format("assets/data/v23/%s", baseFilename));
        
        trendNode.set("tile_index", objectMapper.createObjectNode());
        trendNode.set("timeseries_points", objectMapper.createArrayNode());
        
        trendTimeseriesPoints.put(trendId, new TreeSet<>());
        
        trendsArray.add(trendNode);
    }

    private void processTrendDeactivated(TrendEvent event, Context ctx) throws Exception {
        String trendId = event.getTrendId().toString();
        
        for (int i = 0; i < trendsArray.size(); i++) {
            if (trendsArray.get(i).get("id").asText().equals(trendId)) {
                ((ObjectNode) trendsArray.get(i)).put("is_active", false);
                ((ObjectNode) trendsArray.get(i)).put("expired_at", Instant.ofEpochMilli(ctx.timerService().currentWatermark()).toString());
                break;
            }
        }
    }

    private void processTileIndex(TrendEvent event, Context ctx) throws Exception {
        String trendId = event.getTrendId().toString();
        TileIndexInfo tileInfo = (TileIndexInfo) event.getInfo();
        
        for (int i = 0; i < trendsArray.size(); i++) {
            if (trendsArray.get(i).get("id").asText().equals(trendId)) {
                ObjectNode trendNode = (ObjectNode) trendsArray.get(i);
                ObjectNode tileIndex = objectMapper.createObjectNode();
                
                for (ZoomInfo zoomInfo : tileInfo.getTileIndex()) {
                    String zoom = zoomInfo.getZoom().toString();
                    ArrayNode tilesArray = objectMapper.createArrayNode();
                    zoomInfo.getTiles().forEach(tile -> tilesArray.add(tile.toString()));
                    tileIndex.set(zoom, tilesArray);
                }
                
                trendNode.set("tile_index", tileIndex);
                break;
            }
        }
    }

    private void updateTrendTimeseriesPoints(String trendId, Set<String> timeseriesPoints) throws Exception {
        for (int i = 0; i < trendsArray.size(); i++) {
            if (trendsArray.get(i).get("id").asText().equals(trendId)) {
                ObjectNode trendNode = (ObjectNode) trendsArray.get(i);
                ArrayNode timeseriesArray = objectMapper.createArrayNode();
                timeseriesPoints.forEach(timeseriesArray::add);
                trendNode.set("timeseries_points", timeseriesArray);
                break;
            }
        }
    }

    private void writeToFile() throws Exception {
        String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(trendsArray);
        Files.write(
            Paths.get(outputPath, OUTPUT_FILENAME),
            jsonString.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    @Override
    public void close() throws Exception {
        writeToFile();
    }
}