package com.viewserver.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a financial instrument (equity, bond, currency, etc.).
 * Contains metadata about the instrument including geographic and sector information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {
    
    /**
     * Unique numeric identifier for the instrument
     */
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    /**
     * Human-readable name of the instrument
     * Examples: "Apple Inc", "Microsoft Corp", "USD Cash"
     */
    @JsonProperty("instrumentName")
    private String instrumentName;
    
    /**
     * Type of financial instrument
     * Examples: "EQUITY", "BOND", "CURRENCY", "DERIVATIVE", "FUND"
     */
    @JsonProperty("instrumentType")
    private String instrumentType;
    
    /**
     * Currency in which the instrument is denominated
     * Examples: "USD", "EUR", "GBP", "JPY"
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
     * Examples: "Technology", "Financials", "Healthcare", "Cash"
     */
    @JsonProperty("sector")
    private String sector;
    
    /**
     * Sub-sector classifications for more granular categorization
     * Examples: ["Software", "Cloud Computing"] for a tech stock
     */
    @JsonProperty("subSectors")
    private List<String> subSectors;
    
    /**
     * Timestamp when this instrument record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this instrument
     */
    public String getKafkaKey() {
        return instrumentId;
    }
    
    /**
     * Check if this instrument represents a cash/currency position
     */
    public boolean isCash() {
        return "Cash".equalsIgnoreCase(sector) || "CURRENCY".equalsIgnoreCase(instrumentType);
    }
} 