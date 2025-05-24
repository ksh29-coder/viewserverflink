package com.viewserver.data.kafka;

import com.viewserver.common.kafka.TopicConstants;
import com.viewserver.data.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing data layer models to their respective Kafka topics.
 * Handles all base layer data publishing with proper key generation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Publish an Account to the base.account topic
     */
    public CompletableFuture<SendResult<String, Object>> publishAccount(Account account) {
        String key = account.getKafkaKey();
        log.debug("Publishing account with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_ACCOUNT, key, account)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published account {} to topic {}", 
                                account.getAccountId(), TopicConstants.BASE_ACCOUNT);
                    } else {
                        log.error("Failed to publish account {} to topic {}: {}", 
                                account.getAccountId(), TopicConstants.BASE_ACCOUNT, ex.getMessage());
                    }
                });
    }
    
    /**
     * Publish an Instrument to the base.instrument topic
     */
    public CompletableFuture<SendResult<String, Object>> publishInstrument(Instrument instrument) {
        String key = instrument.getKafkaKey();
        log.debug("Publishing instrument with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_INSTRUMENT, key, instrument)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published instrument {} to topic {}", 
                                instrument.getInstrumentId(), TopicConstants.BASE_INSTRUMENT);
                    } else {
                        log.error("Failed to publish instrument {} to topic {}: {}", 
                                instrument.getInstrumentId(), TopicConstants.BASE_INSTRUMENT, ex.getMessage());
                    }
                });
    }
    
    /**
     * Publish a SOD Holding to the base.sod-holding topic
     */
    public CompletableFuture<SendResult<String, Object>> publishSODHolding(SODHolding holding) {
        String key = holding.getKafkaKey();
        log.debug("Publishing SOD holding with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_SOD_HOLDING, key, holding)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published SOD holding {} to topic {}", 
                                holding.getHoldingId(), TopicConstants.BASE_SOD_HOLDING);
                    } else {
                        log.error("Failed to publish SOD holding {} to topic {}: {}", 
                                holding.getHoldingId(), TopicConstants.BASE_SOD_HOLDING, ex.getMessage());
                    }
                });
    }
    
    /**
     * Publish a Price to the base.price topic
     */
    public CompletableFuture<SendResult<String, Object>> publishPrice(Price price) {
        String key = price.getKafkaKey();
        log.debug("Publishing price with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_PRICE, key, price)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published price for instrument {} to topic {}", 
                                price.getInstrumentId(), TopicConstants.BASE_PRICE);
                    } else {
                        log.error("Failed to publish price for instrument {} to topic {}: {}", 
                                price.getInstrumentId(), TopicConstants.BASE_PRICE, ex.getMessage());
                    }
                });
    }
    
    /**
     * Publish an Intraday Cash movement to the base.intraday-cash topic
     */
    public CompletableFuture<SendResult<String, Object>> publishIntradayCash(IntradayCash cash) {
        String key = cash.getKafkaKey();
        log.debug("Publishing intraday cash with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_INTRADAY_CASH, key, cash)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published intraday cash for account {} to topic {}", 
                                cash.getAccountId(), TopicConstants.BASE_INTRADAY_CASH);
                    } else {
                        log.error("Failed to publish intraday cash for account {} to topic {}: {}", 
                                cash.getAccountId(), TopicConstants.BASE_INTRADAY_CASH, ex.getMessage());
                    }
                });
    }
    
    /**
     * Publish an Order to the base.order-events topic
     */
    public CompletableFuture<SendResult<String, Object>> publishOrder(Order order) {
        String key = order.getKafkaKey();
        log.debug("Publishing order with key: {}", key);
        
        return kafkaTemplate.send(TopicConstants.BASE_ORDER_EVENTS, key, order)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published order {} to topic {}", 
                                order.getOrderId(), TopicConstants.BASE_ORDER_EVENTS);
                    } else {
                        log.error("Failed to publish order {} to topic {}: {}", 
                                order.getOrderId(), TopicConstants.BASE_ORDER_EVENTS, ex.getMessage());
                    }
                });
    }
} 