package com.trendpulse.processors;

import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;

import com.trendpulse.items.InputMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class EvenTopicDistributionPartitioner implements Partitioner<String> {
    private final ConcurrentHashMap<String, Integer> topicToPartition = new ConcurrentHashMap<>();
    private final AtomicInteger nextPartition = new AtomicInteger(0);

    @Override
    public int partition(String topic, int numPartitions) {
        return topicToPartition.computeIfAbsent(topic, k -> {
            int partition = nextPartition.getAndIncrement() % numPartitions;
            return partition;
        });
    }

    // Usage example with a stream
    public static DataStream<InputMessage> partitionByTopicEvenly(
            DataStream<InputMessage> stream,
            KeySelector<InputMessage, String> topicExtractor) {
        
        return stream.partitionCustom(
            new EvenTopicDistributionPartitioner(),
            topicExtractor
        );
    }
}