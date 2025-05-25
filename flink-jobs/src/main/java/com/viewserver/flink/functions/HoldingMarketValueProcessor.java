package com.viewserver.flink.functions;

import com.viewserver.data.model.Instrument;
import com.viewserver.data.model.Price;
import com.viewserver.data.model.SODHolding;
import com.viewserver.flink.model.HoldingMV;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * KeyedProcessFunction that maintains holdings and instruments in Flink state
 * and calculates market values immediately when price updates arrive.
 * 
 * State Management:
 * - holdingsState: List of holdings for this instrumentId (multiple accounts can hold same instrument)
 * - instrumentState: Instrument details for this instrumentId
 * - lastPriceState: Last known price for this instrumentId
 * 
 * Processing Logic:
 * 1. When holdings arrive: Store in state
 * 2. When instruments arrive: Store in state  
 * 3. When prices arrive: Calculate market values immediately using state
 */
public class HoldingMarketValueProcessor extends KeyedProcessFunction<String, Price, HoldingMV> {
    
    private static final Logger log = LoggerFactory.getLogger(HoldingMarketValueProcessor.class);
    
    // State descriptors
    private ValueStateDescriptor<List<SODHolding>> holdingsDescriptor;
    private ValueStateDescriptor<Instrument> instrumentDescriptor;
    private ValueStateDescriptor<Price> lastPriceDescriptor;
    
    // State handles (initialized by Flink)
    private ValueState<List<SODHolding>> holdingsState;
    private ValueState<Instrument> instrumentState;
    private ValueState<Price> lastPriceState;
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // Initialize state descriptors with proper type information
        holdingsDescriptor = new ValueStateDescriptor<>(
            "holdings-state",
            TypeInformation.of(new TypeHint<List<SODHolding>>() {})
        );
        
        instrumentDescriptor = new ValueStateDescriptor<>(
            "instrument-state",
            TypeInformation.of(Instrument.class)
        );
        
        lastPriceDescriptor = new ValueStateDescriptor<>(
            "last-price-state",
            TypeInformation.of(Price.class)
        );
        
        // Get state handles from runtime context
        holdingsState = getRuntimeContext().getState(holdingsDescriptor);
        instrumentState = getRuntimeContext().getState(instrumentDescriptor);
        lastPriceState = getRuntimeContext().getState(lastPriceDescriptor);
        
