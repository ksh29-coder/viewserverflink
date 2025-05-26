package com.viewserver.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.data.model.Instrument;
import com.viewserver.data.model.Order;
import com.viewserver.data.model.Price;
import com.viewserver.flink.functions.InstrumentParseFunction;
import com.viewserver.flink.functions.OrderMarketValueProcessor;
import com.viewserver.flink.functions.OrderParseFunction;
import com.viewserver.flink.functions.PriceParseFunction;
import com.viewserver.flink.model.OrderMV;
import com.viewserver.flink.serialization.OrderMVKafkaSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Flink job for real-time order market value calculations using KeyedState.
 * 
 * This job processes order updates immediately and calculates market values
 * by looking up instruments and prices from Flink state (no windowed joins).
 * 
 * Architecture:
 * 1. Bootstrap: Load instruments into KeyedState
 * 2. Runtime: Process order updates and price updates immediately using state lookups
 * 3. Output: Emit OrderMV records to aggregation topic
 */
public class OrderMarketValueJob {
    
    private static final Logger log = LoggerFactory.getLogger(OrderMarketValueJob.class);
    
    // Static ObjectMapper to avoid serialization issues
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public static void main(String[] args) throws Exception {
        
        // Configuration
        String kafkaBootstrapServers = getParameter(args, "kafka.bootstrap-servers", "localhost:9092");
        String consumerGroupId = getParameter(args, "consumer.group-id", "flink-order-mv-keyed-state");
        
        log.info("Starting Flink OrderMarketValue Job with KeyedState");
        log.info("Kafka Bootstrap Servers: {}", kafkaBootstrapServers);
        log.info("Consumer Group ID: {}", consumerGroupId);
        
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Start with parallelism 1 for simplicity
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000); // 30 seconds
        
        // ==================== CREATE KAFKA SOURCES ====================
        
        // Orders source (order events)
        KafkaSource<String> ordersSource = createKafkaSource(
            kafkaBootstrapServers, 
            "base.order-events", 
            consumerGroupId + "-orders"
        );
        
        // Instruments source (static data)
        KafkaSource<String> instrumentsSource = createKafkaSource(
            kafkaBootstrapServers, 
            "base.instrument", 
            consumerGroupId + "-instruments"
        );
        
        // Prices source (every 5 seconds)
        KafkaSource<String> pricesSource = createKafkaSource(
            kafkaBootstrapServers, 
            "base.price", 
            consumerGroupId + "-prices"
        );
        
        // ==================== CREATE DATA STREAMS ====================
        
        DataStream<Order> ordersStream = env
            .fromSource(ordersSource, WatermarkStrategy.noWatermarks(), "Orders Source")
            .map(new OrderParseFunction())
            .filter(order -> order != null)
            .name("Parse Orders");
        
        DataStream<Instrument> instrumentsStream = env
            .fromSource(instrumentsSource, WatermarkStrategy.noWatermarks(), "Instruments Source")
            .map(new InstrumentParseFunction())
            .filter(instrument -> instrument != null)
            .name("Parse Instruments");
        
        DataStream<Price> pricesStream = env
            .fromSource(pricesSource, WatermarkStrategy.noWatermarks(), "Prices Source")
            .map(new PriceParseFunction())
            .filter(price -> price != null)
            .name("Parse Prices");
        
        // ==================== UNIFIED STATE PROCESSING ====================
        
        // Create a unified processor that handles all data types
        OrderMarketValueProcessor processor = new OrderMarketValueProcessor();
        
        // Convert all streams to a common format and union them
        // Orders -> Tagged as "ORDER"
        DataStream<String> ordersTagged = ordersStream
            .map(order -> "ORDER:" + OrderMarketValueJob.getObjectMapper().writeValueAsString(order))
            .name("Tag Orders");
        
        // Instruments -> Tagged as "INSTRUMENT"  
        DataStream<String> instrumentsTagged = instrumentsStream
            .map(instrument -> "INSTRUMENT:" + OrderMarketValueJob.getObjectMapper().writeValueAsString(instrument))
            .name("Tag Instruments");
        
        // Prices -> Tagged as "PRICE"
        DataStream<String> pricesTagged = pricesStream
            .map(price -> "PRICE:" + OrderMarketValueJob.getObjectMapper().writeValueAsString(price))
            .name("Tag Prices");
        
