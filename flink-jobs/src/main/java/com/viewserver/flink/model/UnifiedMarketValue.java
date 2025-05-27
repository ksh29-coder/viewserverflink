package com.viewserver.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified Market Value model for Flink processing.
 * 
 * This model can represent both holdings and orders with their market values,
 * ensuring price consistency across both data types by using the same price
 * source and timestamp for calculations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedMarketValue {
    
    // ==================== Record Type ====================
    
    /**
     * Type of record: HOLDING or ORDER
     */
    private String recordType;
    
    // ==================== Common Fields ====================
    
    private String instrumentId;
    private String accountId;
    private LocalDateTime timestamp;
    
    // ==================== Holding-Specific Fields ====================
    
    private String holdingId;
    private LocalDate date;
    private BigDecimal position;
    
    // ==================== Order-Specific Fields ====================
    
    private String orderId;
    private BigDecimal orderQuantity;
    private BigDecimal filledQuantity;
    private String orderStatus;
    private BigDecimal orderPrice;
    private String orderType;
    private String venue;
    
    // ==================== Enriched Instrument Fields ====================
    
    private String instrumentName;
    private String instrumentType;
    private String currency;
    private String countryOfRisk;
    private String countryOfDomicile;
    private String sector;
    private List<String> subSectors;
    
    // ==================== Unified Price Fields ====================
    
    private BigDecimal price;
    private String priceCurrency;
    private String priceSource;
    private LocalDateTime priceTimestamp;
    
    // ==================== Calculated Market Value Fields ====================
    
    /**
     * For HOLDING: position * price in local currency
     * For ORDER: orderQuantity * price in local currency
     */
    private BigDecimal marketValueLocal;
    
    /**
     * For HOLDING: position * price in USD
     * For ORDER: orderQuantity * price in USD
     */
    private BigDecimal marketValueUSD;
    
    /**
     * For ORDER only: filledQuantity * price in local currency
     */
    private BigDecimal filledMarketValueLocal;
    
    /**
     * For ORDER only: filledQuantity * price in USD
     */
    private BigDecimal filledMarketValueUSD;
    
    /**
     * Timestamp when market values were calculated
     */
    private LocalDateTime calculationTimestamp;
    
    // ==================== Utility Methods ====================
    
    /**
     * Get the Kafka key for this unified record
     */
    public String getKafkaKey() {
        if ("HOLDING".equals(recordType)) {
            return String.format("HOLDING#%s#%s#%s", date, instrumentId, accountId);
        } else if ("ORDER".equals(recordType)) {
            return String.format("ORDER#%s", orderId);
        }
        return "UNKNOWN#" + instrumentId + "#" + accountId;
    }
    
    /**
     * Check if this record has a valid price
     */
    public boolean hasValidPrice() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this record has calculated market values
     */
    public boolean hasMarketValues() {
        return marketValueLocal != null && marketValueUSD != null;
    }
    
    /**
     * Check if this is a holding record
     */
    public boolean isHolding() {
        return "HOLDING".equals(recordType);
    }
    
    /**
     * Check if this is an order record
     */
    public boolean isOrder() {
        return "ORDER".equals(recordType);
    }
    
    /**
     * Get the quantity for market value calculation
     * For holdings: position
     * For orders: orderQuantity
     */
    public BigDecimal getQuantityForCalculation() {
        if (isHolding()) {
            return position != null ? position : BigDecimal.ZERO;
        } else if (isOrder()) {
            return orderQuantity != null ? orderQuantity : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Get remaining quantity for orders
     */
    public BigDecimal getRemainingQuantity() {
        if (!isOrder() || orderQuantity == null || filledQuantity == null) {
            return BigDecimal.ZERO;
        }
        return orderQuantity.abs().subtract(filledQuantity);
    }
    
    /**
     * Check if this is a buy order
     */
    public boolean isBuyOrder() {
        return isOrder() && orderQuantity != null && orderQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a sell order
     */
    public boolean isSellOrder() {
        return isOrder() && orderQuantity != null && orderQuantity.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Check if this is a cash position
     */
    public boolean isCashPosition() {
        return "Cash".equalsIgnoreCase(sector) || "CURRENCY".equalsIgnoreCase(instrumentType);
    }
    

} 