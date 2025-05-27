package com.viewserver.aggregation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified Market Value model for view server.
 * 
 * This model represents both holdings and orders with their market values,
 * ensuring price consistency across both data types by using the same price
 * source and timestamp for calculations.
 * 
 * This matches the output from the UnifiedMarketValueJob Flink processor.
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
    @JsonProperty("recordType")
    private String recordType;
    
    // ==================== Common Fields ====================
    
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    @JsonProperty("accountId")
    private String accountId;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    // ==================== Holding-Specific Fields ====================
    
    @JsonProperty("holdingId")
    private String holdingId;
    
    @JsonProperty("date")
    private LocalDate date;
    
    @JsonProperty("position")
    private BigDecimal position;
    
    // ==================== Order-Specific Fields ====================
    
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("orderQuantity")
    private BigDecimal orderQuantity;
    
    @JsonProperty("filledQuantity")
    private BigDecimal filledQuantity;
    
    @JsonProperty("orderStatus")
    private String orderStatus;
    
    @JsonProperty("orderPrice")
    private BigDecimal orderPrice;
    
    @JsonProperty("orderType")
    private String orderType;
    
    @JsonProperty("venue")
    private String venue;
    
    // ==================== Enriched Instrument Fields ====================
    
    @JsonProperty("instrumentName")
    private String instrumentName;
    
    @JsonProperty("instrumentType")
    private String instrumentType;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("countryOfRisk")
    private String countryOfRisk;
    
    @JsonProperty("countryOfDomicile")
    private String countryOfDomicile;
    
    @JsonProperty("sector")
    private String sector;
    
    @JsonProperty("subSectors")
    private List<String> subSectors;
    
    // ==================== Unified Price Fields ====================
    
    @JsonProperty("price")
    private BigDecimal price;
    
    @JsonProperty("priceCurrency")
    private String priceCurrency;
    
    @JsonProperty("priceSource")
    private String priceSource;
    
    @JsonProperty("priceTimestamp")
    private LocalDateTime priceTimestamp;
    
    // ==================== Calculated Market Value Fields ====================
    
    /**
     * For HOLDING: position * price in local currency
     * For ORDER: orderQuantity * price in local currency
     */
    @JsonProperty("marketValueLocal")
    private BigDecimal marketValueLocal;
    
    /**
     * For HOLDING: position * price in USD
     * For ORDER: orderQuantity * price in USD
     */
    @JsonProperty("marketValueUSD")
    private BigDecimal marketValueUSD;
    
    /**
     * For ORDER only: filledQuantity * price in local currency
     */
    @JsonProperty("filledMarketValueLocal")
    private BigDecimal filledMarketValueLocal;
    
    /**
     * For ORDER only: filledQuantity * price in USD
     */
    @JsonProperty("filledMarketValueUSD")
    private BigDecimal filledMarketValueUSD;
    
    /**
     * Timestamp when market values were calculated
     */
    @JsonProperty("calculationTimestamp")
    private LocalDateTime calculationTimestamp;
    
    // ==================== Utility Methods ====================
    
    /**
     * Get the cache key for this unified record
     */
    public String getCacheKey() {
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
    
    /**
     * Get market value in the specified currency
     * 
     * @param targetCurrency "LOCAL" for instrument currency, "USD" for USD
     * @param valueType "ORDER" for order market value, "FILLED" for filled market value (orders only)
     * @return Market value in the requested currency
     */
    public BigDecimal getMarketValue(String targetCurrency, String valueType) {
        boolean isUSD = "USD".equalsIgnoreCase(targetCurrency);
        boolean isOrder = "ORDER".equalsIgnoreCase(valueType);
        
        if (isOrder() && !isOrder) {
            // For orders, "FILLED" value type
            return isUSD ? filledMarketValueUSD : filledMarketValueLocal;
        } else {
            // For holdings or order total value
            return isUSD ? marketValueUSD : marketValueLocal;
        }
    }
} 