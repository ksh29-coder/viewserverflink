package com.viewserver.mockdata.generator;

import com.viewserver.data.kafka.DataPublisher;
import com.viewserver.data.model.Order;
import com.viewserver.data.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates realistic trading orders and simulates their lifecycle.
 * Creates new orders periodically and updates existing orders through their states.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderGenerator {
    
    private final DataPublisher dataPublisher;
    private final Random random = new Random();
    
    // Control flag for enabling/disabling generation
    private final AtomicBoolean generationEnabled = new AtomicBoolean(true);
    
    private static final String[] ORDER_TYPES = {"MARKET", "LIMIT", "STOP"};
    private static final String[] VENUES = {"NYSE", "NASDAQ", "LSE", "EURONEXT"};
    
    /**
     * Generate new orders every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void generateNewOrders() {
        if (!generationEnabled.get()) {
            return; // Skip generation if disabled
        }
        
        // Generate 1-3 new orders per cycle
        int numOrders = random.nextInt(3) + 1;
        
        List<CompletableFuture<Void>> futures = range(numOrders)
                .mapToObj(i -> generateNewOrder())
                .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Generated {} new orders", numOrders);
                    } else {
                        log.error("Failed to generate orders: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Generate a single new order
     */
    private CompletableFuture<Void> generateNewOrder() {
        LocalDateTime now = LocalDateTime.now();
        
        // Select random account and instrument
        List<String> accountIds = StaticDataGenerator.getAccountIds();
        List<String> instrumentIds = StaticDataGenerator.getEquityInstrumentIds();
        
        String accountId = accountIds.get(random.nextInt(accountIds.size()));
        String instrumentId = instrumentIds.get(random.nextInt(instrumentIds.size()));
        
        Order order = Order.builder()
                .orderId(generateOrderId())
                .instrumentId(instrumentId)
                .accountId(accountId)
                .date(now)
                .orderQuantity(generateOrderQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .orderStatus(OrderStatus.CREATED)
                .orderPrice(generateOrderPrice())
                .orderType(ORDER_TYPES[random.nextInt(ORDER_TYPES.length)])
                .venue(VENUES[random.nextInt(VENUES.length)])
                .build();
        
        log.debug("Creating new order: {} for {} shares of {} (account: {})", 
                order.getOrderId(), order.getOrderQuantity(), instrumentId, accountId);
        
        return dataPublisher.publishOrder(order)
                .thenApply(result -> null); // Convert to Void
    }
    
    /**
     * Simulate order fills and updates every 10 seconds
     * In a real system, this would be driven by market events
     */
    @Scheduled(fixedRate = 10000) // 10 seconds
    public void simulateOrderUpdates() {
        if (!generationEnabled.get()) {
            return; // Skip generation if disabled
        }
        
        // For demonstration, we'll simulate a few order updates
        // In practice, we'd track active orders and update them
        
        // Generate 0-2 order updates per cycle
        int numUpdates = random.nextInt(3);
        
        for (int i = 0; i < numUpdates; i++) {
            simulateOrderUpdate();
        }
    }
    
    /**
     * Simulate an order update (fill, partial fill, or cancellation)
     */
    private void simulateOrderUpdate() {
        // Create a mock order update for demonstration
        // In practice, we'd maintain a state of active orders
        
        LocalDateTime now = LocalDateTime.now();
        List<String> accountIds = StaticDataGenerator.getAccountIds();
        List<String> instrumentIds = StaticDataGenerator.getEquityInstrumentIds();
        
        String accountId = accountIds.get(random.nextInt(accountIds.size()));
        String instrumentId = instrumentIds.get(random.nextInt(instrumentIds.size()));
        BigDecimal orderQuantity = generateOrderQuantity();
        
        Order updatedOrder = Order.builder()
                .orderId(generateOrderId())
                .instrumentId(instrumentId)
                .accountId(accountId)
                .date(now)
                .orderQuantity(orderQuantity)
                .filledQuantity(generateFilledQuantity(orderQuantity))
                .orderStatus(generateUpdatedOrderStatus())
                .orderPrice(generateOrderPrice())
                .orderType(ORDER_TYPES[random.nextInt(ORDER_TYPES.length)])
                .venue(VENUES[random.nextInt(VENUES.length)])
                .build();
        
        log.debug("Updating order: {} - status: {}, filled: {}/{}", 
                updatedOrder.getOrderId(), updatedOrder.getOrderStatus(),
                updatedOrder.getFilledQuantity(), updatedOrder.getOrderQuantity());
        
        dataPublisher.publishOrder(updatedOrder);
    }
    
    /**
     * Generate a unique order ID
     */
    private String generateOrderId() {
        return "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * Generate realistic order quantities
     */
    private BigDecimal generateOrderQuantity() {
        // Mix of small and large orders
        if (random.nextDouble() < 0.7) {
            // 70% small orders (1-100 shares)
            return BigDecimal.valueOf(random.nextInt(100) + 1);
        } else {
            // 30% larger orders (100-1000 shares)
            return BigDecimal.valueOf(random.nextInt(900) + 100);
        }
    }
    
    /**
     * Generate realistic order prices (simplified)
     */
    private BigDecimal generateOrderPrice() {
        // Generate price between $10 and $500
        double price = 10 + (random.nextDouble() * 490);
        return BigDecimal.valueOf(price).setScale(2, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Generate filled quantity for order updates
     */
    private BigDecimal generateFilledQuantity(BigDecimal orderQuantity) {
        double fillRatio = random.nextDouble();
        
        // 30% no fill, 40% partial fill, 30% complete fill
        if (fillRatio < 0.3) {
            return BigDecimal.ZERO;
        } else if (fillRatio < 0.7) {
            // Partial fill (10-90% of order)
            double partialRatio = 0.1 + (random.nextDouble() * 0.8);
            return orderQuantity.multiply(BigDecimal.valueOf(partialRatio))
                    .setScale(0, BigDecimal.ROUND_DOWN);
        } else {
            // Complete fill
            return orderQuantity;
        }
    }
    
    /**
     * Generate order status for updates
     */
    private OrderStatus generateUpdatedOrderStatus() {
        double statusRandom = random.nextDouble();
        
        if (statusRandom < 0.1) {
            return OrderStatus.CANCELLED;
        } else if (statusRandom < 0.15) {
            return OrderStatus.REJECTED;
        } else if (statusRandom < 0.5) {
            return OrderStatus.PARTIALLY_FILLED;
        } else {
            return OrderStatus.FILLED;
        }
    }
    
    /**
     * Helper method to create a stream of integers
     */
    private java.util.stream.IntStream range(int count) {
        return java.util.stream.IntStream.range(0, count);
    }
    
    /**
     * Manual trigger for order generation (for testing)
     */
    public void generateOrdersNow() {
        log.info("Manually triggering order generation");
        generateNewOrders();
        simulateOrderUpdates();
    }
    
    /**
     * Start order generation
     */
    public void startGeneration() {
        generationEnabled.set(true);
        log.info("Order generation started");
    }
    
    /**
     * Stop order generation
     */
    public void stopGeneration() {
        generationEnabled.set(false);
        log.info("Order generation stopped");
    }
    
    /**
     * Check if order generation is enabled
     */
    public boolean isGenerationEnabled() {
        return generationEnabled.get();
    }
} 