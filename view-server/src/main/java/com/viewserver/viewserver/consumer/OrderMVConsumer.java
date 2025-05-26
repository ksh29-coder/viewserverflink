package com.viewserver.viewserver.consumer;

import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for OrderMV (Order Market Value) aggregation data.
 * Consumes enriched order data with market value calculations from Flink.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderMVConsumer {
    
    private final CacheService cacheService;
    
    /**
     * Consume OrderMV data from aggregation.order-mv topic
     */
    @KafkaListener(
            topics = "aggregation.order-mv",
            groupId = "view-server-order-mv-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderMV(
            @Payload String orderMVJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.debug("Received OrderMV from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        
        try {
            // Cache the OrderMV data
            cacheService.cacheOrderMVFromJson(orderMVJson);
            
            log.debug("Successfully processed OrderMV from offset {}", offset);
            
        } catch (Exception e) {
            log.error("Failed to process OrderMV from topic {}, partition {}, offset {}: {}", 
                     topic, partition, offset, e.getMessage());
            log.error("OrderMV JSON content: {}", orderMVJson);
        }
    }
} 