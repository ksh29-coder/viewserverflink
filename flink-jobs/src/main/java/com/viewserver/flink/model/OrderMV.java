package com.viewserver.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Market Value (OrderMV) model for Flink processing.
 * 
 * This model combines:
 * - Order data (order information and status)
 * - Instrument data (instrument details)
 * - Price data (current pricing)
 * - Calculated market values for order and filled quantities
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderMV {
    
    // ==================== Original Order Fields ====================
    
    private String orderId;
    private String instrumentId;
    private String accountId;
    private LocalDateTime date;
    private BigDecimal orderQuantity;
    private BigDecimal filledQuantity;
    private String orderStatus;
    private BigDecimal orderPrice;
    private String orderType;
    private String venue;
    private LocalDateTime timestamp;
    
    // ==================== Enriched Instrument Fields ====================
    
    private String instrumentName;
    private String instrumentType;
    private String currency;
    private String countryOfRisk;
    private String countryOfDomicile;
    private String sector;
    private List<String> subSectors;
    
    // ==================== Enriched Price Fields ====================
    
    private BigDecimal price;
    private String priceCurrency;
    private String priceSource;
    private LocalDateTime priceTimestamp;
    
    // ==================== Calculated Market Value Fields ====================
    
    /**
     * Market value of the full order quantity in instrument currency
     */
    private BigDecimal orderMarketValueLocal;
    
    /**
     * Market value of the full order quantity in USD
     */
    private BigDecimal orderMarketValueUSD;
    
    /**
     * Market value of the filled quantity in instrument currency
     */
    private BigDecimal filledMarketValueLocal;
    
    /**
     * Market value of the filled quantity in USD
     */
    private BigDecimal filledMarketValueUSD;
    
    /**
     * Timestamp when market values were calculated
     */
    private LocalDateTime calculationTimestamp;
    
    // ==================== Utility Methods ====================
    
    /**
     * Get the Kafka key for this order-mv record
     * Format: {orderId}
     */
    public String getKafkaKey() {
        return orderId;
    }
    
    /**
     * Check if this order has a valid price
     */
    public boolean hasValidPrice() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this order has calculated market values
     */
    public boolean hasMarketValues() {
        return orderMarketValueLocal != null && orderMarketValueUSD != null;
    }
    
    /**
     * Get remaining quantity to be filled
     */
    public BigDecimal getRemainingQuantity() {
        if (orderQuantity == null || filledQuantity == null) {
            return BigDecimal.ZERO;
        }
        return orderQuantity.abs().subtract(filledQuantity);
    }
    
    /**
     * Check if the order is completely filled
     */
    public boolean isCompletelyFilled() {
        if (orderQuantity == null || filledQuantity == null) {
            return false;
        }
        return filledQuantity.compareTo(orderQuantity.abs()) == 0;
    }
    
    /**
     * Check if this is a buy order
     */
    public boolean isBuyOrder() {
        return orderQuantity != null && orderQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a sell order
     */
    public boolean isSellOrder() {
        return orderQuantity != null && orderQuantity.compareTo(BigDecimal.ZERO) < 0;
    }
} 