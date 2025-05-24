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
 * Represents a price update for a financial instrument.
 * This captures both real-time and historical pricing data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Price {
    
    /**
     * The timestamp when this price was observed/recorded
     */
    @JsonProperty("date")
    private LocalDateTime date;
    
    /**
     * The instrument for which this price applies
     */
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    /**
     * The price value (could be market price, NAV, exchange rate, etc.)
     */
    @JsonProperty("price")
    private BigDecimal price;
    
    /**
     * Optional: Currency in which the price is denominated
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Optional: Source of the price (e.g., "BLOOMBERG", "REUTERS", "INTERNAL")
     */
    @JsonProperty("source")
    private String source;
    
    /**
     * Timestamp when this price record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this price
     * Format: {instrumentId}#{date}
     */
    public String getKafkaKey() {
        return KeyBuilder.buildPriceKey(instrumentId, date);
    }
} 