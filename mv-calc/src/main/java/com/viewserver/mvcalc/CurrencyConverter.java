package com.viewserver.mvcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Currency Converter for converting market values to USD.
 * Uses simplified exchange rates for POC. In production, this would integrate
 * with real-time FX rate feeds.
 * Pure Java implementation with no framework dependencies.
 */
public class CurrencyConverter {
    
    // Simplified exchange rates to USD (for POC purposes)
    // In production, these would come from real-time FX feeds
    private static final Map<String, BigDecimal> USD_EXCHANGE_RATES = Map.of(
            "USD", new BigDecimal("1.0000"),
            "EUR", new BigDecimal("1.0850"),  // 1 EUR = 1.0850 USD
            "GBP", new BigDecimal("1.2650"),  // 1 GBP = 1.2650 USD
            "JPY", new BigDecimal("0.0067"),  // 1 JPY = 0.0067 USD
            "CHF", new BigDecimal("1.1200"),  // 1 CHF = 1.1200 USD
            "CAD", new BigDecimal("0.7350"),  // 1 CAD = 0.7350 USD
            "AUD", new BigDecimal("0.6580")   // 1 AUD = 0.6580 USD
    );
    
    /**
     * Convert amount from source currency to USD
     * 
     * @param amount Amount in source currency
     * @param fromCurrency Source currency code (e.g., "EUR", "GBP")
     * @return Amount converted to USD
     */
    public BigDecimal convertToUSD(BigDecimal amount, String fromCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if (fromCurrency == null || fromCurrency.trim().isEmpty()) {
            System.err.println("Currency code is null or empty, assuming USD");
            return amount;
        }
        
        String currency = fromCurrency.toUpperCase().trim();
        
        // Already in USD
        if ("USD".equals(currency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        
        BigDecimal exchangeRate = USD_EXCHANGE_RATES.get(currency);
        if (exchangeRate == null) {
            System.err.println("No exchange rate found for currency: " + currency + ". Using 1:1 rate.");
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        
        try {
            BigDecimal convertedAmount = amount.multiply(exchangeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            
            System.out.println("Currency conversion: " + amount + " " + currency + 
                             " = " + convertedAmount + " USD (rate: " + exchangeRate + ")");
            
            return convertedAmount;
            
        } catch (Exception e) {
            System.err.println("Error converting " + amount + " " + currency + " to USD: " + e.getMessage());
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Get current exchange rate for a currency to USD
     * 
     * @param currency Currency code
     * @return Exchange rate to USD, or 1.0 if not found
     */
    public BigDecimal getExchangeRateToUSD(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return BigDecimal.ONE;
        }
        
        return USD_EXCHANGE_RATES.getOrDefault(currency.toUpperCase().trim(), BigDecimal.ONE);
    }
    
    /**
     * Check if currency is supported
     * 
     * @param currency Currency code
     * @return true if supported, false otherwise
     */
    public boolean isCurrencySupported(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return false;
        }
        
        return USD_EXCHANGE_RATES.containsKey(currency.toUpperCase().trim());
    }
} 