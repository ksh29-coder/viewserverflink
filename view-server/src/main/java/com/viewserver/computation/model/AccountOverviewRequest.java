package com.viewserver.computation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Request model for creating an Account Overview view.
 * Contains all configuration needed to set up dynamic aggregation.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AccountOverviewRequest {
    
    /**
     * Selected account IDs to include in the view
     * Minimum 1 account must be selected
     */
    private Set<String> selectedAccounts;
    
    /**
     * Fields to group by for aggregation
     * First field is always accountName, followed by user selections
     */
    private List<String> groupByFields;
    
    /**
     * Account exposure types to calculate and display
     * SOD = Start of Day, CURRENT = Current positions, EXPECTED = Including pending orders
     */
    private Set<ExposureType> exposureTypes;
    
    /**
     * Optional user ID for tracking view ownership
     */
    private String userId;
    
    /**
     * Optional view name for user reference
     */
    private String viewName;
    
    /**
     * Exposure calculation types
     */
    public enum ExposureType {
        SOD,        // Start of Day NAV (holdings only)
        CURRENT,    // Current NAV (holdings + filled orders)
        EXPECTED    // Expected NAV (holdings + all orders)
    }
    
    /**
     * Validate the request configuration
     */
    public boolean isValid() {
        return selectedAccounts != null && !selectedAccounts.isEmpty() &&
               groupByFields != null && !groupByFields.isEmpty() &&
               exposureTypes != null && !exposureTypes.isEmpty();
    }
    
    /**
     * Get a unique key for this view configuration
     * Used for caching and deduplication
     */
    public String getConfigurationKey() {
        return String.format("accounts:%s|groupBy:%s|exposures:%s",
                String.join(",", selectedAccounts),
                String.join(",", groupByFields),
                exposureTypes.toString());
    }
} 