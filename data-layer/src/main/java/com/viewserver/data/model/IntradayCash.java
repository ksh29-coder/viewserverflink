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
 * Represents an intraday cash movement or position.
 * Cash is treated as an instrument (e.g., USD, GBP, EUR) in our system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayCash {
    
    /**
     * The timestamp when this cash movement occurred
     */
    @JsonProperty("date")
    private LocalDateTime date;
    
    /**
     * The currency/cash instrument ID (e.g., "USD", "GBP", "EUR")
     * This maps to an instrument record where sector = "Cash"
     */
    @JsonProperty("instrumentId")
    private String instrumentId;
    
    /**
     * The account for which this cash movement applies
     */
    @JsonProperty("accountId")
    private String accountId;
    
    /**
     * The cash amount/quantity
     * Positive for cash inflows, negative for cash outflows
     */
    @JsonProperty("quantity")
    private BigDecimal quantity;
    
    /**
     * Optional: Description of the cash movement
     * Examples: "DIVIDEND", "TRADE_SETTLEMENT", "SUBSCRIPTION", "REDEMPTION"
     */
    @JsonProperty("movementType")
    private String movementType;
    
    /**
     * Optional: Reference to related transaction/order
     */
    @JsonProperty("referenceId")
    private String referenceId;
    
    /**
     * Timestamp when this cash record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this intraday cash movement
     * Format: {date}#{instrumentId}#{accountId}
     */
    public String getKafkaKey() {
        return KeyBuilder.buildIntradayCashKey(date, instrumentId, accountId);
    }
} 