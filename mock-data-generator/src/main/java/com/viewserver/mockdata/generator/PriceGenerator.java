package com.viewserver.mockdata.generator;

import com.viewserver.data.kafka.DataPublisher;
import com.viewserver.data.model.Price;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates realistic price updates for financial instruments throughout the trading day.
 * Uses random walk model to simulate price movements.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceGenerator {
    
    private final DataPublisher dataPublisher;
    private final Random random = new Random();
    
    // Control flag for enabling/disabling generation
    private final AtomicBoolean generationEnabled = new AtomicBoolean(true);
    
    // Current prices for each instrument (simulated market state)
    private final Map<String, BigDecimal> currentPrices = new HashMap<>();
    
    // Initial base prices for instruments
    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "AAPL", new BigDecimal("180.50"),
            "MSFT", new BigDecimal("415.75"),
            "GOOGL", new BigDecimal("140.25"),
            "JPM", new BigDecimal("165.30"),
            "BAC", new BigDecimal("32.80"),
            "NESN", new BigDecimal("105.60"),
            "ASML", new BigDecimal("720.40")
    );
    
    /**
     * Initialize prices when the service starts
     */
    public void initializePrices() {
        currentPrices.putAll(BASE_PRICES);
        log.info("Initialized prices for {} instruments", currentPrices.size());
    }
    
    /**
     * Generate price updates every 5 seconds during trading hours
     * In production, this would be more sophisticated with market hours
     */
    @Scheduled(fixedRate = 5000) // 5 seconds
    public void generatePriceUpdates() {
        if (!generationEnabled.get()) {
            return; // Skip generation if disabled
        }
        
        if (currentPrices.isEmpty()) {
            initializePrices();
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<String> instrumentIds = StaticDataGenerator.getEquityInstrumentIds();
        
        // Generate price updates for all equity instruments
        List<CompletableFuture<Void>> futures = instrumentIds.stream()
                .map(instrumentId -> generatePriceUpdate(instrumentId, now))
                .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to generate price updates: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Generate a price update for a specific instrument
     */
    private CompletableFuture<Void> generatePriceUpdate(String instrumentId, LocalDateTime timestamp) {
        BigDecimal newPrice = calculateNewPrice(instrumentId);
        
        Price price = Price.builder()
                .date(timestamp)
                .instrumentId(instrumentId)
                .price(newPrice)
                .currency("USD") // Simplified - all prices in USD
                .source("MOCK_EXCHANGE")
                .build();
        
        log.debug("Generated price update: {} = ${}", instrumentId, newPrice);
        
        return dataPublisher.publishPrice(price)
                .thenApply(result -> null); // Convert to Void
    }
    
    /**
     * Calculate new price using random walk model
     * Price changes are typically small with occasional larger moves
     */
    private BigDecimal calculateNewPrice(String instrumentId) {
        BigDecimal currentPrice = currentPrices.get(instrumentId);
        if (currentPrice == null) {
            currentPrice = BASE_PRICES.get(instrumentId);
            if (currentPrice == null) {
                currentPrice = new BigDecimal("100.00"); // Default price
            }
        }
        
        // Random walk with small bias towards mean reversion
        double changePercent = generatePriceChangePercent();
        BigDecimal changeAmount = currentPrice
                .multiply(BigDecimal.valueOf(changePercent))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        
        BigDecimal newPrice = currentPrice.add(changeAmount);
        
        // Ensure price doesn't go negative
        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            newPrice = currentPrice.multiply(BigDecimal.valueOf(0.99));
        }
        
        // Round to 2 decimal places
        newPrice = newPrice.setScale(2, RoundingMode.HALF_UP);
        
        // Update current price state
        currentPrices.put(instrumentId, newPrice);
        
        return newPrice;
    }
    
    /**
     * Generate realistic price change percentages
     * Most changes are small (-0.5% to +0.5%), with occasional larger moves
     */
    private double generatePriceChangePercent() {
        double randomValue = random.nextGaussian(); // Normal distribution
        
        // 95% of moves are within ±1%
        if (Math.abs(randomValue) <= 2.0) {
            return randomValue * 0.5; // Scale to ±1%
        }
        
        // 5% of moves are larger (±1% to ±3%)
        return Math.signum(randomValue) * (1.0 + random.nextDouble() * 2.0);
    }
    
    /**
     * Get current price for an instrument (for testing)
     */
    public BigDecimal getCurrentPrice(String instrumentId) {
        return currentPrices.get(instrumentId);
    }
    
    /**
     * Manual trigger for price generation (for testing)
     */
    public void generatePricesNow() {
        log.info("Manually triggering price generation");
        generatePriceUpdates();
    }
    
    /**
     * Start price generation
     */
    public void startGeneration() {
        generationEnabled.set(true);
        log.info("Price generation started");
    }
    
    /**
     * Stop price generation
     */
    public void stopGeneration() {
        generationEnabled.set(false);
        log.info("Price generation stopped");
    }
    
    /**
     * Check if price generation is enabled
     */
    public boolean isGenerationEnabled() {
        return generationEnabled.get();
    }
} 