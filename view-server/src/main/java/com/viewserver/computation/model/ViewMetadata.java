package com.viewserver.computation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata for tracking Account Overview view lifecycle.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ViewMetadata {
    
    /**
     * Unique view identifier
     */
    private String viewId;
    
    /**
     * User who created this view
     */
    private String userId;
    
    /**
     * Optional user-friendly view name
     */
    private String viewName;
    
    /**
     * View configuration
     */
    private AccountOverviewRequest configuration;
    
    /**
     * When the view was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When the view was last accessed (WebSocket connection)
     */
    private LocalDateTime lastAccessedAt;
    
    /**
     * Current view status
     */
    private ViewStatus status;
    
    /**
     * Number of active WebSocket connections to this view
     */
    private Integer activeConnections;
    
    /**
     * Total number of rows currently in this view
     */
    private Integer rowCount;
    
    /**
     * Last time the view data was updated
     */
    private LocalDateTime lastDataUpdate;
    
    /**
     * View lifecycle status
     */
    public enum ViewStatus {
        CREATING,       // View is being set up
        ACTIVE,         // View is active and receiving updates
        IDLE,           // View has no active connections but is still maintained
        CLEANUP_PENDING, // View is scheduled for cleanup
        DESTROYED       // View has been destroyed
    }
    
    /**
     * Check if view is active (has connections or recently accessed)
     */
    public boolean isActive() {
        return status == ViewStatus.ACTIVE && 
               (activeConnections != null && activeConnections > 0);
    }
    
    /**
     * Check if view should be cleaned up
     */
    public boolean shouldCleanup(int idleTimeoutMinutes) {
        if (status == ViewStatus.CLEANUP_PENDING || status == ViewStatus.DESTROYED) {
            return true;
        }
        
        if (activeConnections != null && activeConnections > 0) {
            return false; // Still has active connections
        }
        
        if (lastAccessedAt == null) {
            return true; // Never accessed
        }
        
        return lastAccessedAt.isBefore(LocalDateTime.now().minusMinutes(idleTimeoutMinutes));
    }
    
    /**
     * Update last accessed timestamp
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    /**
     * Increment active connection count
     */
    public void addConnection() {
        if (activeConnections == null) {
            activeConnections = 0;
        }
        activeConnections++;
        updateLastAccessed();
        
        if (status == ViewStatus.IDLE) {
            status = ViewStatus.ACTIVE;
        }
    }
    
    /**
     * Decrement active connection count
     */
    public void removeConnection() {
        if (activeConnections != null && activeConnections > 0) {
            activeConnections--;
        }
        
        if (activeConnections == null || activeConnections == 0) {
            status = ViewStatus.IDLE;
        }
    }
} 