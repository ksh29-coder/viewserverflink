package com.viewserver.common.keys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for building composite Kafka keys according to our topic key strategy.
 * 
 * Key Formats:
 * - base.account: accountId
 * - base.instrument: instrumentId (numeric)
 * - base.sod-holding: {date}#{instrumentId}#{accountId}
 * - base.price: {instrumentId}#{date}
 * - base.intraday-cash: {date}#{instrumentId}#{accountId}
 * - base.order-events: orderId
 */
public class KeyBuilder {
    
    private static final String DELIMITER = "#";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /**
     * Build key for base.account topic
     */
    public static String buildAccountKey(String accountId) {
        return accountId;
    }
    
    /**
     * Build key for base.instrument topic
     */
    public static String buildInstrumentKey(String instrumentId) {
        return instrumentId;
    }
    
    /**
     * Build key for base.sod-holding topic
     * Format: {date}#{instrumentId}#{accountId}
     */
    public static String buildSODHoldingKey(LocalDate date, String instrumentId, String accountId) {
        return date.format(DATE_FORMATTER) + DELIMITER + instrumentId + DELIMITER + accountId;
    }
    
    /**
     * Build key for base.price topic
     * Format: {instrumentId}#{date}
     */
    public static String buildPriceKey(String instrumentId, LocalDate date) {
        return instrumentId + DELIMITER + date.format(DATE_FORMATTER);
    }
    
    /**
     * Build key for base.price topic with LocalDateTime
     * Format: {instrumentId}#{date}
     */
    public static String buildPriceKey(String instrumentId, LocalDateTime dateTime) {
        return buildPriceKey(instrumentId, dateTime.toLocalDate());
    }
    
    /**
     * Build key for base.intraday-cash topic
     * Format: {date}#{instrumentId}#{accountId}
     */
    public static String buildIntradayCashKey(LocalDate date, String instrumentId, String accountId) {
        return date.format(DATE_FORMATTER) + DELIMITER + instrumentId + DELIMITER + accountId;
    }
    
    /**
     * Build key for base.intraday-cash topic with LocalDateTime
     * Format: {date}#{instrumentId}#{accountId}
     */
    public static String buildIntradayCashKey(LocalDateTime dateTime, String instrumentId, String accountId) {
        return buildIntradayCashKey(dateTime.toLocalDate(), instrumentId, accountId);
    }
    
    /**
     * Build key for base.order-events topic
     */
    public static String buildOrderKey(String orderId) {
        return orderId;
    }
    
    /**
     * Parse composite key components (for debugging/monitoring)
     */
    public static String[] parseCompositeKey(String compositeKey) {
        return compositeKey.split(DELIMITER);
    }
} 