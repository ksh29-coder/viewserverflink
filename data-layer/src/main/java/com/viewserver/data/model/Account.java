package com.viewserver.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a fund account in the financial system.
 * Each account typically represents an equity strategy or fund.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    /**
     * Unique identifier for the account
     */
    @JsonProperty("accountId")
    private String accountId;
    
    /**
     * Human-readable name of the account/strategy
     * Examples: "Global Growth Fund", "US Value Strategy", "Emerging Markets Equity"
     */
    @JsonProperty("accountName")
    private String accountName;
    
    /**
     * Timestamp when this account record was created or last updated
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Get the Kafka key for this account
     */
    public String getKafkaKey() {
        return accountId;
    }
} 