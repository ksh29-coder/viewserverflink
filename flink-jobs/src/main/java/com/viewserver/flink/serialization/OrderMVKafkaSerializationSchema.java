package com.viewserver.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.flink.model.OrderMV;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Kafka serialization schema for OrderMV objects.
 * Converts OrderMV objects to JSON for publishing to Kafka.
 */
public class OrderMVKafkaSerializationSchema implements SerializationSchema<OrderMV> {
    
    private static final Logger log = LoggerFactory.getLogger(OrderMVKafkaSerializationSchema.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public byte[] serialize(OrderMV orderMV) {
        try {
            String json = objectMapper.writeValueAsString(orderMV);
            log.debug("Serialized OrderMV: {} for order {}", orderMV.getInstrumentName(), orderMV.getOrderId());
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to serialize OrderMV for order {}: {}", orderMV.getOrderId(), e.getMessage());
            return new byte[0];
        }
    }
} 