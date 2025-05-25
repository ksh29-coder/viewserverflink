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
 * Holding Market Value (holding-mv) - Enriched holding with market value calculations.
 * Combines SODHolding + Instrument + Price data with calculated market values.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HoldingMV {
    
    // ==================== Original SODHolding Fields ====================
    
    /**
     * Unique identifier for this holding record
     */
    @JsonProperty("holdingId")
    private String holdingId;
    
    /**
     * The date for which this holding position is valid
     */
    @JsonProperty("date")
    private LocalDate date;
    
    /**
     * The instrument being held
     */
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    /**
     * The account that holds this position
     */
    @JsonProperty("accountId")
    private String accountId;
    
    /**
     * The position size (number of shares/units/nominal amount)
     */
    @JsonProperty("position")
    private BigDecimal position;
    
    /**
     * Timestamp when this holding record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
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
    
    // ==================== NEW: Calculated Market Value Fields ====================
    
    /**
     * Market value in the instrument's local currency
     * Calculated using mv-calc library based on instrument type
     */
    @JsonProperty("marketValueLocal")
    private BigDecimal marketValueLocal;
    
    /**
     * Market value converted to USD
     * Always in USD regardless of instrument currency
     */
    @JsonProperty("marketValueUSD")
    private BigDecimal marketValueUSD;
    
    /**
     * Timestamp when market values were calculated
     */
    @JsonProperty("calculationTimestamp")
    @Builder.Default
    private LocalDateTime calculationTimestamp = LocalDateTime.now();
    
    // ==================== Utility Methods ====================
    
    /**
     * Get the Kafka key for this holding-mv record
     * Format: {date}#{instrumentId}#{accountId}
     */
    public String getKafkaKey() {
        return String.format("%s#%s#%s", date, instrumentId, accountId);
    }
    
    /**
     * Check if this holding has a valid price
     */
    public boolean hasValidPrice() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this holding has calculated market values
     */
    public boolean hasMarketValues() {
        return marketValueLocal != null && marketValueUSD != null;
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
     * @return Market value in the requested currency
     */
    public BigDecimal getMarketValue(String targetCurrency) {
        if ("USD".equalsIgnoreCase(targetCurrency)) {
            return marketValueUSD;
        } else if ("LOCAL".equalsIgnoreCase(targetCurrency)) {
            return marketValueLocal;
        } else {
            // Default to USD
            return marketValueUSD;
        }
    }
} 