package com.trendpulse;

import org.apache.commons.cli.*;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.core.fs.Path;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.trendpulse.items.InputMessage;
import com.trendpulse.lib.LocationUtils;
import com.trendpulse.processors.TimeseriesWriter;
import com.trendpulse.processors.TrendDetectionProcessor;
import com.trendpulse.processors.TrendStatsRouter;
import com.trendpulse.processors.TrendManagementProcessor;
import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.EventType;

public class TrendDetectionJob {
    
    // /opt/flink/data/messages_rows.json
    static String DEFAULT_DATA_PATH = "/Users/viktor/workspace/ds2/trend_detection/java/data/messages_rows_with_id_v26_500.json";
    static String DEFAULT_SOCKET_PATH = "/tmp/embedding_server.sock";
    static String DEFAULT_OUTPUT_PATH = "./output";
    static int DEFAULT_LIMIT = 10;

    private static int trendStatsWindowMinutes = 5;

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        
        Option limitOption = Option.builder("l")
            .longOpt("limit")
            .hasArg()
            .argName("LIMIT")
            .desc("Limit the number of messages to process")
            .type(Number.class)
            .build();
            
        Option pathOption = Option.builder("p")
            .longOpt("path")
            .hasArg()
            .argName("PATH")
            .desc("Path to the input JSON file")
            .build();

        Option outputOption = Option.builder("o")
            .longOpt("output")
            .hasArg()
            .argName("OUTPUT")
            .desc("Path for output files")
            .build();

        options.addOption(limitOption);
        options.addOption(pathOption);

