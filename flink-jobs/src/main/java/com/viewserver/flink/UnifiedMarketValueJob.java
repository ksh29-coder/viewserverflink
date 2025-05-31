package com.viewserver.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.viewserver.data.model.Instrument;
import com.viewserver.data.model.Order;
import com.viewserver.data.model.Price;
import com.viewserver.data.model.SODHolding;
import com.viewserver.flink.functions.InstrumentParseFunction;
import com.viewserver.flink.functions.OrderParseFunction;
import com.viewserver.flink.functions.PriceParseFunction;
import com.viewserver.flink.functions.SODHoldingParseFunction;
import com.viewserver.flink.functions.UnifiedMarketValueProcessor;
import com.viewserver.flink.model.UnifiedMarketValue;
import com.viewserver.flink.serialization.UnifiedMarketValueKafkaSerializationSchema;
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
 * Unified Flink job for real-time market value calculations using KeyedState.
 * 
 * This job processes both holdings and orders with shared price state to ensure
 * price consistency across both data types. When a price update arrives, it
 * calculates market values for BOTH holdings and orders using the same price.
 * 
 * Architecture:
 * 1. Bootstrap: Load holdings, orders, and instruments into KeyedState (keyed by instrumentId)
 * 2. Runtime: Process price updates immediately using state lookups
 * 3. Output: Emit UnifiedMarketValue records to aggregation topic
 * 
 * Key Benefits:
 * - Price Consistency: Holdings and orders use identical prices for calculations
 * - Real-time: Immediate calculation on price updates (no windowing delays)
 * - Scalable: State partitioned by instrumentId for parallel processing
 * - Fault Tolerant: Checkpointed state for recovery
 */
