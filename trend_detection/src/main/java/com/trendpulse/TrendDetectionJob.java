package com.trendpulse;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendpulse.items.InputMessage;
import com.trendpulse.lib.InputMessageJsonDeserializer;
import com.trendpulse.lib.LocationUtils;
import com.trendpulse.processors.LocalTrendsWriter;
import com.trendpulse.processors.TrendDetectionProcessor;
import com.trendpulse.processors.TrendStatsRouter;
import com.trendpulse.processors.TrendWriter;
import com.trendpulse.processors.TrendsJsonSink;
import com.trendpulse.processors.TrendManagementProcessor;
import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.EventType;
import com.trendpulse.schema.LocalTrendInfo;
import com.trendpulse.schema.TrendDataWrittenEvent;

public class TrendDetectionJob {
    
    static final String TOPIC = "input-messages-v26-2p";
    static final String CONSUMER_CONFIG_FILE_PATH = "consumer.config";

    // /opt/flink/data/messages_rows.json
    static String DEFAULT_DATA_PATH = "/Users/viktor/workspace/ds2/trend_detection/data/messages_rows_with_id_v26_500.json";
    static String DEFAULT_SOCKET_PATH = "/tmp/embedding_server.sock";
    static String DEFAULT_OUTPUT_PATH = "./output";
    static int DEFAULT_LIMIT = 10;

    private static int trendStatsWindowMinutes = 5;

    public static void main(String[] args) throws Exception {
        String socketPath = System.getenv().getOrDefault("SOCKET_PATH", DEFAULT_SOCKET_PATH);
        
        // Log the configuration
        System.out.println("Running with configuration:");
        System.out.println("  Socket path: " + socketPath);
        
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // env.setParallelism(1);
        env.getConfig().setAutoWatermarkInterval(50);

        // Trends source
        DataStream<TrendEvent> localTrendEvents = getKafkaMessages(env)
        // DataStream<TrendEvent> localTrendEvents = getLocalMessages(env, -1, DEFAULT_DATA_PATH)
            .keyBy(new KeySelector<InputMessage, Tuple2<Integer, String>>() {
                @Override
                public Tuple2<Integer, String> getKey(InputMessage message) {
                    return new Tuple2<>(message.getLocationId(), message.getTopic());
                }
            })
            .process(new TrendDetectionProcessor(socketPath, trendStatsWindowMinutes))
            .name("Trend Detection");

        // Apply TrendManagementProcessor
        DataStream<TrendEvent> globalTrendEvents = localTrendEvents
            .keyBy(e -> e.getTopic())
            .process(new TrendManagementProcessor(trendStatsWindowMinutes))
            .name("Trend Management");

        // Write output
        TrendStatsRouter statsRouter = new TrendStatsRouter();
        Pair<DataStream<TrendEvent>, DataStream<TrendDataWrittenEvent>> localOutuput = output(localTrendEvents, statsRouter);
        Pair<DataStream<TrendEvent>, DataStream<TrendDataWrittenEvent>> globalOutput = output(globalTrendEvents, statsRouter);

        DataStream<TrendDataWrittenEvent> localTrendsWrittenEvent = localOutuput.getRight();
        DataStream<TrendDataWrittenEvent> globalTrendsWrittenEvent = globalOutput.getRight();

        DataStream<TrendEvent> localGlobalTrendActivatedDeactvatedEvents = localTrendEvents
            .union(globalTrendEvents, localOutuput.getLeft(), globalOutput.getLeft())
            .filter(e -> 
                e.getEventType() == EventType.TREND_ACTIVATED || 
                e.getEventType() == EventType.TREND_DEACTIVATED || 
                e.getEventType() == EventType.TREND_TILE_INDEX
            );
            
        DataStream<TrendDataWrittenEvent> trendsWrittenEvent = localTrendsWrittenEvent
            .union(globalTrendsWrittenEvent);

        localGlobalTrendActivatedDeactvatedEvents
            .connect(trendsWrittenEvent)
            .process(new TrendsJsonSink())
            .name("TrendsJsonSink")
            .setParallelism(1);

        // DEBUG: Print TREND_DEACTIVATED events
        // localTrendEvents
        //     .filter(e -> e.getEventType() ==  EventType.TREND_DEACTIVATED)
        //     .map(event -> String.format(
        //         "%s(%s, %s): %s", 
        //         event.getEventType(), ((LocalTrendInfo) event.getTrendInfo()).getLocationId(), ""))
        //     .name("TREND_DEACTIVATED print ")
        //     .print();       

        // Execute
        env.execute("Trend Detection Job");
    }

