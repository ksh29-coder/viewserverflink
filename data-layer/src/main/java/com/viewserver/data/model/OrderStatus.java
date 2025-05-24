package com.viewserver.data.model;

/**
 * Enumeration representing the various states of an order in its lifecycle.
 */
public enum OrderStatus {
    
    /**
     * Order has been created but not yet filled
     */
    CREATED,
    
    /**
     * Order has been partially filled (some quantity executed)
     */
    PARTIALLY_FILLED,
    
    /**
     * Order has been completely filled
     */
    FILLED,
    
    /**
     * Order has been cancelled
     */
    CANCELLED,
    
    /**
     * Order has been rejected
     */
    REJECTED
} 