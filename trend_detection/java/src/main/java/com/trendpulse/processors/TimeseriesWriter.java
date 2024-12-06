package com.trendpulse.processors;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import com.trendpulse.schema.TrendDataEvent;
import com.trendpulse.schema.TrendDataWrittenEvent;
import com.trendpulse.schema.TrendEvent;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class TimeseriesWriter extends KeyedProcessFunction<CharSequence, TrendDataEvent, TrendDataWrittenEvent> {
    
    private final String basePath;
    private final StandardOpenOption openOption;
    
    public TimeseriesWriter(String basePath, boolean append) {
        this.basePath = basePath;
        if (append) {
            this.openOption = StandardOpenOption.APPEND;
        } else {
            this.openOption = StandardOpenOption.WRITE;
        }
    }
    
    @Override
    public void processElement(TrendDataEvent event, Context ctx, Collector<TrendDataWrittenEvent> out) throws Exception {
        String filePath = event.getPath().toString();
        String fullPath = Paths.get(basePath, filePath).toString();
        
        String line = event.getData().toString();

        // Create directories if they don't exist
        Files.createDirectories(Paths.get(fullPath).getParent());

        // System.out.println("Printed: " + filePath + "  -  " + line);
        
        // Write line to file
        Files.write(
            Paths.get(fullPath),
            line.getBytes(),
            StandardOpenOption.CREATE,
            openOption
        );

        out.collect(new TrendDataWrittenEvent(
            event.getTrendId(),
            event.getTimestamp(),
            event.getDataType()
        ));
    }
}