    private static Pair<DataStream<TrendEvent>, DataStream<TrendDataWrittenEvent>> output(DataStream<TrendEvent> trendEvents, TrendStatsRouter statsRouter) {
        SingleOutputStreamOperator<TrendEvent> routedStream = trendEvents
            .keyBy(e -> e.getTrendId())
            .process(statsRouter)
            .name("stats-router");
            
        
        String connectionString = System.getenv("AZURE_BLOBSTORAGE_CONNECTION_STRING");
        if (connectionString == null) {
            throw new IllegalStateException("AZURE_BLOBSTORAGE_CONNECTION_STRING is required");
        }

        DataStream<TrendDataWrittenEvent> timeSeriesWriter = routedStream
            .getSideOutput(statsRouter.getTileOutput())
            .keyBy(e -> e.getTrendId())
            .process(new TrendWriter(connectionString, "trend-pulse", ""))
            // .process(new LocalTrendsWriter(DEFAULT_OUTPUT_PATH, false))
            .name("tile-writer");

        DataStream<TrendDataWrittenEvent> tilesWriter = routedStream
            .getSideOutput(statsRouter.getTimeseriesOutput())
            .keyBy(e -> e.getTrendId())
            .process(new TrendWriter(connectionString, "trend-pulse", ""))
            // .process(new LocalTrendsWriter(DEFAULT_OUTPUT_PATH, true))
            .name("timeseries-writer");

        return Pair.of((DataStream<TrendEvent>) routedStream, timeSeriesWriter.union(tilesWriter));
    }

    private static Properties loadKafkaProperties() throws IOException {
        Properties properties = new Properties();

        File configFile = new File("/opt/flink/conf/eventhubs/consumer.config");
        if (configFile.exists()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                System.out.println("Loaded config from mounted file: " + configFile.getAbsolutePath());
            }
        } else {
            try (InputStream input = TrendDetectionJob.class.getClassLoader().getResourceAsStream(CONSUMER_CONFIG_FILE_PATH)) {
                if (input == null) {
                    throw new IOException("Unable to find consumer.config on classpath");
                }
                properties.load(input);
            }
        }

        String connectionString = System.getenv("AZURE_EVENTHUB_CONNECTION_STRING");
        if (connectionString == null) {
            throw new IllegalStateException("AZURE_EVENTHUB_CONNECTION_STRING is required");
        }
        properties.setProperty("sasl.jaas.config", connectionString);

        return properties;
    }

    private static DataStream<InputMessage> getKafkaMessages(StreamExecutionEnvironment env) throws IOException {
        KafkaSource<InputMessage> kafkaSource = KafkaSource.<InputMessage>builder()
            .setTopics(TOPIC)
            .setProperties(loadKafkaProperties())
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new InputMessageJsonDeserializer())
            .build();

        return env
            .fromSource(
                kafkaSource,
                createWatermarkStrategy(),
                "EventHubs Source"
            ).setParallelism(2);
    }

    private static WatermarkStrategy<InputMessage> createWatermarkStrategy() {
        return WatermarkStrategy
            .<InputMessage>forBoundedOutOfOrderness(Duration.ofMinutes(15))
            .withIdleness(Duration.ofSeconds(1))
            .withTimestampAssigner((event, timestamp) -> {
                return event.getDatetime().toInstant().toEpochMilli();
            })
            // .withWatermarkAlignment(
            //     "group-1",                      // Alignment group name
            //     Duration.ofSeconds(1),          // Max drift
            //     Duration.ofSeconds(1)           // Update interval
            // )
        ;
    }

    private static DataStream<InputMessage> getLocalMessages(StreamExecutionEnvironment env, int limit, String inputDataPath) throws IOException {
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

        return input
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
                createWatermarkStrategy()
            );
    }
}

