package com.trendpulse.processors;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TimeseriesWriter extends KeyedProcessFunction<CharSequence, Tuple3<CharSequence, String, String>, Void> {
    
    private final String basePath;
    
    public TimeseriesWriter(String basePath) {
        this.basePath = basePath;
    }
    
    @Override
    public void processElement(Tuple3<CharSequence, String, String> value, Context ctx, Collector<Void> out) throws Exception {
        String trendId = value.f1;
        String line = value.f2;

        String filePath = Paths.get(basePath, trendId, "timeseries.json").toString();
        
        // Create directories if they don't exist
        Files.createDirectories(Paths.get(filePath).getParent());
        
        // Append line to file
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(line + "\n");
        }
    }
}