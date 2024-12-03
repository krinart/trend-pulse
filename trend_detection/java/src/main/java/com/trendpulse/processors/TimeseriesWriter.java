package com.trendpulse.processors;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class TimeseriesWriter extends KeyedProcessFunction<CharSequence, Tuple3<CharSequence, String, String>, Void> {
    
    private final String basePath;
    
    public TimeseriesWriter(String basePath) {
        this.basePath = basePath;
    }
    
    @Override
    public void processElement(Tuple3<CharSequence, String, String> value, Context ctx, Collector<Void> out) throws Exception {
        String filePath = value.f1;
        String line = value.f2;

        String fullPath = Paths.get(basePath, filePath).toString();

        // Create directories if they don't exist
        Files.createDirectories(Paths.get(fullPath).getParent());

        // System.out.println("Printed: " + filePath + "  -  " + line);
        
        // Write line to file
        Files.write(
            Paths.get(fullPath),
            line.getBytes(),
            StandardOpenOption.APPEND, 
            StandardOpenOption.CREATE);
    }
}