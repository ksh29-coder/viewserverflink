package com.viewserver.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.flink.model.HoldingMV;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka serialization schema for HoldingMV objects.
 * Converts HoldingMV objects to JSON for publishing to Kafka topics.
 */
public class HoldingMVKafkaSerializationSchema implements SerializationSchema<HoldingMV> {
    
    private static final Logger log = LoggerFactory.getLogger(HoldingMVKafkaSerializationSchema.class);
    
    // Transient to avoid serialization issues
    private transient ObjectMapper objectMapper;
    
    @Override
    public byte[] serialize(HoldingMV holdingMV) {
        try {
            // Lazy initialization to avoid serialization issues
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
            }
            
            String json = objectMapper.writeValueAsString(holdingMV);
            log.debug("Serialized HoldingMV: {}", json);
            return json.getBytes();
        } catch (Exception e) {
            log.error("Failed to serialize HoldingMV for instrument {} in account {}: {}", 
                     holdingMV.getInstrumentId(), holdingMV.getAccountId(), e.getMessage());
            return new byte[0];
        }
    }
} 