package com.viewserver.aggregation.kafka;

import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for HoldingMV (Holding Market Value) records from the aggregation layer.
 * Consumes enriched holdings with market value calculations and caches them in Redis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingMVConsumer {
    
    private final CacheService cacheService;
    
    /**
     * Consume HoldingMV records from aggregation.holding-mv topic
     */
    @KafkaListener(
            topics = "aggregation.holding-mv",
            groupId = "view-server-holding-mv-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeHoldingMV(
            @Payload String holdingMVJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.debug("Received HoldingMV from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        
        try {
            // Cache the HoldingMV record
            cacheService.cacheHoldingMVFromJson(holdingMVJson);
            
            log.debug("Successfully processed HoldingMV from offset {}", offset);
            
        } catch (Exception e) {
            log.error("Failed to process HoldingMV from topic {}, partition {}, offset {}: {}", 
                     topic, partition, offset, e.getMessage());
            log.error("HoldingMV JSON content: {}", holdingMVJson);
        }
    }
} 