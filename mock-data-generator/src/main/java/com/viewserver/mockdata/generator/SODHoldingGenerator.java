package com.viewserver.mockdata.generator;

import com.viewserver.data.kafka.DataPublisher;
import com.viewserver.data.model.SODHolding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Generates Start-of-Day (SOD) holding positions for all accounts and instruments.
 * Triggered manually on demand rather than on a schedule.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SODHoldingGenerator {
    
    private final DataPublisher dataPublisher;
    private final Random random = new Random();
    
    /**
     * Method to trigger SOD generation manually
     */
    public void generateSODHoldingsNow() {
        log.info("Manually triggering SOD holdings generation");
        generateDailySODHoldings();
    }
    
    /**
     * Generate SOD holdings for the current date
     */
    private void generateDailySODHoldings() {
        LocalDate today = LocalDate.now();
        log.info("Generating SOD holdings for date: {}", today);
        
        List<String> accountIds = StaticDataGenerator.getAccountIds();
        List<String> instrumentIds = StaticDataGenerator.getEquityInstrumentIds();
        
        // Generate holdings for each account-instrument combination
        List<CompletableFuture<Void>> futures = accountIds.stream()
                .flatMap(accountId -> instrumentIds.stream()
                        .map(instrumentId -> generateSODHolding(today, accountId, instrumentId)))
                .toList();
        
        // Wait for all holdings to be published
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully generated {} SOD holdings for {}", 
                                futures.size(), today);
                    } else {
                        log.error("Failed to generate SOD holdings: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Generate a single SOD holding for an account-instrument pair
     */
    private CompletableFuture<Void> generateSODHolding(LocalDate date, String accountId, String instrumentId) {
        SODHolding holding = SODHolding.builder()
                .holdingId(generateHoldingId(date, accountId, instrumentId))
                .date(date)
                .instrumentId(instrumentId)
                .accountId(accountId)
                .position(generateRandomPosition())
                .build();
        
        log.debug("Generating SOD holding: {} - {} shares of {} for account {}", 
                holding.getHoldingId(), holding.getPosition(), instrumentId, accountId);
        
        return dataPublisher.publishSODHolding(holding)
                .thenApply(result -> null); // Convert to Void
    }
    
    /**
     * Generate a deterministic holding ID
     */
    private String generateHoldingId(LocalDate date, String accountId, String instrumentId) {
        return String.format("SOD_%s_%s_%s_%s", 
                date.toString().replace("-", ""),
                accountId,
                instrumentId,
                UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Generate realistic position sizes
     * Mix of zero positions (no holding), small positions, and larger positions
     */
    private BigDecimal generateRandomPosition() {
        // 20% chance of zero position (no holding)
        if (random.nextDouble() < 0.2) {
            return BigDecimal.ZERO;
        }
        
        // 60% chance of small position (1-1000 shares)
        if (random.nextDouble() < 0.6) {
            return BigDecimal.valueOf(random.nextInt(1000) + 1);
        }
        
        // 20% chance of large position (1000-10000 shares)
        return BigDecimal.valueOf(random.nextInt(9000) + 1000);
    }
} 