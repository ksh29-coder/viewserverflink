package com.viewserver.common.kafka;

/**
 * Constants for Kafka topic names used throughout the system.
 * Centralizes topic naming to avoid typos and ensure consistency.
 */
public final class TopicConstants {
    
    private TopicConstants() {
        // Utility class - prevent instantiation
    }
    
    // Base Layer Topics (from Mock Data Generator)
    public static final String BASE_ACCOUNT = "base.account";
    public static final String BASE_INSTRUMENT = "base.instrument";
    public static final String BASE_SOD_HOLDING = "base.sod-holding";
    public static final String BASE_PRICE = "base.price";
    public static final String BASE_INTRADAY_CASH = "base.intraday-cash";
    public static final String BASE_ORDER_EVENTS = "base.order-events";
    
    // Aggregation Layer Topics (from Flink Jobs in View Server)
    public static final String AGGREGATION_ENRICHED_HOLDINGS = "aggregation.enriched-holdings";
    public static final String AGGREGATION_MARKET_VALUES = "aggregation.market-values";
    public static final String AGGREGATION_ACCOUNT_CASH_SUMMARY = "aggregation.account-cash-summary";
    
    // View Layer Topics (Internal to View Server - if needed)
    public static final String VIEW_PORTFOLIO_UPDATES = "view.portfolio-updates";
    public static final String VIEW_CASH_UPDATES = "view.cash-updates";
    public static final String VIEW_PID_CARVE_OUT_UPDATES = "view.pid-carve-out-updates";
    
    // Dead Letter Topics (for error handling)
    public static final String DLT_BASE_DATA = "dlt.base-data";
    public static final String DLT_AGGREGATION = "dlt.aggregation";
    public static final String DLT_COMPUTATION = "dlt.computation";
} 