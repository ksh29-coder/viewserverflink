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
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Generates realistic trading orders and simulates their lifecycle.
 * Creates new orders periodically and updates existing orders through their states.
 * Orders progress through: CREATED -> PARTIALLY_FILLED (with incremental fills) -> FILLED
 * Some orders may be CANCELLED or REJECTED.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderGenerator {
    
    private final DataPublisher dataPublisher;
    private final Random random = new Random();
    
    // Control flag for enabling/disabling generation
    private final AtomicBoolean generationEnabled = new AtomicBoolean(true);
    
    // Track active orders that can be updated
    private final Map<String, Order> activeOrders = new ConcurrentHashMap<>();
    
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
        
        List<CompletableFuture<Void>> futures = java.util.stream.IntStream.range(0, numOrders)
                .mapToObj(i -> generateNewOrder())
                .collect(Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Generated {} new orders. Active orders: {}", numOrders, activeOrders.size());
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
                .timestamp(now)
                .build();
        
        // Add to active orders for future updates
        activeOrders.put(order.getOrderId(), order);
        
        log.debug("Creating new order: {} for {} shares of {} (account: {})", 
                order.getOrderId(), order.getOrderQuantity(), instrumentId, accountId);
        
        return dataPublisher.publishOrder(order)
                .thenApply(result -> null); // Convert to Void
    }
    
    /**
     * Simulate order fills and updates every 10 seconds
     * Updates existing orders with incremental fills
     */
    @Scheduled(fixedRate = 10000) // 10 seconds
    public void simulateOrderUpdates() {
        if (!generationEnabled.get()) {
            return; // Skip generation if disabled
        }
        
        // Get list of orders that can be updated (not FILLED, CANCELLED, or REJECTED)
        List<Order> updatableOrders = activeOrders.values().stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.CREATED || 
                               order.getOrderStatus() == OrderStatus.PARTIALLY_FILLED)
                .collect(Collectors.toList());
        
        if (updatableOrders.isEmpty()) {
            log.debug("No active orders to update");
            return;
        }
        
        // Update 1-3 random orders (or all if fewer than 3)
        int numUpdates = Math.min(random.nextInt(3) + 1, updatableOrders.size());
        
        for (int i = 0; i < numUpdates; i++) {
            Order orderToUpdate = updatableOrders.get(random.nextInt(updatableOrders.size()));
            simulateOrderUpdate(orderToUpdate);
        }
    }
    
    /**
     * Simulate an order update (incremental fill, completion, or cancellation)
     */
    private void simulateOrderUpdate(Order existingOrder) {
        LocalDateTime now = LocalDateTime.now();
        
        // Create updated order with same ID but new state
        Order.OrderBuilder updatedOrderBuilder = existingOrder.toBuilder()
                .timestamp(now);
        
        // Determine what type of update to make
        double updateType = random.nextDouble();
        
        if (updateType < 0.05) {
            // 5% chance to cancel the order
            updatedOrderBuilder.orderStatus(OrderStatus.CANCELLED);
            log.debug("Cancelling order: {}", existingOrder.getOrderId());
        } else if (updateType < 0.08) {
            // 3% chance to reject the order
            updatedOrderBuilder.orderStatus(OrderStatus.REJECTED);
            log.debug("Rejecting order: {}", existingOrder.getOrderId());
        } else {
            // 92% chance for a fill update
            BigDecimal currentFilled = existingOrder.getFilledQuantity();
            BigDecimal orderQuantity = existingOrder.getOrderQuantity().abs();
            BigDecimal remaining = orderQuantity.subtract(currentFilled);
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // Increment filled quantity by 1 (or remaining if less than 1)
                BigDecimal fillIncrement = remaining.compareTo(BigDecimal.ONE) >= 0 ? 
                    BigDecimal.ONE : remaining;
                BigDecimal newFilledQuantity = currentFilled.add(fillIncrement);
                
                updatedOrderBuilder.filledQuantity(newFilledQuantity);
                
                // Check if order is now completely filled
                if (newFilledQuantity.compareTo(orderQuantity) == 0) {
                    updatedOrderBuilder.orderStatus(OrderStatus.FILLED);
                    log.debug("Completing order: {} - filled: {}/{}", 
                            existingOrder.getOrderId(), newFilledQuantity, orderQuantity);
                } else {
                    updatedOrderBuilder.orderStatus(OrderStatus.PARTIALLY_FILLED);
                    log.debug("Partially filling order: {} - filled: {}/{}", 
                            existingOrder.getOrderId(), newFilledQuantity, orderQuantity);
                }
            }
        }
        
        Order updatedOrder = updatedOrderBuilder.build();
        
        // Update the active orders map
        if (updatedOrder.getOrderStatus() == OrderStatus.FILLED ||
            updatedOrder.getOrderStatus() == OrderStatus.CANCELLED ||
            updatedOrder.getOrderStatus() == OrderStatus.REJECTED) {
            // Remove from active orders as it's now terminal
            activeOrders.remove(updatedOrder.getOrderId());
            log.debug("Order {} reached terminal state: {}. Active orders: {}", 
                    updatedOrder.getOrderId(), updatedOrder.getOrderStatus(), activeOrders.size());
        } else {
            // Update the order in active orders
            activeOrders.put(updatedOrder.getOrderId(), updatedOrder);
        }
        
        // Publish the updated order
        dataPublisher.publishOrder(updatedOrder);
    }
    
    /**
     * Generate a unique order ID
     */
    private String generateOrderId() {
        return "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * Generate realistic order quantities (smaller quantities for more fills)
     */
    private BigDecimal generateOrderQuantity() {
        // Favor smaller orders so we can see more fill progression
        if (random.nextDouble() < 0.8) {
            // 80% small orders (5-25 shares) - more fills to observe
            return BigDecimal.valueOf(random.nextInt(21) + 5);
        } else {
            // 20% larger orders (25-100 shares)
            return BigDecimal.valueOf(random.nextInt(76) + 25);
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
    
    /**
     * Get current active orders count (for monitoring)
     */
    public int getActiveOrdersCount() {
        return activeOrders.size();
    }
    
    /**
     * Clear all active orders (for testing/reset)
     */
    public void clearActiveOrders() {
        activeOrders.clear();
        log.info("Cleared all active orders");
    }
} 