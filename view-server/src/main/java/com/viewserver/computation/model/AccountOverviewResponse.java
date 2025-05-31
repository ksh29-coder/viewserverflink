package com.viewserver.computation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response model for Account Overview aggregated data.
 * Represents a single row in the dynamic grid.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AccountOverviewResponse {
    
    /**
     * Unique identifier for this view
     */
    private String viewId;
    
    /**
     * Unique row key for grid updates
     * Format: "value1#value2#value3" based on groupBy fields
     */
    private String rowKey;
    
    /**
     * Dynamic grouping field values
     * Key = field name (e.g., "accountName", "instrumentName")
     * Value = field value (e.g., "Global Growth Fund", "Apple Inc")
     */
    private Map<String, String> groupingFields;
    
    /**
     * Start of Day NAV in USD (holdings only)
     * Only present if SOD exposure type is selected
     */
    private BigDecimal sodNavUSD;
    
    /**
     * Current NAV in USD (holdings + filled orders)
     * Only present if CURRENT exposure type is selected
     */
    private BigDecimal currentNavUSD;
    
    /**
     * Expected NAV in USD (holdings + all orders)
     * Only present if EXPECTED exposure type is selected
     */
    private BigDecimal expectedNavUSD;
    
    /**
     * Timestamp of the last update to this row
     */
    private LocalDateTime lastUpdated;
    
    /**
     * Number of underlying records contributing to this aggregation
     */
    private Integer recordCount;
    
    /**
     * Generate row key from grouping fields
     */
    public String generateRowKey() {
        if (groupingFields == null || groupingFields.isEmpty()) {
            return "unknown";
        }
        
        return groupingFields.values()
                .stream()
                .map(value -> value != null ? value : "null")
                .reduce((a, b) -> a + "#" + b)
                .orElse("unknown");
    }
    
    /**
     * Check if this row has any NAV values
     */
    public boolean hasAnyNavValue() {
        return (sodNavUSD != null && sodNavUSD.compareTo(BigDecimal.ZERO) != 0) ||
               (currentNavUSD != null && currentNavUSD.compareTo(BigDecimal.ZERO) != 0) ||
               (expectedNavUSD != null && expectedNavUSD.compareTo(BigDecimal.ZERO) != 0);
    }
    
    /**
     * Get total NAV across all exposure types
     */
    public BigDecimal getTotalNav() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (sodNavUSD != null) {
            total = total.add(sodNavUSD);
        }
        if (currentNavUSD != null) {
            total = total.add(currentNavUSD);
        }
        if (expectedNavUSD != null) {
            total = total.add(expectedNavUSD);
        }
        
        return total;
    }
} 