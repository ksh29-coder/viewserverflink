package com.viewserver.viewserver.controller;

import com.viewserver.aggregation.model.HoldingMV;
import com.viewserver.data.model.*;
import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST API controllers for inspecting cached financial data.
 * Provides endpoints to query data consumed from Kafka topics.
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow React frontend access
public class ViewServerController {
    
    private final CacheService cacheService;
    
    /**
     * Get all accounts
     * GET /api/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<Set<Account>> getAllAccounts() {
        log.debug("API request: GET /api/accounts");
        Set<Account> accounts = cacheService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }
    
    /**
     * Get all instruments
     * GET /api/instruments
     */
    @GetMapping("/instruments")
    public ResponseEntity<Set<Instrument>> getAllInstruments() {
        log.debug("API request: GET /api/instruments");
        Set<Instrument> instruments = cacheService.getAllInstruments();
        return ResponseEntity.ok(instruments);
    }
    
    /**
     * Get all current prices
     * GET /api/prices
     */
    @GetMapping("/prices")
    public ResponseEntity<Set<Price>> getAllPrices() {
        log.debug("API request: GET /api/prices");
        Set<Price> prices = cacheService.getAllPrices();
        return ResponseEntity.ok(prices);
    }
    
    /**
     * Get latest price for specific instrument
     * GET /api/prices/{instrumentId}
     */
    @GetMapping("/prices/{instrumentId}")
    public ResponseEntity<Price> getInstrumentPrice(@PathVariable("instrumentId") String instrumentId) {
        log.debug("API request: GET /api/prices/{}", instrumentId);
        Price price = cacheService.getLatestPrice(instrumentId);
        
        if (price != null) {
            return ResponseEntity.ok(price);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get holdings for specific account
     * GET /api/holdings/{accountId}
     */
    @GetMapping("/holdings/{accountId}")
    public ResponseEntity<Set<SODHolding>> getAccountHoldings(@PathVariable("accountId") String accountId) {
        log.debug("API request: GET /api/holdings/{}", accountId);
        Set<SODHolding> holdings = cacheService.getHoldingsForAccount(accountId);
        return ResponseEntity.ok(holdings);
    }
    
    /**
     * Get recent orders
     * GET /api/orders
     */
    @GetMapping("/orders")
    public ResponseEntity<Set<Order>> getRecentOrders() {
        log.debug("API request: GET /api/orders");
        Set<Order> orders = cacheService.getRecentOrders();
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get cash movements for specific account
     * GET /api/cash/{accountId}
     */
    @GetMapping("/cash/{accountId}")
    public ResponseEntity<Set<IntradayCash>> getAccountCashMovements(@PathVariable("accountId") String accountId) {
        log.debug("API request: GET /api/cash/{}", accountId);
        Set<IntradayCash> cashMovements = cacheService.getCashMovementsForAccount(accountId);
        return ResponseEntity.ok(cashMovements);
    }
    
    /**
     * Get cache statistics
     * GET /api/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        log.debug("API request: GET /api/stats");
        CacheService.CacheStats stats = cacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Simple health check endpoint
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", new Date());
        health.put("message", "View Server is running");
        return ResponseEntity.ok(health);
    }

    /**
     * Redis connectivity test
     * GET /api/redis-test
     */
    @GetMapping("/redis-test")
    public ResponseEntity<Map<String, Object>> redisTest() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Try to get a simple key from Redis
            String testKey = "test:connection";
            cacheService.cacheAccount(new Account("TEST", "Test Account", LocalDateTime.now()));
            result.put("status", "SUCCESS");
            result.put("message", "Redis connection is working");
            result.put("timestamp", new Date());
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", "Redis connection failed: " + e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Get holdings with market values for specific account
     * GET /api/holdings-mv/{accountId}
     */
    @GetMapping("/holdings-mv/{accountId}")
    public ResponseEntity<Set<HoldingMV>> getAccountHoldingsWithMarketValue(@PathVariable("accountId") String accountId) {
        log.debug("API request: GET /api/holdings-mv/{}", accountId);
        Set<HoldingMV> holdingsMV = cacheService.getHoldingsMVForAccount(accountId);
        return ResponseEntity.ok(holdingsMV);
    }
    
    /**
     * Get all holdings with market values
     * GET /api/holdings-mv
     */
    @GetMapping("/holdings-mv")
    public ResponseEntity<Set<HoldingMV>> getAllHoldingsWithMarketValue() {
        log.debug("API request: GET /api/holdings-mv");
        Set<HoldingMV> holdingsMV = cacheService.getAllHoldingsMV();
        return ResponseEntity.ok(holdingsMV);
    }
} 