package com.viewserver.mockdata.generator;

import com.viewserver.data.kafka.DataPublisher;
import com.viewserver.data.model.IntradayCash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates realistic intraday cash movements including dividends, 
 * trade settlements, subscriptions, and redemptions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntradayCashGenerator {
    
    private final DataPublisher dataPublisher;
    private final Random random = new Random();
    
    // Control flag for enabling/disabling generation
    private final AtomicBoolean generationEnabled = new AtomicBoolean(true);
    
    private static final String[] MOVEMENT_TYPES = {
            "DIVIDEND", "TRADE_SETTLEMENT", "SUBSCRIPTION", 
            "REDEMPTION", "FEE", "INTEREST"
    };
    
    /**
     * Generate cash movements every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void generateCashMovements() {
        if (!generationEnabled.get()) {
            return; // Skip generation if disabled
        }
        
        // Generate 0-3 cash movements per cycle
        int numMovements = random.nextInt(4);
        
        if (numMovements == 0) {
            return; // No movements this cycle
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        List<CompletableFuture<Void>> futures = java.util.stream.IntStream.range(0, numMovements)
                .mapToObj(i -> generateCashMovement(now))
                .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Generated {} cash movements", numMovements);
                    } else {
                        log.error("Failed to generate cash movements: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Generate a single cash movement
     */
    private CompletableFuture<Void> generateCashMovement(LocalDateTime timestamp) {
        // Select random account and cash instrument
        List<String> accountIds = StaticDataGenerator.getAccountIds();
        List<String> cashInstrumentIds = StaticDataGenerator.getCashInstrumentIds();
        
        String accountId = accountIds.get(random.nextInt(accountIds.size()));
        String cashInstrumentId = cashInstrumentIds.get(random.nextInt(cashInstrumentIds.size()));
        String movementType = MOVEMENT_TYPES[random.nextInt(MOVEMENT_TYPES.length)];
        
        IntradayCash cashMovement = IntradayCash.builder()
                .date(timestamp)
                .instrumentId(cashInstrumentId)
                .accountId(accountId)
                .quantity(generateCashAmount(movementType))
                .movementType(movementType)
                .referenceId(generateReferenceId(movementType))
                .build();
        
        log.debug("Generated cash movement: {} {} in {} for account {} ({})", 
                cashMovement.getQuantity(), cashInstrumentId, movementType, accountId,
                cashMovement.getReferenceId());
        
        return dataPublisher.publishIntradayCash(cashMovement)
                .thenApply(result -> null); // Convert to Void
    }
    
    /**
     * Generate realistic cash amounts based on movement type
     */
    private BigDecimal generateCashAmount(String movementType) {
        BigDecimal amount;
        
        switch (movementType) {
            case "DIVIDEND":
                // Dividends are typically small positive amounts
                amount = BigDecimal.valueOf(50 + random.nextDouble() * 500);
                break;
                
            case "TRADE_SETTLEMENT":
                // Trade settlements can be positive (sales) or negative (purchases)
                double tradeAmount = 1000 + random.nextDouble() * 50000;
                amount = BigDecimal.valueOf(random.nextBoolean() ? tradeAmount : -tradeAmount);
                break;
                
            case "SUBSCRIPTION":
                // Subscriptions are large positive cash inflows
                amount = BigDecimal.valueOf(10000 + random.nextDouble() * 100000);
                break;
                
            case "REDEMPTION":
                // Redemptions are large negative cash outflows
                amount = BigDecimal.valueOf(-10000 - random.nextDouble() * 100000);
                break;
                
            case "FEE":
                // Fees are small negative amounts
                amount = BigDecimal.valueOf(-10 - random.nextDouble() * 200);
                break;
                
            case "INTEREST":
                // Interest is small positive amount
                amount = BigDecimal.valueOf(5 + random.nextDouble() * 100);
                break;
                
            default:
                // Default to small random amount
                amount = BigDecimal.valueOf(-100 + random.nextDouble() * 200);
                break;
        }
        
        return amount.setScale(2, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Generate reference IDs based on movement type
     */
    private String generateReferenceId(String movementType) {
        String prefix = switch (movementType) {
            case "DIVIDEND" -> "DIV_";
            case "TRADE_SETTLEMENT" -> "TRD_";
            case "SUBSCRIPTION" -> "SUB_";
            case "REDEMPTION" -> "RED_";
            case "FEE" -> "FEE_";
            case "INTEREST" -> "INT_";
            default -> "REF_";
        };
        
        return prefix + System.currentTimeMillis() + "_" + random.nextInt(1000);
    }
    
    /**
     * Generate dividend payments for all accounts (quarterly simulation)
     */
    public void generateDividendPayments() {
        log.info("Generating quarterly dividend payments");
        
        LocalDateTime now = LocalDateTime.now();
        List<String> accountIds = StaticDataGenerator.getAccountIds();
        List<String> equityInstrumentIds = StaticDataGenerator.getEquityInstrumentIds();
        
        // Generate dividend for each account for each equity holding
        List<CompletableFuture<Void>> futures = accountIds.stream()
                .flatMap(accountId -> equityInstrumentIds.stream()
                        .filter(instrumentId -> random.nextDouble() < 0.3) // 30% chance of dividend
                        .map(instrumentId -> {
                            String cashInstrumentId = "USD"; // Simplified - dividends in USD
                            
                            IntradayCash dividend = IntradayCash.builder()
                                    .date(now)
                                    .instrumentId(cashInstrumentId)
                                    .accountId(accountId)
                                    .quantity(BigDecimal.valueOf(100 + random.nextDouble() * 1000))
                                    .movementType("DIVIDEND")
                                    .referenceId("DIV_" + instrumentId + "_" + System.currentTimeMillis())
                                    .build();
                            
                            return dataPublisher.publishIntradayCash(dividend)
                                    .thenApply(result -> (Void) null);
                        }))
                .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Generated {} dividend payments", futures.size());
                    } else {
                        log.error("Failed to generate dividend payments: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Manual trigger for cash generation (for testing)
     */
    public void generateCashMovementsNow() {
        log.info("Manually triggering cash movement generation");
        generateCashMovements();
    }
    
    /**
     * Start cash movement generation
     */
    public void startGeneration() {
        generationEnabled.set(true);
        log.info("Cash movement generation started");
    }
    
    /**
     * Stop cash movement generation
     */
    public void stopGeneration() {
        generationEnabled.set(false);
        log.info("Cash movement generation stopped");
    }
    
    /**
     * Check if cash movement generation is enabled
     */
    public boolean isGenerationEnabled() {
        return generationEnabled.get();
    }
} 