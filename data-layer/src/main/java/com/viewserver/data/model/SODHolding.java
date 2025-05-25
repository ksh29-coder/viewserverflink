package com.viewserver.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.viewserver.common.keys.KeyBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a Start-of-Day (SOD) holding position.
 * This captures the position of a specific instrument in a specific account at the start of a trading day.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SODHolding {
    
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
     * Positive for long positions, negative for short positions
     */
    @JsonProperty("position")
    private BigDecimal position;
    
    /**
     * Timestamp when this holding record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this SOD holding
     * Format: {date}#{instrumentId}#{accountId}
     */
    public String getKafkaKey() {
        return KeyBuilder.buildSODHoldingKey(date, instrumentId, accountId);
    }
} 