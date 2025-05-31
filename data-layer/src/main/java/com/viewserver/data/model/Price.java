package com.viewserver.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Price {
    
    /**
     * The date when this price was recorded
     */
    @JsonProperty("date")
    private LocalDateTime date;

    /**
     * The unique identifier for the financial instrument
     */
    @JsonProperty("instrumentId")
    private String instrumentId;

    /**
     * The price value
     */
    @JsonProperty("price")
    private BigDecimal price;

    /**
     * The currency in which the price is denominated
     */
    @JsonProperty("currency")
    private String currency;

    /**
     * The source of the price data (e.g., exchange, vendor)
     */
    @JsonProperty("source")
    private String source;

    /**
     * The timestamp when this price was generated/received
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * Get the Kafka key for this price
     */
    @JsonIgnore
    public String getKafkaKey() {
        return KeyBuilder.buildPriceKey(instrumentId, date);
    }
} 