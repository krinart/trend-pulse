package com.trendpulse;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;


public class TrendTest {

    public static void main(String[] args) throws Exception {
        final Consumer<Long, String> consumer = createConsumer();
        System.out.println("Polling");

        for(ConsumerRecord<Long, String> cr : consumer.poll(5000)) {
            System.out.printf("Consumer Record:(%d, %s, %d, %d)\n", cr.key(), cr.value(), cr.partition(), cr.offset());
        }

    }

    //Each consumer needs a unique client ID per thread
    private static int consumerID = 0;

    static final String TOPIC = "input-messages";
    static final String COMSUMER_CONFIG_FILE_PATH = "/Users/viktor/workspace/ds2/trend_detection/java/src/main/resources/consumer.config";

    static Consumer<Long, String> createConsumer() {
        try {
            final Properties properties = new Properties();
            synchronized (TrendDetectionJob.class) {
                properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "KafkaConsumer#" + consumerID);
                consumerID++;
            }
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            //Get remaining properties from config file
            properties.load(new FileReader(COMSUMER_CONFIG_FILE_PATH));

            // Create the consumer using properties.
            final Consumer<Long, String> consumer = new KafkaConsumer<>(properties);

            // Subscribe to the topic.
            consumer.subscribe(Collections.singletonList(TOPIC));
            return consumer;
            
        } catch (FileNotFoundException e){
            System.out.println("FileNotFoundException: " + e);
            System.exit(1);
            return null;        //unreachable
        } catch (IOException e){
            System.out.println("IOException: " + e);
            System.exit(1);
            return null;        //unreachable
        }
    }

}
