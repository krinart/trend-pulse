package com.trendpulse;

import org.apache.commons.cli.*;


import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.time.Duration;

import com.trendpulse.items.InputMessage;
import com.trendpulse.lib.InputMessageJsonDeserializer;
import com.trendpulse.processors.TrendDetectionProcessor;
import com.trendpulse.processors.TrendStatsRouter;
import com.trendpulse.processors.TrendWriter;
import com.trendpulse.processors.TrendManagementProcessor;
import com.trendpulse.schema.TrendEvent;
import com.trendpulse.schema.EventType;
import com.trendpulse.schema.TrendDataWrittenEvent;

public class TrendDetectionJob {
    
    static final String TOPIC = "input-messages-2";
    static final String CONSUMER_CONFIG_FILE_PATH = "consumer.config";

    // /opt/flink/data/messages_rows.json
    static String DEFAULT_DATA_PATH = "/Users/viktor/workspace/ds2/trend_detection/java/data/messages_rows_with_id_v26_500.json";
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

        DataStream<InputMessage> messagesFromKafka = getKafkaMessages(env);

        // Detect trends
        DataStream<TrendEvent> trendEvents = messagesFromKafka
            .keyBy(new KeySelector<InputMessage, Tuple2<Integer, String>>() {
                @Override
                public Tuple2<Integer, String> getKey(InputMessage message) {
                    return new Tuple2<>(message.getLocationId(), message.getTopic());
                }
            })
            .process(new TrendDetectionProcessor(socketPath, trendStatsWindowMinutes))
            .name("Trend Detection");

        // Apply TrendManagementProcessor
        DataStream<TrendEvent> globalTrendEvents = trendEvents
            .keyBy(e -> e.getTopic())
            .process(new TrendManagementProcessor(trendStatsWindowMinutes))
            .name("Trend Management");

        // Write output
        TrendStatsRouter statsRouter = new TrendStatsRouter();
        DataStream<TrendDataWrittenEvent> localTrendsWrittenEvent = output(trendEvents, statsRouter);
        DataStream<TrendDataWrittenEvent> globalTrendsWrittenEvent = output(globalTrendEvents, statsRouter);

        // DEBUG: Print TREND_DEACTIVATED events
        trendEvents
            .filter(e -> e.getEventType() ==  EventType.TREND_DEACTIVATED)
            .map(event -> String.format(
                "%s(%s, %s): %s", 
                event.getEventType(), event.getTrendId(), event.getLocationId(), ""))
            .name("TREND_DEACTIVATED print ")
            .print();       

        // Execute
        env.execute("Trend Detection Job");
    }

    private static DataStream<TrendDataWrittenEvent> output(DataStream<TrendEvent> trendEvents, TrendStatsRouter statsRouter) {
        SingleOutputStreamOperator<Void> routedStream = trendEvents
            .keyBy(e -> e.getTrendId())
            .process(statsRouter)
            .name("stats-router");
            
        String connectionString = "DefaultEndpointsProtocol=https;AccountName=trenddetection5811932626;AccountKey=Ihxo1OGV+3QBBdPVrfUnc3Zy9gRTJXzqmXyQQOhq6KWrwer8rS9g0ZfuZtlqxR4YiOEgDZToiIdJ+AStAJXqpw==;EndpointSuffix=core.windows.net";

        DataStream<TrendDataWrittenEvent> timeSeriesWriter = routedStream
            .getSideOutput(statsRouter.getTileOutput())
            .keyBy(e -> e.getTrendId())
            .process(new TrendWriter(connectionString, "trend-pulse", ""))
            .name("tile-writer");

        DataStream<TrendDataWrittenEvent> tilesWriter = routedStream
            .getSideOutput(statsRouter.getTimeseriesOutput())
            .keyBy(e -> e.getTrendId())
            .process(new TrendWriter(connectionString, "trend-pulse", ""))
            .name("timeseries-writer");

        return timeSeriesWriter.union(tilesWriter);
    }

    private static Properties loadKafkaProperties() throws IOException {
        Properties properties = new Properties();

        File configFile = new File("/opt/flink/conf/eventhubs/consumer.config");
        if (configFile.exists()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                System.out.println("Loaded config from mounted file: " + configFile.getAbsolutePath());
                return properties;
            }
        }

        // Use getResourceAsStream instead of FileReader
        try (InputStream input = TrendDetectionJob.class.getClassLoader().getResourceAsStream(CONSUMER_CONFIG_FILE_PATH)) {
            if (input == null) {
                throw new IOException("Unable to find consumer.config on classpath");
            }
            properties.load(input);
        }

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
                WatermarkStrategy
                    .<InputMessage>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                    .withTimestampAssigner((event, timestamp) -> {
                        return event.getDatetime().toInstant().toEpochMilli();
                    }),
                "EventHubs Source"
            ).setParallelism(4);
    }
}

