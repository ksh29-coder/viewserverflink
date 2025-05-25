package com.viewserver.mvcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Market Value Calculator Library (mv-calc)
 * Pure Java library for financial market value calculations.
 * No framework dependencies - can be used in any Java application.
 */
public class MarketValueCalculator {
    
    private static final BondCalculator bondCalculator = new BondCalculator();
    private static final CurrencyConverter currencyConverter = new CurrencyConverter();
    
    /**
     * Calculate market value based on instrument type
     * 
     * @param instrumentType The type of instrument (EQUITY, BOND, CURRENCY, FUND)
     * @param price The current price of the instrument
     * @param quantity The holding quantity/position
     * @param instrumentCurrency The currency of the instrument
     * @return Market value in the instrument's local currency
     */
    public static BigDecimal calculateMarketValueLocal(String instrumentType, BigDecimal price, 
                                                     BigDecimal quantity, String instrumentCurrency) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        try {
            return switch (instrumentType.toUpperCase()) {
                case "EQUITY" -> calculateEquityMarketValue(price, quantity);
                case "BOND" -> calculateBondMarketValue(price, quantity);
                case "CURRENCY" -> calculateCurrencyMarketValue(quantity);
                case "FUND" -> calculateFundMarketValue(price, quantity);
                default -> {
                    System.err.println("Unknown instrument type: " + instrumentType + ". Using equity calculation as default.");
                    yield calculateEquityMarketValue(price, quantity);
                }
            };
        } catch (Exception e) {
            System.err.println("Error calculating market value for instrument type " + instrumentType + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Calculate market value in USD
     * 
     * @param marketValueLocal Market value in local currency
     * @param fromCurrency The local currency
     * @return Market value converted to USD
     */
    public static BigDecimal calculateMarketValueUSD(BigDecimal marketValueLocal, String fromCurrency) {
        if (marketValueLocal == null || marketValueLocal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if ("USD".equalsIgnoreCase(fromCurrency)) {
            return marketValueLocal;
        }
        
        return currencyConverter.convertToUSD(marketValueLocal, fromCurrency);
    }
    
    /**
     * Calculate market value with face value (for bonds)
     * 
     * @param instrumentType The type of instrument
     * @param price The current price
     * @param quantity The holding quantity
     * @param faceValue The face value (for bonds)
     * @param instrumentCurrency The currency
     * @return Market value in local currency
     */
    public static BigDecimal calculateMarketValueLocal(String instrumentType, BigDecimal price, 
                                                     BigDecimal quantity, BigDecimal faceValue, 
                                                     String instrumentCurrency) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        try {
            return switch (instrumentType.toUpperCase()) {
                case "EQUITY" -> calculateEquityMarketValue(price, quantity);
                case "BOND" -> bondCalculator.calculateBondMarketValue(price, quantity, faceValue);
                case "CURRENCY" -> calculateCurrencyMarketValue(quantity);
                case "FUND" -> calculateFundMarketValue(price, quantity);
                default -> {
                    System.err.println("Unknown instrument type: " + instrumentType + ". Using equity calculation as default.");
                    yield calculateEquityMarketValue(price, quantity);
                }
            };
        } catch (Exception e) {
            System.err.println("Error calculating market value for instrument type " + instrumentType + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Equity market value calculation: price × quantity
     */
    private static BigDecimal calculateEquityMarketValue(BigDecimal price, BigDecimal quantity) {
        if (price == null) {
            System.err.println("Price is null for equity calculation, returning zero market value");
            return BigDecimal.ZERO;
        }
        
        return price.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Bond market value calculation: uses bond calculator for complex bond math
     */
    private static BigDecimal calculateBondMarketValue(BigDecimal price, BigDecimal quantity) {
        if (price == null) {
            System.err.println("Price is null for bond calculation, returning zero market value");
            return BigDecimal.ZERO;
        }
        
        return bondCalculator.calculateBondMarketValue(price, quantity);
    }
    
    /**
     * Currency market value calculation: just the quantity (no price multiplication)
     */
    private static BigDecimal calculateCurrencyMarketValue(BigDecimal quantity) {
        return quantity.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Fund/ETF market value calculation: price × quantity (same as equity)
     */
    private static BigDecimal calculateFundMarketValue(BigDecimal price, BigDecimal quantity) {
        return calculateEquityMarketValue(price, quantity);
    }
} 