package com.trendpulse;

import org.apache.commons.cli.*;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.core.fs.Path;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.trendpulse.items.InputMessage;
import com.trendpulse.lib.LocationUtils;
import com.trendpulse.processors.TileWriter;
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

        // Transform and detect trends
        DataStream<TrendEvent> trendEvents = messages
            .keyBy(message -> message.getLocationId())
            .process(new TrendDetectionProcessor(socketPath, trendStatsWindowMinutes))
            .name("trend-detection");


        trendEvents
            .filter(e -> e.getEventType() ==  EventType.TREND_DEACTIVATED)
            .map(event -> String.format(
                "%s(%s, %s): %s", 
                event.getEventType(), event.getTrendId(), event.getLocationId(), ""))
            .print();

        TrendStatsRouter statsRouter = new TrendStatsRouter();
        SingleOutputStreamOperator<TrendEvent> routedStream = trendEvents
            .filter(e -> e.getEventType() == EventType.TREND_STATS)
            .process(statsRouter)
            .name("stats-router");
            
        // Get side outputs and attach writers
        DataStream<Tuple2<String, String>> timeseriesStream = routedStream
            .getSideOutput(statsRouter.getTimeseriesOutput());
        timeseriesStream
            .process(new TimeseriesWriter(outputPath))
            .name("timeseries-writer");
            
        DataStream<Tuple2<String, String>> tileStream = routedStream
            .getSideOutput(statsRouter.getTileOutput());
        tileStream
            .process(new TileWriter(outputPath))
            .name("tile-writer");

        trendEvents
            .filter(e -> e.getEventType() == EventType.TREND_ACTIVATED)
            .process(new TrendManagementProcessor())
            .setParallelism(1);

        // Execute
        env.execute("Trend Detection Job");
    }
}