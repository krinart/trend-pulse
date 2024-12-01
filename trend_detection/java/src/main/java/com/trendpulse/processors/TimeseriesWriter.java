package com.trendpulse.processors;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TimeseriesWriter extends ProcessFunction<Tuple2<String, String>, Void> {
    
    private final String basePath;
    
    public TimeseriesWriter(String basePath) {
        this.basePath = basePath;
    }
    
    @Override
    public void processElement(Tuple2<String, String> value, Context ctx, Collector<Void> out) throws Exception {
        String trendId = value.f0;
        String line = value.f1;
        
        String filePath = Paths.get(basePath, trendId, "timeseries.json").toString();
        
        // Create directories if they don't exist
        Files.createDirectories(Paths.get(filePath).getParent());
        
        // Append line to file
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(line + "\n");
        }
    }
}