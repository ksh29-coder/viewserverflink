package com.viewserver.flink.functions;

import com.viewserver.data.model.Instrument;
import com.viewserver.flink.UnifiedMarketValueJob;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializable function to parse Instrument objects from JSON strings.
 */
public class InstrumentParseFunction implements MapFunction<String, Instrument> {
    
    private static final Logger log = LoggerFactory.getLogger(InstrumentParseFunction.class);
    
    @Override
    public Instrument map(String json) throws Exception {
        try {
            Instrument instrument = UnifiedMarketValueJob.getObjectMapper().readValue(json, Instrument.class);
            log.debug("Parsed instrument: {} ({})", 
                    instrument.getInstrumentName(), instrument.getInstrumentId());
            return instrument;
        } catch (Exception e) {
            log.error("Failed to parse Instrument: {}", e.getMessage());
            return null;
        }
    }
} 