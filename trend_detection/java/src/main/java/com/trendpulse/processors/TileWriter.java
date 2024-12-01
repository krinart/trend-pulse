package com.trendpulse.processors;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TileWriter extends ProcessFunction<Tuple2<String, String>, Void> {
    
    private final String basePath;
    
    public TileWriter(String basePath) {
        this.basePath = basePath;
    }
    
    @Override
    public void processElement(Tuple2<String, String> value, Context ctx, Collector<Void> out) throws Exception {
        String filePath = value.f0;
        String tileData = value.f1;
        
        String fullPath = Paths.get(basePath, filePath).toString();
        
        // Create directories if they don't exist
        Files.createDirectories(Paths.get(fullPath).getParent());
        
        // Write tile data to file
        Files.write(Paths.get(fullPath), tileData.getBytes());
    }
}