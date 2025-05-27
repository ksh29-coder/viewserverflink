package com.viewserver.flink.functions;

import com.viewserver.data.model.Price;
import com.viewserver.flink.UnifiedMarketValueJob;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializable function to parse Price objects from JSON strings.
 */
public class PriceParseFunction implements MapFunction<String, Price> {
    
    private static final Logger log = LoggerFactory.getLogger(PriceParseFunction.class);
    
    @Override
    public Price map(String json) throws Exception {
        try {
            // Check for null or empty input
            if (json == null || json.trim().isEmpty()) {
                log.warn("Received null or empty JSON string for Price");
                return null;
            }
            
            // Log the raw JSON for debugging (first 200 chars)
            log.debug("Parsing Price JSON: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
            
            Price price = UnifiedMarketValueJob.getObjectMapper().readValue(json, Price.class);
            
            // Validate the parsed price
            if (price.getInstrumentId() == null || price.getPrice() == null) {
                log.warn("Parsed Price has null instrumentId or price: {}", json);
                return null;
            }
            
            log.debug("Successfully parsed price: {} = ${}", price.getInstrumentId(), price.getPrice());
            return price;
            
        } catch (Exception e) {
            log.error("Failed to parse Price from JSON: {}", e.getMessage());
            log.error("Problematic JSON (first 500 chars): {}", 
                     json != null && json.length() > 500 ? json.substring(0, 500) + "..." : json);
            return null;
        }
    }
} 