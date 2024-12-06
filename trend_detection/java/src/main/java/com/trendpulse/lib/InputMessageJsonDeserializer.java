package com.trendpulse.lib;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.apache.flink.formats.json.JsonDeserializationSchema;

import com.trendpulse.items.InputMessage;
import com.trendpulse.items.KafkaInputMessage;

public class InputMessageJsonDeserializer extends JsonDeserializationSchema<InputMessage> {
    
    private transient JsonDeserializationSchema<KafkaInputMessage> kafkaDeserializer;

    public InputMessageJsonDeserializer() {
        super(InputMessage.class);
    }

    @Override
    public void open(InitializationContext context) {
        kafkaDeserializer = new JsonDeserializationSchema<KafkaInputMessage>(KafkaInputMessage.class);
        kafkaDeserializer.open(context);
    }

    @Override
    public InputMessage deserialize(byte[] message) throws IOException {
        if (kafkaDeserializer == null) {
            throw new IOException("Received null kafkaDeserializer");
        }
        KafkaInputMessage kafkaMessage = kafkaDeserializer.deserialize(message);
        return convertToInputMessage(kafkaMessage);
    }
    
    private static InputMessage convertToInputMessage(KafkaInputMessage kafkaMessage) {
        InputMessage inputMessage = new InputMessage();
        
        inputMessage.setTopic(kafkaMessage.getTopic());
        inputMessage.setTimestamp(kafkaMessage.getTimestamp());
        inputMessage.setLon(kafkaMessage.getLon());
        inputMessage.setLat(kafkaMessage.getLat());
        inputMessage.setText(kafkaMessage.getText());
        inputMessage.setDTrendId(kafkaMessage.getD_trend_id());
        inputMessage.setDLocationId(kafkaMessage.getD_location_id());
        
        inputMessage.setDatetime(OffsetDateTime.parse(kafkaMessage.getTimestamp()));
        // System.out.println(inputMessage.getDatetime().toInstant().toString());
        
        Integer nearestLocationId = LocationUtils.findNearestLocation(
            kafkaMessage.getLat(), 
            kafkaMessage.getLon()
        );
        if (nearestLocationId != null) {
            inputMessage.setLocationId(nearestLocationId);
        }
        
        return inputMessage;
    }
}
