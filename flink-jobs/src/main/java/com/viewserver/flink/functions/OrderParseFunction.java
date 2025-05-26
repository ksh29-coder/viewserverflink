package com.viewserver.flink.functions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.data.model.Order;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse Order JSON messages from Kafka into Order objects
 */
public class OrderParseFunction implements MapFunction<String, Order> {
    
    private static final Logger log = LoggerFactory.getLogger(OrderParseFunction.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    @Override
    public Order map(String json) throws Exception {
        try {
            Order order = objectMapper.readValue(json, Order.class);
            log.debug("Parsed order: {} for instrument {} (status: {})", 
                     order.getOrderId(), order.getInstrumentId(), order.getOrderStatus());
            return order;
        } catch (Exception e) {
            log.error("Failed to parse order JSON: {}", json, e);
            return null; // Return null for invalid records
        }
    }
} 