        // Union all streams and process by instrumentId
        DataStream<OrderMV> orderMVStream = ordersTagged
            .union(instrumentsTagged, pricesTagged)
            .keyBy(taggedData -> {
                // Extract instrumentId from tagged data
                try {
                    String[] parts = taggedData.split(":", 2);
                    String type = parts[0];
                    String json = parts[1];
                    
                    if ("ORDER".equals(type)) {
                        Order order = OrderMarketValueJob.getObjectMapper().readValue(json, Order.class);
                        return order.getInstrumentId();
                    } else if ("INSTRUMENT".equals(type)) {
                        Instrument instrument = OrderMarketValueJob.getObjectMapper().readValue(json, Instrument.class);
                        return instrument.getInstrumentId();
                    } else if ("PRICE".equals(type)) {
                        Price price = OrderMarketValueJob.getObjectMapper().readValue(json, Price.class);
                        return price.getInstrumentId();
                    }
                    return "unknown";
                } catch (Exception e) {
                    log.error("Failed to extract instrumentId from tagged data: {}", e.getMessage());
                    return "error";
                }
            })
            .process(processor.createUnifiedProcessor())
            .name("Unified State Processing");
        
        // ==================== OUTPUT TO KAFKA ====================
        
        // Create Kafka sink for aggregation topic
        KafkaSink<OrderMV> orderMVSink = KafkaSink.<OrderMV>builder()
            .setBootstrapServers(kafkaBootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("aggregation.order-mv")
                .setValueSerializationSchema(new OrderMVKafkaSerializationSchema())
                .build())
            .build();
        
        // Sink the results
        orderMVStream.sinkTo(orderMVSink).name("Sink to Kafka");
        
        // Add logging for monitoring
        orderMVStream
            .map(orderMV -> {
                log.info("🚀 REAL-TIME ORDER UPDATE: {} {} order for {} @ ${} = ${} USD (Filled: ${} USD) [{}]", 
                        orderMV.getOrderQuantity().compareTo(java.math.BigDecimal.ZERO) > 0 ? "BUY" : "SELL",
                        orderMV.getOrderQuantity().abs(),
                        orderMV.getInstrumentName(),
                        orderMV.getPrice(),
                        orderMV.getOrderMarketValueUSD(),
                        orderMV.getFilledMarketValueUSD(),
                        orderMV.getOrderStatus());
                return orderMV;
            })
            .name("Log Order Market Value Updates");
        
        // ==================== EXECUTE JOB ====================
        
        log.info("🚀 Starting Flink OrderMarketValue job with KeyedState approach...");
        env.execute("Order Market Value Job - KeyedState");
    }
    
    /**
     * Custom robust string deserializer that handles encoding issues gracefully
     */
    public static class RobustStringDeserializer implements DeserializationSchema<String> {
        
        private static final Logger log = LoggerFactory.getLogger(RobustStringDeserializer.class);
        
        @Override
        public String deserialize(byte[] message) throws IOException {
            if (message == null) {
                log.warn("Received null message, returning empty string");
                return "";
            }
            
            try {
                // Try UTF-8 first (most common)
                String result = new String(message, StandardCharsets.UTF_8);
                
                // Basic validation - check if it looks like JSON
                if (result.trim().startsWith("{") && result.trim().endsWith("}")) {
                    return result;
                } else {
                    log.warn("Message doesn't look like JSON: {}", result.substring(0, Math.min(100, result.length())));
                    return result; // Return anyway, let the JSON parser handle it
                }
                
            } catch (Exception e) {
                log.error("Failed to deserialize message as UTF-8, trying ISO-8859-1: {}", e.getMessage());
                
                try {
                    // Fallback to ISO-8859-1
                    return new String(message, StandardCharsets.ISO_8859_1);
                } catch (Exception e2) {
                    log.error("Failed to deserialize message with any encoding: {}", e2.getMessage());
                    throw new IOException("Failed to deserialize message", e2);
                }
            }
        }
        
        @Override
        public boolean isEndOfStream(String nextElement) {
            return false;
        }
        
        @Override
        public TypeInformation<String> getProducedType() {
            return TypeInformation.of(String.class);
        }
    }

    /**
     * Create a Kafka source for the given topic
     */
    private static KafkaSource<String> createKafkaSource(String bootstrapServers, String topic, String groupId) {
        return KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.earliest()) // Changed from latest to earliest
            .setValueOnlyDeserializer(new RobustStringDeserializer())
            .build();
    }
    
    /**
     * Get parameter from command line args or use default
     */
    private static String getParameter(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (("--" + key).equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
    
    /**
     * Get the static ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
} 