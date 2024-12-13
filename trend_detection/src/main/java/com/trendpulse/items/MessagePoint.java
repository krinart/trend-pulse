package com.trendpulse.items;

import org.apache.commons.math3.ml.clustering.Clusterable;


public class MessagePoint implements Clusterable {
    private final Message message;
    private final double[] embedding;

    public MessagePoint(Message message) {
        this.message = message;
        this.embedding = message.getEmbedding();
    }

    public Message getMessage() {
        return message;
    }

    @Override
    public double[] getPoint() {
        return message.getEmbedding();
    }
}