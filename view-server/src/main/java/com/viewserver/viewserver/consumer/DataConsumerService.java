package com.viewserver.viewserver.consumer;

import com.viewserver.data.model.*;
import com.viewserver.viewserver.service.CacheService;
import com.viewserver.computation.streams.AccountOverviewViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumers for all base layer topics.
 * Consumes data and stores it in Redis cache for API access.
 * Also triggers real-time view updates for Account Overview views.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataConsumerService {
    
    private final CacheService cacheService;
    
    @Autowired(required = false)
    private AccountOverviewViewService accountOverviewViewService;
    
    /**
     * Consume account data from base.account topic
     */
    @KafkaListener(topics = "base.account", groupId = "view-server-accounts")
    public void consumeAccount(@Payload String accountJson) {
        try {
            log.debug("Received account data: {}", accountJson);
            cacheService.cacheAccountFromJson(accountJson);
            log.debug("Successfully cached account data");
        } catch (Exception e) {
            log.error("Error processing account data: {}", accountJson, e);
        }
    }
    
    /**
     * Consume instrument data from base.instrument topic
     */
    @KafkaListener(topics = "base.instrument", groupId = "view-server-instruments")
    public void consumeInstrument(@Payload String instrumentJson) {
        try {
            log.debug("Received instrument data: {}", instrumentJson);
            cacheService.cacheInstrumentFromJson(instrumentJson);
            log.debug("Successfully cached instrument data");
        } catch (Exception e) {
            log.error("Error processing instrument data: {}", instrumentJson, e);
        }
    }
    
    /**
     * Consume price data from base.price topic
     */
    @KafkaListener(topics = "base.price", groupId = "view-server-prices")
    public void consumePrice(@Payload String priceJson) {
        try {
            log.debug("Received price data: {}", priceJson);
            cacheService.cachePriceFromJson(priceJson);
            log.debug("Successfully cached price data");
            
            // ✅ Price updates can affect all portfolio values - trigger view refresh
            if (accountOverviewViewService != null) {
                accountOverviewViewService.notifyViewsOfPriceUpdate();
            }
        } catch (Exception e) {
            log.error("Error processing price data: {}", priceJson, e);
        }
    }
    
    /**
     * Consume order event data from base.order-events topic
     */
    @KafkaListener(topics = "base.order-events", groupId = "view-server-orders")
    public void consumeOrder(@Payload String orderJson) {
        try {
            log.debug("Received order data: {}", orderJson);
            cacheService.cacheOrderFromJson(orderJson);
            log.debug("Successfully cached order data");
        } catch (Exception e) {
            log.error("Error processing order data: {}", orderJson, e);
        }
    }
    
    /**
     * Consume SOD holding data from base.sod-holding topic
     */
    @KafkaListener(topics = "base.sod-holding", groupId = "view-server-holdings")
    public void consumeSODHolding(@Payload String holdingJson) {
        try {
            log.debug("Received SOD holding data: {}", holdingJson);
            cacheService.cacheSODHoldingFromJson(holdingJson);
            log.debug("Successfully cached SOD holding data");
        } catch (Exception e) {
            log.error("Error processing SOD holding data: {}", holdingJson, e);
        }
    }
    
    /**
     * Consume intraday cash data from base.intraday-cash topic
     */
    @KafkaListener(topics = "base.intraday-cash", groupId = "view-server-cash")
    public void consumeIntradayCash(@Payload String cashJson) {
        try {
            log.debug("Received intraday cash data: {}", cashJson);
            cacheService.cacheIntradayCashFromJson(cashJson);
            log.debug("Successfully cached intraday cash data");
        } catch (Exception e) {
            log.error("Error processing intraday cash data: {}", cashJson, e);
        }
    }
} 