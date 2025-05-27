package com.viewserver.flink.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.data.model.Instrument;
import com.viewserver.data.model.Order;
import com.viewserver.data.model.Price;
import com.viewserver.data.model.SODHolding;
import com.viewserver.flink.model.UnifiedMarketValue;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified KeyedProcessFunction that maintains holdings, orders, and instruments in Flink state
 * and calculates market values immediately when price updates arrive.
 * 
 * This processor ensures price consistency by using the same price state for both
 * holdings and orders calculations.
 * 
 * State Management:
 * - holdingsState: List of holdings for this instrumentId (multiple accounts can hold same instrument)
 * - ordersState: Map of orders for this instrumentId (keyed by orderId)
 * - instrumentState: Instrument details for this instrumentId
 * - lastPriceState: Last known price for this instrumentId
 * 
 * Processing Logic:
 * 1. When holdings arrive: Store in state
 * 2. When orders arrive: Store in state
 * 3. When instruments arrive: Store in state  
 * 4. When prices arrive: Calculate market values immediately using state for BOTH holdings and orders
 */
public class UnifiedMarketValueProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedMarketValueProcessor.class);
    
    // Static ObjectMapper to avoid serialization issues
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * Create the unified processor that handles all data types
     */
    public KeyedProcessFunction<String, String, UnifiedMarketValue> createUnifiedProcessor() {
        return new KeyedProcessFunction<String, String, UnifiedMarketValue>() {
            
            // State handles
            private ValueState<List<SODHolding>> holdingsState;
            private ValueState<Map<String, Order>> ordersState;
            private ValueState<Instrument> instrumentState;
            private ValueState<Price> lastPriceState;
            
            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                
                // Initialize state descriptors with proper type information
                ValueStateDescriptor<List<SODHolding>> holdingsDescriptor = new ValueStateDescriptor<>(
                    "holdings-state",
                    TypeInformation.of(new TypeHint<List<SODHolding>>() {})
                );
                
                ValueStateDescriptor<Map<String, Order>> ordersDescriptor = new ValueStateDescriptor<>(
                    "orders-state",
                    TypeInformation.of(new TypeHint<Map<String, Order>>() {})
                );
                
                ValueStateDescriptor<Instrument> instrumentDescriptor = new ValueStateDescriptor<>(
                    "instrument-state",
                    TypeInformation.of(Instrument.class)
                );
                
                ValueStateDescriptor<Price> lastPriceDescriptor = new ValueStateDescriptor<>(
                    "last-price-state",
                    TypeInformation.of(Price.class)
                );
                
                // Get state handles from runtime context
                holdingsState = getRuntimeContext().getState(holdingsDescriptor);
                ordersState = getRuntimeContext().getState(ordersDescriptor);
                instrumentState = getRuntimeContext().getState(instrumentDescriptor);
                lastPriceState = getRuntimeContext().getState(lastPriceDescriptor);
                
                log.debug("Initialized KeyedState for UnifiedMarketValueProcessor");
            }
            
            @Override
            public void processElement(String taggedData, Context ctx, Collector<UnifiedMarketValue> out) throws Exception {
                
                try {
                    String[] parts = taggedData.split(":", 2);
                    if (parts.length != 2) {
                        log.warn("Invalid tagged data format: {}", taggedData);
                        return;
                    }
                    
                    String type = parts[0];
                    String json = parts[1];
                    
                    switch (type) {
                        case "HOLDING":
                            processHolding(json);
                            break;
                        case "ORDER":
                            processOrder(json);
                            break;
                        case "INSTRUMENT":
                            processInstrument(json);
                            break;
                        case "PRICE":
                            processPrice(json, out);
                            break;
                        default:
                            log.warn("Unknown data type: {}", type);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to process tagged data: {}", e.getMessage());
                }
            }
            
            /**
             * Process holding data and store in state
             */
            private void processHolding(String json) throws Exception {
                SODHolding holding = OBJECT_MAPPER.readValue(json, SODHolding.class);
                
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
                
                log.debug("Stored holding for instrument {} in account {}: {} shares", 
                         holding.getInstrumentId(), holding.getAccountId(), holding.getPosition());
            }
            
            /**
             * Process order data and store in state
             */
            private void processOrder(String json) throws Exception {
                Order order = OBJECT_MAPPER.readValue(json, Order.class);
                
                // Get current orders map or create new one
                Map<String, Order> currentOrders = ordersState.value();
                if (currentOrders == null) {
                    currentOrders = new HashMap<>();
                }
                
                // Add or update the order
                currentOrders.put(order.getOrderId(), order);
                
                // Update state
                ordersState.update(currentOrders);
                
                log.debug("Stored order {} for instrument {} in account {}: {} shares @ ${}", 
                         order.getOrderId(), order.getInstrumentId(), order.getAccountId(), 
                         order.getOrderQuantity(), order.getOrderPrice());
            }
            
            /**
             * Process instrument data and store in state
             */
            private void processInstrument(String json) throws Exception {
                Instrument instrument = OBJECT_MAPPER.readValue(json, Instrument.class);
                
                // Update instrument state
                instrumentState.update(instrument);
                
                log.debug("Stored instrument {}: {}", instrument.getInstrumentId(), instrument.getInstrumentName());
            }
            
            /**
             * Process price updates and calculate market values for BOTH holdings and orders
             */
            private void processPrice(String json, Collector<UnifiedMarketValue> out) throws Exception {
                Price newPrice = OBJECT_MAPPER.readValue(json, Price.class);
                String instrumentId = newPrice.getInstrumentId();
                
                log.debug("Processing price update for {}: ${}", instrumentId, newPrice.getPrice());
                
                // Update price state
                lastPriceState.update(newPrice);
                
                // Get current state
                List<SODHolding> holdings = holdingsState.value();
                Map<String, Order> orders = ordersState.value();
                Instrument instrument = instrumentState.value();
                
                if (instrument == null) {
                    log.debug("No instrument found in state for {}", instrumentId);
                    return;
                }
                
                // Calculate market values for all holdings of this instrument
                if (holdings != null && !holdings.isEmpty()) {
                    for (SODHolding holding : holdings) {
                        try {
                            UnifiedMarketValue holdingMV = calculateHoldingMarketValue(holding, instrument, newPrice);
                            out.collect(holdingMV);
                            
                            log.info("✅ HOLDING MV: {} in account {}: {} shares @ ${} = ${} USD", 
                                    instrument.getInstrumentName(),
                                    holding.getAccountId(),
                                    holding.getPosition(),
                                    newPrice.getPrice(),
                                    holdingMV.getMarketValueUSD());
                                    
                        } catch (Exception e) {
                            log.error("Failed to calculate holding market value for {} in account {}: {}", 
                                     instrumentId, holding.getAccountId(), e.getMessage());
                        }
                    }
                }
                
                // Calculate market values for all orders of this instrument
                if (orders != null && !orders.isEmpty()) {
                    for (Order order : orders.values()) {
                        try {
                            UnifiedMarketValue orderMV = calculateOrderMarketValue(order, instrument, newPrice);
                            out.collect(orderMV);
                            
                            log.info("✅ ORDER MV: {} {} order for {} @ ${} = ${} USD (Filled: ${} USD) [{}]", 
                                    order.getOrderQuantity().compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL",
                                    order.getOrderQuantity().abs(),
                                    instrument.getInstrumentName(),
                                    newPrice.getPrice(),
                                    orderMV.getMarketValueUSD(),
                                    orderMV.getFilledMarketValueUSD(),
                                    order.getOrderStatus());
                                    
                        } catch (Exception e) {
                            log.error("Failed to calculate order market value for {} in account {}: {}", 
                                     order.getOrderId(), order.getAccountId(), e.getMessage());
                        }
                    }
                }
            }
            
            /**
             * Calculate market value for a holding using the unified price
             */
            private UnifiedMarketValue calculateHoldingMarketValue(SODHolding holding, Instrument instrument, Price price) {
                
                // Simple market value calculation: price × position
                BigDecimal marketValueLocal = price.getPrice().multiply(holding.getPosition());
                
                // For simplicity, assume all prices are in USD (no currency conversion needed)
                BigDecimal marketValueUSD = marketValueLocal;
                
                return UnifiedMarketValue.builder()
                    .recordType("HOLDING")
                    
                    // Common fields
                    .instrumentId(holding.getInstrumentId())
                    .accountId(holding.getAccountId())
                    .timestamp(holding.getTimestamp())
                    
                    // Holding-specific fields
                    .holdingId(holding.getHoldingId())
                    .date(holding.getDate())
                    .position(holding.getPosition())
                    
                    // Enriched Instrument fields
                    .instrumentName(instrument.getInstrumentName())
                    .instrumentType(instrument.getInstrumentType())
                    .currency(instrument.getCurrency())
                    .countryOfRisk(instrument.getCountryOfRisk())
                    .countryOfDomicile(instrument.getCountryOfDomicile())
                    .sector(instrument.getSector())
                    .subSectors(instrument.getSubSectors())
                    
                    // Unified Price fields (CRITICAL: Same price for all calculations)
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
             * Calculate market value for an order using the unified price
             */
            private UnifiedMarketValue calculateOrderMarketValue(Order order, Instrument instrument, Price price) {
                
                // Order market value calculation: price × orderQuantity
                BigDecimal orderMarketValueLocal = price.getPrice().multiply(order.getOrderQuantity());
                BigDecimal orderMarketValueUSD = orderMarketValueLocal;
                
                // Filled market value calculation: price × filledQuantity
                BigDecimal filledQuantity = order.getFilledQuantity() != null ? order.getFilledQuantity() : BigDecimal.ZERO;
                BigDecimal filledMarketValueLocal = price.getPrice().multiply(filledQuantity);
                BigDecimal filledMarketValueUSD = filledMarketValueLocal;
                
                return UnifiedMarketValue.builder()
                    .recordType("ORDER")
                    
                    // Common fields
                    .instrumentId(order.getInstrumentId())
                    .accountId(order.getAccountId())
                    .timestamp(order.getTimestamp())
                    
                    // Order-specific fields
                    .orderId(order.getOrderId())
                    .orderQuantity(order.getOrderQuantity())
                    .filledQuantity(order.getFilledQuantity())
                    .orderStatus(order.getOrderStatus().toString())
                    .orderPrice(order.getOrderPrice())
                    .orderType(order.getOrderType())
                    .venue(order.getVenue())
                    
                    // Enriched Instrument fields
                    .instrumentName(instrument.getInstrumentName())
                    .instrumentType(instrument.getInstrumentType())
                    .currency(instrument.getCurrency())
                    .countryOfRisk(instrument.getCountryOfRisk())
                    .countryOfDomicile(instrument.getCountryOfDomicile())
                    .sector(instrument.getSector())
                    .subSectors(instrument.getSubSectors())
                    
                    // Unified Price fields (CRITICAL: Same price for all calculations)
                    .price(price.getPrice())
                    .priceCurrency(price.getCurrency())
                    .priceSource(price.getSource())
                    .priceTimestamp(price.getDate())
                    
                    // Calculated Market Value fields
                    .marketValueLocal(orderMarketValueLocal)
                    .marketValueUSD(orderMarketValueUSD)
                    .filledMarketValueLocal(filledMarketValueLocal)
                    .filledMarketValueUSD(filledMarketValueUSD)
                    .calculationTimestamp(LocalDateTime.now())
                    
                    .build();
            }
        };
    }
} 