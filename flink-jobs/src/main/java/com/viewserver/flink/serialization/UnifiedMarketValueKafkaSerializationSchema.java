package com.viewserver.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.flink.model.UnifiedMarketValue;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka serialization schema for UnifiedMarketValue objects.
 * Converts UnifiedMarketValue objects to JSON for publishing to Kafka topics.
 */
public class UnifiedMarketValueKafkaSerializationSchema implements SerializationSchema<UnifiedMarketValue> {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedMarketValueKafkaSerializationSchema.class);
    
    // Transient to avoid serialization issues
    private transient ObjectMapper objectMapper;
    
    @Override
    public byte[] serialize(UnifiedMarketValue unifiedMV) {
        try {
            // Lazy initialization to avoid serialization issues
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
            }
            
            String json = objectMapper.writeValueAsString(unifiedMV);
            log.debug("Serialized UnifiedMarketValue: {}", json);
            return json.getBytes();
        } catch (Exception e) {
            log.error("Failed to serialize UnifiedMarketValue for {} {} in account {}: {}", 
                     unifiedMV.getRecordType(), unifiedMV.getInstrumentId(), unifiedMV.getAccountId(), e.getMessage());
            return new byte[0];
        }
    }
} 