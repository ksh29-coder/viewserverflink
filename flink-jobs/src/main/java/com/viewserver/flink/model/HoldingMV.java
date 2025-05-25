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
 * Holding Market Value (HoldingMV) model for Flink processing.
 * 
 * This is a simplified version of the HoldingMV model that combines:
 * - SODHolding data (position information)
 * - Instrument data (instrument details)
 * - Price data (current pricing)
 * - Calculated market values
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HoldingMV {
    
    // ==================== Original SODHolding Fields ====================
    
    private String holdingId;
    private LocalDate date;
    private String instrumentId;
    private String accountId;
    private BigDecimal position;
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
    
    private BigDecimal marketValueLocal;
    private BigDecimal marketValueUSD;
    private LocalDateTime calculationTimestamp;
    
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
} 