        log.debug("Initialized KeyedState for HoldingMarketValueProcessor");
    }
    
    /**
     * Process price updates and calculate market values immediately
     */
    @Override
    public void processElement(Price newPrice, Context ctx, Collector<HoldingMV> out) throws Exception {
        
        String instrumentId = newPrice.getInstrumentId();
        log.debug("Processing price update for {}: ${}", instrumentId, newPrice.getPrice());
        
        // Update price state
        lastPriceState.update(newPrice);
        
        // Get current holdings and instrument from state
        List<SODHolding> holdings = holdingsState.value();
        Instrument instrument = instrumentState.value();
        
        if (holdings == null || holdings.isEmpty()) {
            log.debug("No holdings found in state for instrument {}", instrumentId);
            return;
        }
        
        if (instrument == null) {
            log.debug("No instrument found in state for {}", instrumentId);
            return;
        }
        
        // Calculate market values for all holdings of this instrument
        for (SODHolding holding : holdings) {
            try {
                HoldingMV holdingMV = calculateMarketValue(holding, instrument, newPrice);
                out.collect(holdingMV);
                
                log.info("✅ Calculated market value for {} in account {}: {} shares @ ${} = ${} USD", 
                        instrument.getInstrumentName(),
                        holding.getAccountId(),
                        holding.getPosition(),
                        newPrice.getPrice(),
                        holdingMV.getMarketValueUSD());
                        
            } catch (Exception e) {
                log.error("Failed to calculate market value for holding {} in account {}: {}", 
                         instrumentId, holding.getAccountId(), e.getMessage());
            }
        }
    }
    
    /**
     * Calculate market value using simplified logic
     */
    private HoldingMV calculateMarketValue(SODHolding holding, Instrument instrument, Price price) {
        
        // Simple market value calculation: price × position
        BigDecimal marketValueLocal = price.getPrice().multiply(holding.getPosition());
        
        // For simplicity, assume all prices are in USD (no currency conversion needed)
        BigDecimal marketValueUSD = marketValueLocal;
        
        return HoldingMV.builder()
            // Original SODHolding fields
            .holdingId(holding.getHoldingId())
            .date(holding.getDate())
            .instrumentId(holding.getInstrumentId())
            .accountId(holding.getAccountId())
            .position(holding.getPosition())
            .timestamp(holding.getTimestamp())
            
            // Enriched Instrument fields
            .instrumentName(instrument.getInstrumentName())
            .instrumentType(instrument.getInstrumentType())
            .currency(instrument.getCurrency())
            .countryOfRisk(instrument.getCountryOfRisk())
            .countryOfDomicile(instrument.getCountryOfDomicile())
            .sector(instrument.getSector())
            .subSectors(instrument.getSubSectors())
            
            // Enriched Price fields
            .price(price.getPrice())
            .priceCurrency(price.getCurrency())
            .priceSource(price.getSource())
            .priceTimestamp(price.getDate())
            
            // Calculated Market Value fields
            .marketValueLocal(marketValueLocal)
            .marketValueUSD(marketValueUSD)
            .calculationTimestamp(LocalDateTime.now())
            
            .build();
    }
    
    /**
     * Create a processor for storing holdings in state
     */
    public KeyedProcessFunction<String, SODHolding, Void> createHoldingStateProcessor() {
        return new KeyedProcessFunction<String, SODHolding, Void>() {
            
            private ValueState<List<SODHolding>> holdingsState;
            
            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                
                // Create state descriptor for holdings
                ValueStateDescriptor<List<SODHolding>> holdingsDescriptor = new ValueStateDescriptor<>(
                    "holdings-state",
                    TypeInformation.of(new TypeHint<List<SODHolding>>() {})
                );
                
                holdingsState = getRuntimeContext().getState(holdingsDescriptor);
            }
            
            @Override
            public void processElement(SODHolding holding, Context ctx, Collector<Void> out) throws Exception {
                
                // Get current holdings list or create new one
                List<SODHolding> currentHoldings = holdingsState.value();
                if (currentHoldings == null) {
                    currentHoldings = new ArrayList<>();
                }
                
                // Remove any existing holding for the same account (update scenario)
                currentHoldings.removeIf(h -> h.getAccountId().equals(holding.getAccountId()));
                
                // Add the new/updated holding
                currentHoldings.add(holding);
                
                // Update state
                holdingsState.update(currentHoldings);
                
                log.info("📝 Stored holding in state: {} shares of {} in account {}", 
                        holding.getPosition(), holding.getInstrumentId(), holding.getAccountId());
            }
        };
    }
    
    /**
     * Create a processor for storing instruments in state
     */
    public KeyedProcessFunction<String, Instrument, Void> createInstrumentStateProcessor() {
        return new KeyedProcessFunction<String, Instrument, Void>() {
            
            private ValueState<Instrument> instrumentState;
            
            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                
                // Create state descriptor for instruments
                ValueStateDescriptor<Instrument> instrumentDescriptor = new ValueStateDescriptor<>(
                    "instrument-state",
                    TypeInformation.of(Instrument.class)
                );
                
                instrumentState = getRuntimeContext().getState(instrumentDescriptor);
            }
            
            @Override
            public void processElement(Instrument instrument, Context ctx, Collector<Void> out) throws Exception {
                
                // Store instrument in state
                instrumentState.update(instrument);
                
                log.info("📝 Stored instrument in state: {} ({})", 
                        instrument.getInstrumentName(), instrument.getInstrumentId());
            }
        };
    }
    
    /**
     * Create a unified processor that handles all data types in the same state context
     */
    public KeyedProcessFunction<String, String, HoldingMV> createUnifiedProcessor() {
        return new KeyedProcessFunction<String, String, HoldingMV>() {
            
            // Shared state for all data types
            private ValueState<List<SODHolding>> holdingsState;
            private ValueState<Instrument> instrumentState;
            private ValueState<Price> lastPriceState;
            
            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                
                // Initialize shared state descriptors
                ValueStateDescriptor<List<SODHolding>> holdingsDescriptor = new ValueStateDescriptor<>(
                    "unified-holdings-state",
                    TypeInformation.of(new TypeHint<List<SODHolding>>() {})
                );
                
                ValueStateDescriptor<Instrument> instrumentDescriptor = new ValueStateDescriptor<>(
                    "unified-instrument-state",
                    TypeInformation.of(Instrument.class)
                );
                
                ValueStateDescriptor<Price> lastPriceDescriptor = new ValueStateDescriptor<>(
                    "unified-last-price-state",
                    TypeInformation.of(Price.class)
                );
                
                // Get shared state handles
                holdingsState = getRuntimeContext().getState(holdingsDescriptor);
                instrumentState = getRuntimeContext().getState(instrumentDescriptor);
                lastPriceState = getRuntimeContext().getState(lastPriceDescriptor);
                
                log.debug("Initialized unified KeyedState processor");
            }
            
            @Override
            public void processElement(String taggedData, Context ctx, Collector<HoldingMV> out) throws Exception {
                
                try {
                    // Parse the tagged data
                    String[] parts = taggedData.split(":", 2);
                    if (parts.length != 2) {
                        log.warn("Invalid tagged data format: {}", taggedData);
                        return;
                    }
                    
                    String type = parts[0];
                    String json = parts[1];
                    
                    if ("HOLDING".equals(type)) {
                        processHolding(json);
                    } else if ("INSTRUMENT".equals(type)) {
                        processInstrument(json);
                    } else if ("PRICE".equals(type)) {
                        processPrice(json, out);
                    } else {
                        log.warn("Unknown data type: {}", type);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to process tagged data: {}", e.getMessage(), e);
                }
            }
            
            private void processHolding(String json) throws Exception {
                SODHolding holding = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, SODHolding.class);
                
                // Get current holdings list or create new one
                List<SODHolding> currentHoldings = holdingsState.value();
                if (currentHoldings == null) {
                    currentHoldings = new ArrayList<>();
                }
                
                // Remove any existing holding for the same account (update scenario)
                currentHoldings.removeIf(h -> h.getAccountId().equals(holding.getAccountId()));
                
                // Add the new/updated holding
                currentHoldings.add(holding);
                
                // Update state
                holdingsState.update(currentHoldings);
                
                log.info("📝 Stored holding in unified state: {} shares of {} in account {}", 
                        holding.getPosition(), holding.getInstrumentId(), holding.getAccountId());
            }
            
            private void processInstrument(String json) throws Exception {
                Instrument instrument = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, Instrument.class);
                
                // Store instrument in state
                instrumentState.update(instrument);
                
                log.info("📝 Stored instrument in unified state: {} ({})", 
                        instrument.getInstrumentName(), instrument.getInstrumentId());
            }
            
            private void processPrice(String json, Collector<HoldingMV> out) throws Exception {
                Price newPrice = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, Price.class);
                
                String instrumentId = newPrice.getInstrumentId();
                log.debug("Processing price update for {}: ${}", instrumentId, newPrice.getPrice());
                
                // Update price state
                lastPriceState.update(newPrice);
                
                // Get current holdings and instrument from shared state
                List<SODHolding> holdings = holdingsState.value();
                Instrument instrument = instrumentState.value();
                
                if (holdings == null || holdings.isEmpty()) {
                    log.debug("No holdings found in unified state for instrument {}", instrumentId);
                    return;
                }
                
                if (instrument == null) {
                    log.debug("No instrument found in unified state for {}", instrumentId);
                    return;
                }
                
                // Calculate market values for all holdings of this instrument
                for (SODHolding holding : holdings) {
                    try {
                        HoldingMV holdingMV = calculateMarketValue(holding, instrument, newPrice);
                        out.collect(holdingMV);
                        
                        log.info("🚀 UNIFIED STATE: Calculated market value for {} in account {}: {} shares @ ${} = ${} USD", 
                                instrument.getInstrumentName(),
                                holding.getAccountId(),
                                holding.getPosition(),
                                newPrice.getPrice(),
                                holdingMV.getMarketValueUSD());
                                
                    } catch (Exception e) {
                        log.error("Failed to calculate market value for holding {} in account {}: {}", 
                                 instrumentId, holding.getAccountId(), e.getMessage());
                    }
                }
            }
            
            /**
             * Calculate market value using simplified logic (same as main processor)
             */
            private HoldingMV calculateMarketValue(SODHolding holding, Instrument instrument, Price price) {
                
                // Simple market value calculation: price × position
                BigDecimal marketValueLocal = price.getPrice().multiply(holding.getPosition());
                
                // For simplicity, assume all prices are in USD (no currency conversion needed)
                BigDecimal marketValueUSD = marketValueLocal;
                
                return HoldingMV.builder()
                    // Original SODHolding fields
                    .holdingId(holding.getHoldingId())
                    .date(holding.getDate())
                    .instrumentId(holding.getInstrumentId())
                    .accountId(holding.getAccountId())
                    .position(holding.getPosition())
                    .timestamp(holding.getTimestamp())
                    
                    // Enriched Instrument fields
                    .instrumentName(instrument.getInstrumentName())
                    .instrumentType(instrument.getInstrumentType())
                    .currency(instrument.getCurrency())
                    .countryOfRisk(instrument.getCountryOfRisk())
                    .countryOfDomicile(instrument.getCountryOfDomicile())
                    .sector(instrument.getSector())
                    .subSectors(instrument.getSubSectors())
                    
                    // Enriched Price fields
                    .price(price.getPrice())
                    .priceCurrency(price.getCurrency())
                    .priceSource(price.getSource())
                    .priceTimestamp(price.getDate())
                    
                    // Calculated Market Value fields
                    .marketValueLocal(marketValueLocal)
                    .marketValueUSD(marketValueUSD)
                    .calculationTimestamp(LocalDateTime.now())
                    
                    .build();
            }
        };
    }
} 