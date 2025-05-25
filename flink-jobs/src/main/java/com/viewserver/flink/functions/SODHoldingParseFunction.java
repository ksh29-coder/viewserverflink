package com.viewserver.flink.functions;

import com.viewserver.data.model.SODHolding;
import com.viewserver.flink.HoldingMarketValueJob;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializable function to parse SODHolding objects from JSON strings.
 */
public class SODHoldingParseFunction implements MapFunction<String, SODHolding> {
    
    private static final Logger log = LoggerFactory.getLogger(SODHoldingParseFunction.class);
    
    @Override
    public SODHolding map(String json) throws Exception {
        try {
            SODHolding holding = HoldingMarketValueJob.getObjectMapper().readValue(json, SODHolding.class);
            log.debug("Parsed holding: {} shares of {} in account {}", 
                    holding.getPosition(), holding.getInstrumentId(), holding.getAccountId());
            return holding;
        } catch (Exception e) {
            log.error("Failed to parse SODHolding: {}", e.getMessage());
            return null;
        }
    }
} 