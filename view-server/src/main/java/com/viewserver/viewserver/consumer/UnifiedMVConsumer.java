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
 * Kafka consumer for UnifiedMarketValue aggregation data.
 * Consumes unified holding and order data with market value calculations from Flink.
 * This ensures price consistency between holdings and orders by using the same price source.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnifiedMVConsumer {
    
    private final CacheService cacheService;
    
    /**
     * Consume UnifiedMarketValue data from aggregation.unified-mv topic
     */
    @KafkaListener(
            topics = "aggregation.unified-mv",
            groupId = "view-server-unified-mv-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUnifiedMV(
            @Payload String unifiedMVJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.debug("Received UnifiedMV from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        
        try {
            // Cache the UnifiedMV data
            cacheService.cacheUnifiedMVFromJson(unifiedMVJson);
            
            log.debug("Successfully processed UnifiedMV from offset {}", offset);
            
        } catch (Exception e) {
            log.error("Failed to process UnifiedMV from topic {}, partition {}, offset {}: {}", 
                     topic, partition, offset, e.getMessage());
            log.error("UnifiedMV JSON content: {}", unifiedMVJson);
        }
    }
} 