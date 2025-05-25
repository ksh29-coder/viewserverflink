package com.viewserver.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.viewserver.common.keys.KeyBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a trading order and its current state.
 * Tracks the full lifecycle from creation through fills to completion.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    /**
     * Unique identifier for this order
     */
    @JsonProperty("orderId")
    private String orderId;
    
    /**
     * The instrument being traded
     */
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    /**
     * The account placing the order
     */
    @JsonProperty("accountId")
    private String accountId;
    
    /**
     * When the order was created/placed
     */
    @JsonProperty("date")
    private LocalDateTime date;
    
    /**
     * The total quantity being ordered
     * Positive for buy orders, negative for sell orders
     */
    @JsonProperty("orderQuantity")
    private BigDecimal orderQuantity;
    
    /**
     * The quantity that has been filled so far
     * Should always be <= abs(orderQuantity)
     */
    @JsonProperty("filledQuantity")
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;
    
    /**
     * Current status of the order
     */
    @JsonProperty("orderStatus")
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.CREATED;
    
    /**
     * Optional: Order price (for limit orders)
     */
    @JsonProperty("orderPrice")
    private BigDecimal orderPrice;
    
    /**
     * Optional: Order type (e.g., "MARKET", "LIMIT", "STOP")
     */
    @JsonProperty("orderType")
    private String orderType;
    
    /**
     * Optional: Trading venue/exchange
     */
    @JsonProperty("venue")
    private String venue;
    
    /**
     * Timestamp when this order record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this order
     */
    public String getKafkaKey() {
        return KeyBuilder.buildOrderKey(orderId);
    }
    
    /**
     * Calculate the remaining quantity to be filled
     */
    public BigDecimal getRemainingQuantity() {
        return orderQuantity.abs().subtract(filledQuantity);
    }
    
    /**
     * Check if the order is completely filled
     */
    public boolean isCompletelyFilled() {
        return filledQuantity.compareTo(orderQuantity.abs()) == 0;
    }
    
    /**
     * Check if this is a buy order
     */
    public boolean isBuyOrder() {
        return orderQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a sell order
     */
    public boolean isSellOrder() {
        return orderQuantity.compareTo(BigDecimal.ZERO) < 0;
    }
} 