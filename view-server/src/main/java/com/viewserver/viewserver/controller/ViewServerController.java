package com.viewserver.viewserver.controller;

import com.viewserver.data.model.*;
import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Health check endpoint
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> getHealth() {
        return ResponseEntity.ok(new HealthStatus("UP", "View Server is running"));
    }
    
    /**
     * Simple health status class
     */
    public static class HealthStatus {
        public final String status;
        public final String message;
        
        public HealthStatus(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
} 