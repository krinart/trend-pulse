// File: TrendDetectionJob.java
// package com.example.trendpulse;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.apache.flink.api.java.tuple.Tuple2;
import java.time.Duration;
import java.time.Instant;

import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.core.fs.Path;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.api.common.functions.MapFunction;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class TrendDetectionJob {
    
    static String PATH = "/Users/viktor/workspace/ds2/trend_detection/data/messages_rows.json";

    public static void main(String[] args) throws Exception {

        TfidfKeywordExtractor keywordExtractor = new TfidfKeywordExtractor();


        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid limit argument, using default: " + limit);
            }
        } else {
            System.out.println("No limit specified, using default: " + limit);
        }

        List<String> lines = Files.readAllLines(Paths.get(PATH));
        
        // Optionally limit size
        if (lines.size() > limit) {
            lines = lines.subList(0, limit);
        }

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        final FileSource<String> source = FileSource
            .forRecordStreamFormat(new TextLineInputFormat(), new Path(PATH))
            .build();

        final ObjectMapper mapper = new ObjectMapper();

        DataStream<String> input = env
            .fromCollection(lines);

        // DataStream<String> input = env
        //     .fromSource(
        //         source,
        //         WatermarkStrategy.noWatermarks(),
        //         "JSON-File-Source"
        //     );

        DataStream<InputMessage> messages = input
            .map(new MapFunction<String, InputMessage>() {
                @Override
                public InputMessage map(String jsonLine) throws Exception {
                    InputMessage message = mapper.readValue(jsonLine, InputMessage.class);

                    Integer nearestLocationId = LocationUtils.findNearestLocation(
                        message.getLat(), 
                        message.getLon()
                    );
                    
                    if (nearestLocationId != null) {
                        message.setLocationId(nearestLocationId);
                    }

                    return message;

                }
            })
            .name("JSON-Parser");


        // Transform and detect trends
        DataStream<TrendEvent> trendEvents = messages
            .keyBy(message -> message.getLocationId())
            .process(new TrendDetectionProcessor())
            .name("trend-detection");


        trendEvents
            .map(event -> String.format("%s: %s", event.getEventType(), event.getEventInfo()))
            .print();

        // Execute
        env.execute("Trend Detection Job");
    }
}