        // Parse command line arguments
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("TrendDetectionJob", options);
            System.exit(1);
            return;
        }

        // Get values from command line or use defaults
        int limit = DEFAULT_LIMIT;
        if (cmd.hasOption("l")) {
            try {
                limit = Integer.parseInt(cmd.getOptionValue("l"));
            } catch (NumberFormatException e) {
                System.err.println("Invalid limit value: " + cmd.getOptionValue("l"));
                System.exit(1);
            }
        }

        String inputDataPath = cmd.getOptionValue("p", DEFAULT_DATA_PATH);
        String outputPath = cmd.getOptionValue("o", DEFAULT_OUTPUT_PATH);
        String socketPath = System.getenv().getOrDefault("SOCKET_PATH", DEFAULT_SOCKET_PATH);
        
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // env.setParallelism(1);

        // Log the configuration
        System.out.println("Running with configuration:");
        System.out.println("  Input path: " + inputDataPath);
        System.out.println("  Message limit: " + limit);
        System.out.println("  Socket path: " + socketPath);


        final ObjectMapper mapper = new ObjectMapper();
        DataStream<String> input;

        if (limit == -1) {
            FileSource<String> source = FileSource
                .forRecordStreamFormat(new TextLineInputFormat(), new Path(inputDataPath))
                .build();
            
            input = env.fromSource(
                    source,
                    WatermarkStrategy.noWatermarks(),
                    "JSON-File-Source"
                );
        } else {
            List<String> lines = Files.readAllLines(Paths.get(inputDataPath));
    
            if (lines.size() > limit) {
                lines = lines.subList(0, limit);
            }

            input = env.fromCollection(lines);
        }   

        DataStream<InputMessage> messages = input
            .map(new MapFunction<String, InputMessage>() {
                @Override
                public InputMessage map(String jsonLine) throws Exception {
                    InputMessage message = mapper.readValue(jsonLine, InputMessage.class);

                    Integer nearestLocationId = LocationUtils.findNearestLocation(
                        message.getLat(), 
                        message.getLon()
                    );

                    message.setDatetime(OffsetDateTime.parse(message.getTimestamp()));
                    
                    if (nearestLocationId != null) {
                        message.setLocationId(nearestLocationId);
                    }

                    return message;

                }
            })
            .name("JSON-Parser")
            .assignTimestampsAndWatermarks(
                WatermarkStrategy
                    .<InputMessage>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                    .withTimestampAssigner((event, timestamp) -> event.getDatetime().toInstant().toEpochMilli())
            );

        // Detect trends
        DataStream<TrendEvent> trendEvents = messages
            .keyBy(new KeySelector<InputMessage, Tuple2<Integer, String>>() {
                @Override
                public Tuple2<Integer, String> getKey(InputMessage message) {
                    return new Tuple2<>(message.getLocationId(), message.getTopic());
                }
            })
            .process(new TrendDetectionProcessor(socketPath, trendStatsWindowMinutes))
            .name("trend-detection");

        // Apply TrendManagementProcessor
        DataStream<TrendEvent> globalTrendEvents = trendEvents
            .keyBy(e -> e.getTopic())
            .process(new TrendManagementProcessor(trendStatsWindowMinutes))
            // .setParallelism(1)
        ;

        // Write output
        TrendStatsRouter statsRouter = new TrendStatsRouter();
        output(trendEvents, outputPath, statsRouter);
        output(globalTrendEvents, outputPath, statsRouter);

        // DEBUG: Print TREND_DEACTIVATED events
        trendEvents
            .filter(e -> e.getEventType() ==  EventType.TREND_DEACTIVATED)
            .map(event -> String.format(
                "%s(%s, %s): %s", 
                event.getEventType(), event.getTrendId(), event.getLocationId(), ""))
            .print();       

        // Execute
        env.execute("Trend Detection Job");
    }

    private static void output(DataStream<TrendEvent> trendEvents, String outputPath, TrendStatsRouter statsRouter) {
        SingleOutputStreamOperator<TrendEvent> routedStream = trendEvents
            .keyBy(e -> e.getTrendId())
            .process(statsRouter)
            // .setParallelism(1)
            .name("stats-router");
            
        // DataStreamSink<Tuple3<CharSequence, String, String>> tileWriter = routedStream
        //     .getSideOutput(statsRouter.getTileOutput())
        //     .keyBy(e -> e.f0)
        //     .addSink(new CustomFileSink(outputPath, false))
        //     // .setParallelism(1)
        //     .name("tile-writer");

        // DataStreamSink<Tuple3<CharSequence, String, String>> timeSeriesWriter = routedStream
        //     .getSideOutput(statsRouter.getTimeseriesOutput())
        //     .keyBy(e -> e.f0)
        //     .addSink(new CustomFileSink(outputPath, true))
        //     // .setParallelism(1)
        //     .name("timeseries-writer")
        // ;

        DataStream<TrendEvent> timeSeriesWriter = routedStream
            .getSideOutput(statsRouter.getTileOutput())
            .keyBy(e -> e.f0)
            .process(new TimeseriesWriter(outputPath, false))
            // .setParallelism(1)
            .name("tile-writer");

        DataStream<TrendEvent> tilesWriter = routedStream
            .getSideOutput(statsRouter.getTimeseriesOutput())
            .keyBy(e -> e.f0)
            .process(new TimeseriesWriter(outputPath, true))
            // .setParallelism(1)
            .name("timeseries-writer");

        // timeSeriesWriter.union(tilesWriter).process()
        
    }
    
    static class CustomFileSink extends RichSinkFunction<Tuple3<CharSequence, String, String>> {

        private final String basePath;
        private final boolean append;
    
        public CustomFileSink(String basePath, boolean append) {
            this.basePath = basePath;
            this.append = append;
        }
        
        @Override
        public void invoke(Tuple3<CharSequence, String, String> value, Context context) throws IOException {

            // Extract the file path and content from the tuple
            String filePath = value.f1; // Target file path
            String content = value.f2; // Content to write (third element)

            String fullPath = Paths.get(basePath, filePath).toString();

            Files.createDirectories(Paths.get(fullPath).getParent());

            // Open the file in append mode and write the content
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fullPath, append))) {
                writer.write(content);
            }
        }
    }

}

