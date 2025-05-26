package com.viewserver.aggregation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Market Value (order-mv) - Enriched order with market value calculations.
 * Combines Order + Instrument + Price data with calculated market values.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderMV {
    
    // ==================== Original Order Fields ====================
    
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
     */
    @JsonProperty("orderQuantity")
    private BigDecimal orderQuantity;
    
    /**
     * The quantity that has been filled so far
     */
    @JsonProperty("filledQuantity")
    private BigDecimal filledQuantity;
    
    /**
     * Current status of the order
     */
    @JsonProperty("orderStatus")
    private String orderStatus;
    
    /**
     * Order price (for limit orders)
     */
    @JsonProperty("orderPrice")
    private BigDecimal orderPrice;
    
    /**
     * Order type (e.g., "MARKET", "LIMIT", "STOP")
     */
    @JsonProperty("orderType")
    private String orderType;
    
    /**
     * Trading venue/exchange
     */
    @JsonProperty("venue")
    private String venue;
    
    /**
     * Timestamp when this order record was created or last updated
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    // ==================== Enriched Instrument Fields ====================
    
    /**
     * Human-readable name of the instrument
     */
    @JsonProperty("instrumentName")
    private String instrumentName;
    
    /**
     * Type of financial instrument
     */
    @JsonProperty("instrumentType")
    private String instrumentType;
    
    /**
     * Currency in which the instrument is denominated
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Country where the primary investment risk is located
     */
    @JsonProperty("countryOfRisk")
    private String countryOfRisk;
    
    /**
     * Country where the instrument is domiciled/headquartered
     */
    @JsonProperty("countryOfDomicile")
    private String countryOfDomicile;
    
    /**
     * Primary sector classification
     */
    @JsonProperty("sector")
    private String sector;
    
    /**
     * Sub-sector classifications
     */
    @JsonProperty("subSectors")
    private List<String> subSectors;
    
    // ==================== Enriched Price Fields ====================
    
    /**
     * Current price of the instrument
     */
    @JsonProperty("price")
    private BigDecimal price;
    
    /**
     * Currency in which the price is denominated
     */
    @JsonProperty("priceCurrency")
    private String priceCurrency;
    
    /**
     * Source of the price
     */
    @JsonProperty("priceSource")
    private String priceSource;
    
    /**
     * Timestamp when the price was recorded
     */
    @JsonProperty("priceTimestamp")
    private LocalDateTime priceTimestamp;
    
    // ==================== Calculated Market Value Fields ====================
    
    /**
     * Market value of the full order quantity in instrument currency
     */
    @JsonProperty("orderMarketValueLocal")
    private BigDecimal orderMarketValueLocal;
    
    /**
     * Market value of the full order quantity in USD
     */
    @JsonProperty("orderMarketValueUSD")
    private BigDecimal orderMarketValueUSD;
    
    /**
     * Market value of the filled quantity in instrument currency
     */
    @JsonProperty("filledMarketValueLocal")
    private BigDecimal filledMarketValueLocal;
    
    /**
     * Market value of the filled quantity in USD
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
    
    /**
     * Get market value in the specified currency
     * 
     * @param targetCurrency "LOCAL" for instrument currency, "USD" for USD
     * @param valueType "ORDER" for order market value, "FILLED" for filled market value
     * @return Market value in the requested currency
     */
    public BigDecimal getMarketValue(String targetCurrency, String valueType) {
        boolean isUSD = "USD".equalsIgnoreCase(targetCurrency);
        boolean isOrder = "ORDER".equalsIgnoreCase(valueType);
        
        if (isOrder) {
            return isUSD ? orderMarketValueUSD : orderMarketValueLocal;
        } else {
            return isUSD ? filledMarketValueUSD : filledMarketValueLocal;
        }
    }
} 