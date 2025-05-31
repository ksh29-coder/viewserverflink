package com.viewserver.computation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configuration for a specific Account Overview view.
 * Contains the view ID, request parameters, and creation metadata.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ViewConfiguration {
    
    /**
     * Unique view identifier
     */
    private String viewId;
    
    /**
     * The original request configuration
     */
    private AccountOverviewRequest request;
    
    /**
     * When this view configuration was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When this view was last accessed
     */
    private LocalDateTime lastAccessedAt;
    
    /**
     * Check if this view configuration is valid
     */
    public boolean isValid() {
        return viewId != null && request != null && request.isValid();
    }
    
    /**
     * Update last accessed timestamp
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }
} 