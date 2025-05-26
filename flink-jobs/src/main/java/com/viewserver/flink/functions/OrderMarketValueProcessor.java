package com.viewserver.flink.functions;

import com.viewserver.data.model.Instrument;
import com.viewserver.data.model.Order;
import com.viewserver.data.model.Price;
import com.viewserver.flink.model.OrderMV;
import com.viewserver.mvcalc.MarketValueCalculator;
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
 * KeyedProcessFunction that maintains orders and instruments in Flink state
 * and calculates market values immediately when price updates arrive.
 * 
 * State Management:
 * - ordersState: List of orders for this instrumentId (multiple orders can be for same instrument)
 * - instrumentState: Instrument details for this instrumentId
 * - lastPriceState: Last known price for this instrumentId
 * 
 * Processing Logic:
 * 1. When orders arrive: Store in state
 * 2. When instruments arrive: Store in state  
 * 3. When prices arrive: Calculate market values immediately using state
 */
public class OrderMarketValueProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(OrderMarketValueProcessor.class);
    
    /**
     * Create a processor for unified state management (orders, instruments, prices)
     */
    public KeyedProcessFunction<String, String, OrderMV> createUnifiedProcessor() {
        return new UnifiedOrderProcessor();
    }
    
    /**
     * Unified processor that handles all data types in a single state
     */
    private static class UnifiedOrderProcessor extends KeyedProcessFunction<String, String, OrderMV> {
        
        private static final Logger log = LoggerFactory.getLogger(UnifiedOrderProcessor.class);
        
        // State for orders (multiple orders can exist for same instrument)
        private ValueState<List<Order>> ordersState;
        
        // State for instrument details
        private ValueState<Instrument> instrumentState;
        
        // State for last known price
        private ValueState<Price> lastPriceState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Initialize state descriptors
            ordersState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("orders", TypeInformation.of(new TypeHint<List<Order>>() {}))
            );
            
            instrumentState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("instrument", TypeInformation.of(Instrument.class))
            );
            
            lastPriceState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("lastPrice", TypeInformation.of(Price.class))
            );
            
            log.info("Initialized unified order processor state for key processing");
        }
        
        @Override
        public void processElement(String taggedData, Context ctx, Collector<OrderMV> out) throws Exception {
            
            try {
                String[] parts = taggedData.split(":", 2);
                if (parts.length != 2) {
                    log.warn("Invalid tagged data format: {}", taggedData);
                    return;
                }
                
                String type = parts[0];
                String json = parts[1];
                
                switch (type) {
                    case "ORDER":
                        processOrder(json, out);
                        break;
                    case "INSTRUMENT":
                        processInstrument(json, out);
                        break;
                    case "PRICE":
                        processPrice(json, out);
                        break;
                    default:
                        log.warn("Unknown data type: {}", type);
                }
                
            } catch (Exception e) {
                log.error("Error processing tagged data: {}", taggedData, e);
            }
        }
        
        private void processOrder(String json, Collector<OrderMV> out) throws Exception {
            Order newOrder = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, Order.class);
            
            String instrumentId = newOrder.getInstrumentId();
            log.debug("Processing order for {}: {} (status: {})", instrumentId, newOrder.getOrderId(), newOrder.getOrderStatus());
            
            // Get current orders list or create new one
            List<Order> orders = ordersState.value();
            if (orders == null) {
                orders = new ArrayList<>();
            }
            
            // Update or add the order (replace if same orderId exists)
            orders.removeIf(order -> order.getOrderId().equals(newOrder.getOrderId()));
            orders.add(newOrder);
            
            // Update state
            ordersState.update(orders);
            
            log.debug("Updated orders state for instrument {}: {} orders", instrumentId, orders.size());
            
            // Try to calculate market values if we have price and instrument
            tryCalculateMarketValues(out);
        }
        
        private void processInstrument(String json, Collector<OrderMV> out) throws Exception {
            Instrument newInstrument = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, Instrument.class);
            
            String instrumentId = newInstrument.getInstrumentId();
            log.debug("Processing instrument: {} ({})", instrumentId, newInstrument.getInstrumentName());
            
            // Update instrument state
            instrumentState.update(newInstrument);
            
            log.debug("Updated instrument state for {}", instrumentId);
            
            // Try to calculate market values if we have orders and price
            tryCalculateMarketValues(out);
        }
        
        private void processPrice(String json, Collector<OrderMV> out) throws Exception {
            Price newPrice = com.viewserver.flink.HoldingMarketValueJob.getObjectMapper().readValue(json, Price.class);
            
            String instrumentId = newPrice.getInstrumentId();
            log.debug("Processing price update for {}: ${}", instrumentId, newPrice.getPrice());
            
            // Update price state
            lastPriceState.update(newPrice);
            
            // Try to calculate market values if we have orders and instrument
            tryCalculateMarketValues(out);
        }
        
        private void tryCalculateMarketValues(Collector<OrderMV> out) throws Exception {
            // Get current state
            List<Order> orders = ordersState.value();
            Instrument instrument = instrumentState.value();
            Price price = lastPriceState.value();
            
            if (orders == null || orders.isEmpty()) {
                log.debug("No orders found in unified state");
                return;
            }
            
            if (instrument == null) {
                log.debug("No instrument found in unified state");
                return;
            }
            
            if (price == null) {
                log.debug("No price found in unified state");
                return;
            }
            
            // Calculate market values for all orders of this instrument
            for (Order order : orders) {
                try {
                    OrderMV orderMV = calculateMarketValue(order, instrument, price);
                    out.collect(orderMV);
                    
                    log.info("🚀 UNIFIED STATE: Calculated order market value for {} in account {}: Order {} @ ${} = ${} USD (Filled: ${} USD)", 
                            instrument.getInstrumentName(),
                            order.getAccountId(),
                            order.getOrderQuantity(),
                            price.getPrice(),
                            orderMV.getOrderMarketValueUSD(),
                            orderMV.getFilledMarketValueUSD());
                            
                } catch (Exception e) {
                    log.error("Failed to calculate market value for order {} in account {}: {}", 
                             order.getOrderId(), order.getAccountId(), e.getMessage());
                }
            }
        }
        
        /**
         * Calculate market value using MVCalculator library
         */
        private OrderMV calculateMarketValue(Order order, Instrument instrument, Price price) {
            
            // Calculate order market values using MVCalculator
            BigDecimal orderMarketValueLocal = MarketValueCalculator.calculateMarketValueLocal(
                instrument.getInstrumentType(), 
                price.getPrice(), 
                order.getOrderQuantity().abs(), // Use absolute value for market value calculation
                instrument.getCurrency()
            );
            
            BigDecimal orderMarketValueUSD = MarketValueCalculator.calculateMarketValueUSD(
                orderMarketValueLocal, 
                instrument.getCurrency()
            );
            
            // Calculate filled market values using MVCalculator
            BigDecimal filledMarketValueLocal = MarketValueCalculator.calculateMarketValueLocal(
                instrument.getInstrumentType(), 
                price.getPrice(), 
                order.getFilledQuantity(), 
                instrument.getCurrency()
            );
            
            BigDecimal filledMarketValueUSD = MarketValueCalculator.calculateMarketValueUSD(
                filledMarketValueLocal, 
                instrument.getCurrency()
            );
            
            return OrderMV.builder()
                // Original Order fields
                .orderId(order.getOrderId())
                .instrumentId(order.getInstrumentId())
                .accountId(order.getAccountId())
                .date(order.getDate())
                .orderQuantity(order.getOrderQuantity())
                .filledQuantity(order.getFilledQuantity())
                .orderStatus(order.getOrderStatus().toString())
                .orderPrice(order.getOrderPrice())
                .orderType(order.getOrderType())
                .venue(order.getVenue())
                .timestamp(order.getTimestamp())
                
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
                .orderMarketValueLocal(orderMarketValueLocal)
                .orderMarketValueUSD(orderMarketValueUSD)
                .filledMarketValueLocal(filledMarketValueLocal)
                .filledMarketValueUSD(filledMarketValueUSD)
                .calculationTimestamp(LocalDateTime.now())
                
                .build();
        }
    }
} 