public class UnifiedMarketValueJob {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedMarketValueJob.class);
    
    // Static ObjectMapper to avoid serialization issues
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public static void main(String[] args) throws Exception {
        
        // Configuration
        String kafkaBootstrapServers = getParameter(args, "kafka.bootstrap-servers", "localhost:9092");
        String consumerGroupId = getParameter(args, "consumer.group-id", "flink-unified-mv-keyed-state");
        
        log.info("🚀 Starting Unified Market Value Job with KeyedState");
        log.info("Kafka Bootstrap Servers: {}", kafkaBootstrapServers);
        log.info("Consumer Group ID: {}", consumerGroupId);
        
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Start with parallelism 1 for simplicity
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000); // 30 seconds
        
        // ==================== CREATE KAFKA SOURCES ====================
        
        // Holdings source (daily updates)
        KafkaSource<String> holdingsSource = createStaticKafkaSource(
            kafkaBootstrapServers, 
            "base.sod-holding", 
            consumerGroupId + "-holdings"
        );
        
        // Orders source (order events)
        KafkaSource<String> ordersSource = createStaticKafkaSource(
            kafkaBootstrapServers, 
            "base.order-events", 
            consumerGroupId + "-orders"
        );
        
        // Instruments source (static data)
        KafkaSource<String> instrumentsSource = createStaticKafkaSource(
            kafkaBootstrapServers, 
            "base.instrument", 
            consumerGroupId + "-instruments"
        );
        
        // Prices source (every 5 seconds)
        KafkaSource<String> pricesSource = createDynamicKafkaSource(
            kafkaBootstrapServers, 
            "base.price", 
            consumerGroupId + "-prices"
        );
        
        // ==================== CREATE DATA STREAMS ====================
        
        DataStream<SODHolding> holdingsStream = env
            .fromSource(holdingsSource, WatermarkStrategy.noWatermarks(), "Holdings Source")
            .map(new SODHoldingParseFunction())
            .filter(holding -> holding != null)
            .name("Parse Holdings");
        
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
        UnifiedMarketValueProcessor processor = new UnifiedMarketValueProcessor();
        
        // Convert all streams to a common format and union them
        // Holdings -> Tagged as "HOLDING"
        DataStream<String> holdingsTagged = holdingsStream
            .map(holding -> "HOLDING:" + UnifiedMarketValueJob.getObjectMapper().writeValueAsString(holding))
            .name("Tag Holdings");
        
        // Orders -> Tagged as "ORDER"
        DataStream<String> ordersTagged = ordersStream
            .map(order -> "ORDER:" + UnifiedMarketValueJob.getObjectMapper().writeValueAsString(order))
            .name("Tag Orders");
        
        // Instruments -> Tagged as "INSTRUMENT"  
        DataStream<String> instrumentsTagged = instrumentsStream
            .map(instrument -> "INSTRUMENT:" + UnifiedMarketValueJob.getObjectMapper().writeValueAsString(instrument))
            .name("Tag Instruments");
        
        // Prices -> Tagged as "PRICE"
        DataStream<String> pricesTagged = pricesStream
            .map(price -> "PRICE:" + UnifiedMarketValueJob.getObjectMapper().writeValueAsString(price))
            .name("Tag Prices");
        
        // Union all streams and process by instrumentId
        DataStream<UnifiedMarketValue> unifiedMVStream = holdingsTagged
            .union(ordersTagged, instrumentsTagged, pricesTagged)
            .keyBy(taggedData -> {
                // Extract instrumentId from tagged data
                try {
                    String[] parts = taggedData.split(":", 2);
                    String type = parts[0];
                    String json = parts[1];
                    
                    if ("HOLDING".equals(type)) {
                        SODHolding holding = UnifiedMarketValueJob.getObjectMapper().readValue(json, SODHolding.class);
                        return holding.getInstrumentId();
                    } else if ("ORDER".equals(type)) {
                        Order order = UnifiedMarketValueJob.getObjectMapper().readValue(json, Order.class);
                        return order.getInstrumentId();
                    } else if ("INSTRUMENT".equals(type)) {
                        Instrument instrument = UnifiedMarketValueJob.getObjectMapper().readValue(json, Instrument.class);
                        return instrument.getInstrumentId();
                    } else if ("PRICE".equals(type)) {
                        Price price = UnifiedMarketValueJob.getObjectMapper().readValue(json, Price.class);
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
        
        // Create Kafka sink for unified aggregation topic
        KafkaSink<UnifiedMarketValue> unifiedMVSink = KafkaSink.<UnifiedMarketValue>builder()
            .setBootstrapServers(kafkaBootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("aggregation.unified-mv")
                .setValueSerializationSchema(new UnifiedMarketValueKafkaSerializationSchema())
                .build())
            .build();
        
        // Sink the results
        unifiedMVStream.sinkTo(unifiedMVSink).name("Sink to Kafka");
        
        // Add logging for monitoring
        unifiedMVStream
            .map(unifiedMV -> {
                if (unifiedMV.isHolding()) {
                    log.info("🚀 HOLDING UPDATE: {} {} shares @ ${} = ${} USD (Account: {})", 
                            unifiedMV.getInstrumentName(),
                            unifiedMV.getPosition(),
                            unifiedMV.getPrice(),
                            unifiedMV.getMarketValueUSD(),
                            unifiedMV.getAccountId());
                } else if (unifiedMV.isOrder()) {
                    log.info("🚀 ORDER UPDATE: {} {} order for {} @ ${} = ${} USD (Filled: ${} USD) [{}]", 
                            unifiedMV.getOrderQuantity().compareTo(java.math.BigDecimal.ZERO) > 0 ? "BUY" : "SELL",
                            unifiedMV.getOrderQuantity().abs(),
                            unifiedMV.getInstrumentName(),
                            unifiedMV.getPrice(),
                            unifiedMV.getMarketValueUSD(),
                            unifiedMV.getFilledMarketValueUSD(),
                            unifiedMV.getOrderStatus());
                }
                return unifiedMV;
            })
            .name("Log Unified Market Value Updates");
        
        // ==================== EXECUTE JOB ====================
        
        log.info("🚀 Starting Unified Market Value Job with KeyedState approach...");
        log.info("📊 This job ensures PRICE CONSISTENCY between holdings and orders");
        log.info("📈 Output topic: aggregation.unified-mv");
        env.execute("Unified Market Value Job - KeyedState");
    }
    
    /**
     * Custom robust string deserializer that handles encoding issues gracefully
     */
    public static class RobustStringDeserializer implements DeserializationSchema<String> {
        
        private static final Logger log = LoggerFactory.getLogger(RobustStringDeserializer.class);
        
        @Override
        public String deserialize(byte[] message) throws IOException {
            try {
                // Try UTF-8 first
                String result = new String(message, StandardCharsets.UTF_8);
                
                // Basic validation - check if it looks like JSON
                if (result.trim().startsWith("{") && result.trim().endsWith("}")) {
                    return result;
                }
                
                // If not JSON-like, try other encodings
                log.warn("Message doesn't look like JSON, trying alternative encoding");
                return new String(message, StandardCharsets.ISO_8859_1);
                
            } catch (Exception e) {
                log.error("Failed to deserialize message: {}", e.getMessage());
                return "{}"; // Return empty JSON to avoid breaking the pipeline
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
     * Create a Kafka source for STATIC data with earliest offsets
     * Used for: holdings, orders, instruments (need all existing data)
     */
    private static KafkaSource<String> createStaticKafkaSource(String bootstrapServers, String topic, String groupId) {
        return KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.earliest()) // Read all existing static data
            .setValueOnlyDeserializer(new RobustStringDeserializer())
            .build();
    }
    
    /**
     * Create a Kafka source for DYNAMIC data with latest offsets
     * Used for: prices (only need current/new updates)
     */
    private static KafkaSource<String> createDynamicKafkaSource(String bootstrapServers, String topic, String groupId) {
        return KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.latest()) // Read only new dynamic data
            .setValueOnlyDeserializer(new RobustStringDeserializer())
            .build();
    }
    
    /**
     * Get parameter from command line args or use default
     */
    private static String getParameter(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--" + key)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
    
    /**
     * Get the shared ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
} 