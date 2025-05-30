package com.viewserver.viewserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.aggregation.model.UnifiedMarketValue;
import com.viewserver.data.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for caching consumed Kafka data in Redis.
 * Organizes data by type with structured keys for easy querying.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache key prefixes
    private static final String ACCOUNTS_PREFIX = "accounts:";
    private static final String INSTRUMENTS_PREFIX = "instruments:";
    private static final String PRICES_PREFIX = "prices:";
    private static final String HOLDINGS_PREFIX = "holdings:";
    private static final String ORDERS_PREFIX = "orders:";
    private static final String CASH_PREFIX = "cash:";
    private static final String UNIFIED_MV_PREFIX = "unified-mv:";
    
    // Time-to-live settings
    private static final Duration ACCOUNT_TTL = Duration.ofDays(30); // Static data, long TTL
    private static final Duration INSTRUMENT_TTL = Duration.ofDays(30); // Static data, long TTL
    private static final Duration PRICE_TTL = Duration.ofHours(24); // Price data, daily TTL
    private static final Duration HOLDING_TTL = Duration.ofDays(7); // Holdings, weekly TTL
    private static final Duration ORDER_TTL = Duration.ofDays(1); // Orders, daily TTL
    private static final Duration CASH_TTL = Duration.ofDays(7); // Cash movements, weekly TTL
    private static final Duration UNIFIED_MV_TTL = Duration.ofDays(7); // Unified MV, weekly TTL
    
    // ==================== JSON Parsing Methods ====================
    
    public void cacheAccountFromJson(String json) throws JsonProcessingException {
        Account account = objectMapper.readValue(json, Account.class);
        cacheAccount(account);
    }
    
    public void cacheInstrumentFromJson(String json) throws JsonProcessingException {
        Instrument instrument = objectMapper.readValue(json, Instrument.class);
        cacheInstrument(instrument);
    }
    
    public void cachePriceFromJson(String json) throws JsonProcessingException {
        Price price = objectMapper.readValue(json, Price.class);
        cachePrice(price);
    }
    
    public void cacheOrderFromJson(String json) throws JsonProcessingException {
        Order order = objectMapper.readValue(json, Order.class);
        cacheOrder(order);
    }
    
    public void cacheSODHoldingFromJson(String json) throws JsonProcessingException {
        SODHolding holding = objectMapper.readValue(json, SODHolding.class);
        cacheHolding(holding);
    }
    
    public void cacheIntradayCashFromJson(String json) throws JsonProcessingException {
        IntradayCash cash = objectMapper.readValue(json, IntradayCash.class);
        cacheCashMovement(cash);
    }
    

    
    public void cacheUnifiedMVFromJson(String json) throws JsonProcessingException {
        UnifiedMarketValue unifiedMV = objectMapper.readValue(json, UnifiedMarketValue.class);
        cacheUnifiedMV(unifiedMV);
    }
    
    /**
     * Cache account data
     */
    public void cacheAccount(Account account) {
        try {
            String key = ACCOUNTS_PREFIX + account.getAccountId();
            String json = objectMapper.writeValueAsString(account);
            redisTemplate.opsForValue().set(key, json, ACCOUNT_TTL);
            log.debug("Cached account: {}", account.getAccountId());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache account {}: {}", account.getAccountId(), e.getMessage());
        }
    }
    
    /**
     * Cache instrument data
     */
    public void cacheInstrument(Instrument instrument) {
        try {
            String key = INSTRUMENTS_PREFIX + instrument.getInstrumentId();
            String json = objectMapper.writeValueAsString(instrument);
            redisTemplate.opsForValue().set(key, json, INSTRUMENT_TTL);
            log.debug("Cached instrument: {}", instrument.getInstrumentId());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache instrument {}: {}", instrument.getInstrumentId(), e.getMessage());
        }
    }
    
    /**
     * Cache price data (latest price per instrument)
     */
    public void cachePrice(Price price) {
        try {
            String key = PRICES_PREFIX + price.getInstrumentId();
            String json = objectMapper.writeValueAsString(price);
            redisTemplate.opsForValue().set(key, json, PRICE_TTL);
            log.debug("Cached price: {} = ${}", price.getInstrumentId(), price.getPrice());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache price for {}: {}", price.getInstrumentId(), e.getMessage());
        }
    }
    
    /**
     * Cache SOD holding data
     */
    public void cacheHolding(SODHolding holding) {
        try {
            String key = HOLDINGS_PREFIX + holding.getAccountId() + ":" + holding.getInstrumentId();
            String json = objectMapper.writeValueAsString(holding);
            redisTemplate.opsForValue().set(key, json, HOLDING_TTL);
            log.debug("Cached holding: {} shares of {} for account {}", 
                    holding.getPosition(), holding.getInstrumentId(), holding.getAccountId());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache holding {}: {}", holding.getHoldingId(), e.getMessage());
        }
    }
    
    /**
     * Cache order data
     */
    public void cacheOrder(Order order) {
        try {
            String key = ORDERS_PREFIX + order.getOrderId();
            String json = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set(key, json, ORDER_TTL);
            log.debug("Cached order: {} ({} {})", order.getOrderId(), order.getOrderStatus(), order.getInstrumentId());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
    
    /**
     * Cache intraday cash movement
     */
    public void cacheCashMovement(IntradayCash cash) {
        try {
            // Use timestamp in key to allow multiple cash movements per account
            String key = CASH_PREFIX + cash.getAccountId() + ":" + cash.getDate().toString();
            String json = objectMapper.writeValueAsString(cash);
            redisTemplate.opsForValue().set(key, json, CASH_TTL);
            log.debug("Cached cash movement: {} {} for account {} ({})", 
                    cash.getQuantity(), cash.getInstrumentId(), cash.getAccountId(), cash.getMovementType());
        } catch (JsonProcessingException e) {
            log.error("Failed to cache cash movement for account {}: {}", cash.getAccountId(), e.getMessage());
        }
    }
    

    
    /**
     * Cache UnifiedMarketValue (Unified Holding/Order with Market Value)
     */
    public void cacheUnifiedMV(UnifiedMarketValue unifiedMV) {
        try {
            // Use the cache key from the model
            String key = UNIFIED_MV_PREFIX + unifiedMV.getCacheKey();
            String json = objectMapper.writeValueAsString(unifiedMV);
            redisTemplate.opsForValue().set(key, json, UNIFIED_MV_TTL);
            
            if (unifiedMV.isHolding()) {
                log.debug("Cached Unified HOLDING MV: {} {} shares @ ${} = ${} USD (Account: {})", 
                        unifiedMV.getInstrumentName(),
                        unifiedMV.getPosition(),
                        unifiedMV.getPrice(),
                        unifiedMV.getMarketValueUSD(),
                        unifiedMV.getAccountId());
            } else if (unifiedMV.isOrder()) {
                log.debug("Cached Unified ORDER MV: {} {} order for {} @ ${} = ${} USD (Filled: ${} USD) [{}]", 
                        unifiedMV.isBuyOrder() ? "BUY" : "SELL",
                        unifiedMV.getOrderQuantity().abs(),
                        unifiedMV.getInstrumentName(),
                        unifiedMV.getPrice(),
                        unifiedMV.getMarketValueUSD(),
                        unifiedMV.getFilledMarketValueUSD(),
                        unifiedMV.getOrderStatus());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to cache UnifiedMV for {} {}: {}", 
                     unifiedMV.getRecordType(), unifiedMV.getCacheKey(), e.getMessage());
        }
    }
    
    /**
     * Get all accounts
     */
    public Set<Account> getAllAccounts() {
        return getByKeyPattern(ACCOUNTS_PREFIX + "*", Account.class);
    }
    
    /**
     * Get all instruments
     */
    public Set<Instrument> getAllInstruments() {
        return getByKeyPattern(INSTRUMENTS_PREFIX + "*", Instrument.class);
    }
    
    /**
     * Get latest price for instrument
     */
    public Price getLatestPrice(String instrumentId) {
        return getByKey(PRICES_PREFIX + instrumentId, Price.class);
    }
    
    /**
     * Get all current prices
     */
    public Set<Price> getAllPrices() {
        return getByKeyPattern(PRICES_PREFIX + "*", Price.class);
    }
    
    /**
     * Get holdings for account
     */
    public Set<SODHolding> getHoldingsForAccount(String accountId) {
        return getByKeyPattern(HOLDINGS_PREFIX + accountId + ":*", SODHolding.class);
    }
    
    /**
     * Get recent orders
     */
    public Set<Order> getRecentOrders() {
        return getByKeyPattern(ORDERS_PREFIX + "*", Order.class);
    }
    
    /**
     * Get cash movements for account
     */
    public Set<IntradayCash> getCashMovementsForAccount(String accountId) {
        return getByKeyPattern(CASH_PREFIX + accountId + ":*", IntradayCash.class);
    }
    

    
    /**
     * Get unified market values for account
     */
    public Set<UnifiedMarketValue> getUnifiedMVForAccount(String accountId) {
        // Get all UnifiedMV records and filter by account
        return getAllUnifiedMV().stream()
                .filter(unifiedMV -> accountId.equals(unifiedMV.getAccountId()))
                .collect(Collectors.toSet());
    }
    
    /**
     * Get all unified market values
     */
    public Set<UnifiedMarketValue> getAllUnifiedMV() {
        return getByKeyPattern(UNIFIED_MV_PREFIX + "*", UnifiedMarketValue.class);
    }
    
    /**
     * Get unified holdings (HOLDING records only) for account
     */
    public Set<UnifiedMarketValue> getUnifiedHoldingsForAccount(String accountId) {
        return getUnifiedMVForAccount(accountId).stream()
                .filter(UnifiedMarketValue::isHolding)
                .collect(Collectors.toSet());
    }
    
    /**
     * Get unified orders (ORDER records only) for account
     */
    public Set<UnifiedMarketValue> getUnifiedOrdersForAccount(String accountId) {
        return getUnifiedMVForAccount(accountId).stream()
                .filter(UnifiedMarketValue::isOrder)
                .collect(Collectors.toSet());
    }
    
    /**
     * Get all unified holdings (HOLDING records only)
     */
    public Set<UnifiedMarketValue> getAllUnifiedHoldings() {
        return getAllUnifiedMV().stream()
                .filter(UnifiedMarketValue::isHolding)
                .collect(Collectors.toSet());
    }
    
    /**
     * Get all unified orders (ORDER records only)
     */
    public Set<UnifiedMarketValue> getAllUnifiedOrders() {
        return getAllUnifiedMV().stream()
                .filter(UnifiedMarketValue::isOrder)
                .collect(Collectors.toSet());
    }
    
    /**
     * Generic method to get objects by key pattern
     */
    private <T> Set<T> getByKeyPattern(String pattern, Class<T> clazz) {
        log.debug("Searching for keys with pattern: {}", pattern);
        Set<String> keys = redisTemplate.keys(pattern);
        log.debug("Found {} keys matching pattern {}", keys != null ? keys.size() : 0, pattern);
        
        if (keys == null || keys.isEmpty()) {
            log.debug("No keys found for pattern: {}", pattern);
            return Set.of();
        }
        
        log.debug("Keys found: {}", keys);
        
        Set<T> results = keys.stream()
                .map(key -> getByKey(key, clazz))
                .filter(obj -> obj != null)
                .collect(Collectors.toSet());
        
        log.debug("Successfully deserialized {} objects of type {} from {} keys", 
                results.size(), clazz.getSimpleName(), keys.size());
                
        return results;
    }
    
    /**
     * Generic method to get object by key
     */
    private <T> T getByKey(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                log.debug("Deserializing {} from key {}: {}", clazz.getSimpleName(), key, json);
                T result = objectMapper.readValue(json, clazz);
                log.debug("Successfully deserialized {} from key {}", clazz.getSimpleName(), key);
                return result;
            } else {
                log.debug("No data found for key: {}", key);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize {} from key {}: {}", clazz.getSimpleName(), key, e.getMessage());
            log.error("JSON content was: {}", redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.error("Unexpected error deserializing {} from key {}: {}", clazz.getSimpleName(), key, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        return CacheStats.builder()
                .accountCount(getKeyCount(ACCOUNTS_PREFIX + "*"))
                .instrumentCount(getKeyCount(INSTRUMENTS_PREFIX + "*"))
                .priceCount(getKeyCount(PRICES_PREFIX + "*"))
                .holdingCount(getKeyCount(HOLDINGS_PREFIX + "*"))
                .orderCount(getKeyCount(ORDERS_PREFIX + "*"))
                .cashMovementCount(getKeyCount(CASH_PREFIX + "*"))
                .unifiedMVCount(getKeyCount(UNIFIED_MV_PREFIX + "*"))
                .build();
    }
    
    private long getKeyCount(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? keys.size() : 0;
    }
    
    /**
     * Cache statistics data class
     */
    public static class CacheStats {
        public final long accountCount;
        public final long instrumentCount;
        public final long priceCount;
        public final long holdingCount;
        public final long orderCount;
        public final long cashMovementCount;
        public final long unifiedMVCount;
        
        private CacheStats(long accountCount, long instrumentCount, long priceCount, 
                          long holdingCount, long orderCount, long cashMovementCount,
                          long unifiedMVCount) {
            this.accountCount = accountCount;
            this.instrumentCount = instrumentCount;
            this.priceCount = priceCount;
            this.holdingCount = holdingCount;
            this.orderCount = orderCount;
            this.cashMovementCount = cashMovementCount;
            this.unifiedMVCount = unifiedMVCount;
        }
        
        public static CacheStatsBuilder builder() {
            return new CacheStatsBuilder();
        }
        
        public static class CacheStatsBuilder {
            private long accountCount;
            private long instrumentCount;
            private long priceCount;
            private long holdingCount;
            private long orderCount;
            private long cashMovementCount;
            private long unifiedMVCount;
            
            public CacheStatsBuilder accountCount(long accountCount) {
                this.accountCount = accountCount;
                return this;
            }
            
            public CacheStatsBuilder instrumentCount(long instrumentCount) {
                this.instrumentCount = instrumentCount;
                return this;
            }
            
            public CacheStatsBuilder priceCount(long priceCount) {
                this.priceCount = priceCount;
                return this;
            }
            
            public CacheStatsBuilder holdingCount(long holdingCount) {
                this.holdingCount = holdingCount;
                return this;
            }
            
            public CacheStatsBuilder orderCount(long orderCount) {
                this.orderCount = orderCount;
                return this;
            }
            
            public CacheStatsBuilder cashMovementCount(long cashMovementCount) {
                this.cashMovementCount = cashMovementCount;
                return this;
            }
            

            
            public CacheStatsBuilder unifiedMVCount(long unifiedMVCount) {
                this.unifiedMVCount = unifiedMVCount;
                return this;
            }
            
            public CacheStats build() {
                return new CacheStats(accountCount, instrumentCount, priceCount, 
                                    holdingCount, orderCount, cashMovementCount,
                                    unifiedMVCount);
            }
        }
    }
} 