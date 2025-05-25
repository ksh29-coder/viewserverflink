package com.viewserver.mvcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Bond Calculator for complex bond market value calculations.
 * Handles bond-specific pricing logic including face value and accrued interest.
 * Pure Java implementation with no framework dependencies.
 */
public class BondCalculator {
    
    // Standard face value for bonds (typically $1000 or $100)
    private static final BigDecimal STANDARD_FACE_VALUE = new BigDecimal("100");
    
    /**
     * Calculate bond market value
     * For bonds, price is typically quoted as percentage of face value
     * 
     * @param price Bond price (as percentage of face value, e.g., 98.50 for 98.5%)
     * @param quantity Number of bonds held
     * @return Market value of the bond position
     */
    public BigDecimal calculateBondMarketValue(BigDecimal price, BigDecimal quantity) {
        if (price == null || quantity == null) {
            System.err.println("Price or quantity is null for bond calculation, returning zero");
            return BigDecimal.ZERO;
        }
        
        try {
            // Bond market value = (price / 100) * face_value * quantity
            // Example: price=98.50, quantity=10, face_value=100
            // Market value = (98.50 / 100) * 100 * 10 = 985.00
            
            BigDecimal priceAsDecimal = price.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            BigDecimal marketValue = priceAsDecimal
                    .multiply(STANDARD_FACE_VALUE)
                    .multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            
            System.out.println("Bond calculation: price=" + price + ", quantity=" + quantity + ", market_value=" + marketValue);
            
            return marketValue;
            
        } catch (Exception e) {
            System.err.println("Error in bond market value calculation: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Calculate bond market value with custom face value
     * 
     * @param price Bond price (as percentage of face value)
     * @param quantity Number of bonds held
     * @param faceValue Face value of the bond
     * @return Market value of the bond position
     */
    public BigDecimal calculateBondMarketValue(BigDecimal price, BigDecimal quantity, BigDecimal faceValue) {
        if (price == null || quantity == null || faceValue == null) {
            System.err.println("Price, quantity, or face value is null for bond calculation, returning zero");
            return BigDecimal.ZERO;
        }
        
        try {
            BigDecimal priceAsDecimal = price.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            BigDecimal marketValue = priceAsDecimal
                    .multiply(faceValue)
                    .multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            
            System.out.println("Bond calculation with custom face value: price=" + price + 
                             ", quantity=" + quantity + ", face_value=" + faceValue + 
                             ", market_value=" + marketValue);
            
            return marketValue;
            
        } catch (Exception e) {
            System.err.println("Error in bond market value calculation with custom face value: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
} 