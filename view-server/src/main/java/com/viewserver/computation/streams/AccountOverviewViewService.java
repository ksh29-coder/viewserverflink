package com.viewserver.computation.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.aggregation.model.UnifiedMarketValue;
import com.viewserver.computation.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka Streams service for Account Overview views.
 * Creates dynamic topologies for each view configuration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountOverviewViewService {
    
    private final ObjectMapper objectMapper;
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${account-overview.unified-mv-topic:aggregation.unified-mv}")
    private String unifiedMvTopic;
    
    // Active Kafka Streams instances per view
    private final Map<String, KafkaStreams> activeStreams = new ConcurrentHashMap<>();
    
    // View metadata tracking
    private final Map<String, ViewMetadata> viewMetadata = new ConcurrentHashMap<>();
    
    // Change listeners for WebSocket updates
    private final Map<String, ViewChangeListener> changeListeners = new ConcurrentHashMap<>();
    
    /**
     * Interface for receiving view change notifications
     */
    public interface ViewChangeListener {
        void onRowChange(String viewId, GridUpdate.RowChange rowChange);
        void onViewReady(String viewId, List<AccountOverviewResponse> initialData);
        void onError(String viewId, Exception error);
    }
    
    /**
     * Default implementation of ViewChangeListener that does nothing
     */
    private static class DefaultViewChangeListener implements ViewChangeListener {
        @Override
        public void onRowChange(String viewId, GridUpdate.RowChange rowChange) {
            // Default implementation - no action
        }
        
        @Override
        public void onViewReady(String viewId, List<AccountOverviewResponse> initialData) {
            // Default implementation - no action
        }
        
        @Override
        public void onError(String viewId, Exception error) {
            log.warn("View error for {}: {}", viewId, error.getMessage());
        }
    }
    
    /**
     * Create a new Account Overview view
     */
    public String createView(AccountOverviewRequest request) {
        return createView(request, new DefaultViewChangeListener());
    }
    
    /**
     * Create a new Account Overview view with custom listener
     */
    public String createView(AccountOverviewRequest request, ViewChangeListener listener) {
        String viewId = generateViewId();
        
        log.info("Creating Account Overview view: {} with config: {}", viewId, request);
        
        try {
            // Create view metadata
            ViewMetadata metadata = ViewMetadata.builder()
                    .viewId(viewId)
                    .userId(request.getUserId())
                    .viewName(request.getViewName())
                    .configuration(request)
                    .createdAt(LocalDateTime.now())
                    .status(ViewMetadata.ViewStatus.CREATING)
                    .activeConnections(0)
                    .build();
            
            viewMetadata.put(viewId, metadata);
            changeListeners.put(viewId, listener);
            
            // Create and start Kafka Streams topology
            KafkaStreams streams = createViewTopology(viewId, request);
            activeStreams.put(viewId, streams);
            
            // Start the streams
            streams.start();
            
            // Update status to active
            metadata.setStatus(ViewMetadata.ViewStatus.ACTIVE);
            
            log.info("Account Overview view created successfully: {}", viewId);
            return viewId;
            
        } catch (Exception e) {
            log.error("Failed to create Account Overview view: {}", e.getMessage(), e);
            cleanupView(viewId);
            throw new RuntimeException("Failed to create view: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create Kafka Streams topology for a specific view
     */
    private KafkaStreams createViewTopology(String viewId, AccountOverviewRequest request) {
        StreamsBuilder builder = new StreamsBuilder();
        
        // Use consistent state store name for aggregation and querying
        String stateStoreName = "account-overview-store-" + viewId;
        
        // Stream from unified market value topic
        KStream<String, String> unifiedMvStream = builder.stream(unifiedMvTopic);
        
        // Parse and filter unified market value records
        KStream<String, UnifiedMarketValue> parsedStream = unifiedMvStream
                .mapValues(this::parseUnifiedMV)
                .filter((key, value) -> {
                    boolean isNotNull = value != null;
                    if (!isNotNull) {
                        log.debug("Filtered out null UnifiedMV for key: {}", key);
                    }
                    return isNotNull;
                })
                .filter((key, value) -> {
                    boolean accountMatch = request.getSelectedAccounts().contains(value.getAccountId());
                    if (!accountMatch) {
                        log.debug("Filtered out UnifiedMV for account {} (not in selected accounts: {})", 
                                value.getAccountId(), request.getSelectedAccounts());
                    }
                    return accountMatch;
                })
                .filter((key, value) -> {
                    boolean hasValidPrice = value.hasValidPrice();
                    if (!hasValidPrice) {
                        log.debug("Filtered out UnifiedMV for {} - {} (invalid price: {})", 
                                value.getAccountId(), value.getInstrumentId(), value.getPrice());
                    } else {
                        log.debug("Accepted UnifiedMV for {} - {} (price: {})", 
                                value.getAccountId(), value.getInstrumentId(), value.getPrice());
                    }
                    return hasValidPrice;
                });
        
        // Group by dynamic grouping fields
        KGroupedStream<String, UnifiedMarketValue> groupedStream = parsedStream
                .groupBy((key, value) -> generateGroupKey(value, request.getGroupByFields()),
                        Grouped.with(Serdes.String(), createUnifiedMVSerde()));
        
        // Aggregate into account overview responses with proper serde and consistent store name
        KTable<String, AccountOverviewResponse> aggregatedTable = groupedStream
                .aggregate(
                        () -> createEmptyResponse(viewId),
                        this::aggregateUnifiedMV,
                        Materialized.<String, AccountOverviewResponse, KeyValueStore<Bytes, byte[]>>as(stateStoreName)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(createAccountOverviewResponseSerde())
                );
        
        // Detect changes and send to WebSocket
        aggregatedTable.toStream()
                .foreach((rowKey, response) -> {
                    if (response != null && response.hasAnyNavValue()) {
                        notifyRowChange(viewId, rowKey, response);
                    }
                });
        
        // Create Kafka Streams configuration
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "account-overview-view-" + viewId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000); // 1 second commits
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 10 * 1024 * 1024); // 10MB cache
        
        return new KafkaStreams(builder.build(), props);
    }
    
    /**
     * Parse UnifiedMarketValue from JSON
     */
    private UnifiedMarketValue parseUnifiedMV(String json) {
        try {
            UnifiedMarketValue result = objectMapper.readValue(json, UnifiedMarketValue.class);
            log.debug("Parsed UnifiedMV: {} - {} - {}", result.getRecordType(), result.getAccountId(), result.getInstrumentId());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse UnifiedMarketValue: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate grouping key based on dynamic fields
     */
    private String generateGroupKey(UnifiedMarketValue value, List<String> groupByFields) {
        List<String> keyParts = new ArrayList<>();
        
        for (String field : groupByFields) {
            String fieldValue = getFieldValue(value, field);
            keyParts.add(fieldValue != null ? fieldValue : "null");
        }
        
        return String.join("#", keyParts);
    }
    
    /**
     * Get field value from UnifiedMarketValue using reflection-like approach
     */
    private String getFieldValue(UnifiedMarketValue value, String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "accountname":
            case "accountid":
                return value.getAccountId(); // Assuming we'll enrich with account name
            case "instrumentname":
                return value.getInstrumentName();
            case "instrumenttype":
                return value.getInstrumentType();
            case "currency":
                return value.getCurrency();
            case "countryofrisk":
                return value.getCountryOfRisk();
            case "countryofdomicile":
                return value.getCountryOfDomicile();
            case "sector":
                return value.getSector();
            case "orderstatus":
                return value.isOrder() ? value.getOrderStatus() : null;
            case "ordertype":
                return value.isOrder() ? value.getOrderType() : null;
            case "venue":
                return value.isOrder() ? value.getVenue() : null;
            default:
                log.warn("Unknown grouping field: {}", fieldName);
                return "unknown";
        }
    }
    
    /**
     * Create empty response for aggregation initialization
     */
    private AccountOverviewResponse createEmptyResponse(String viewId) {
        return AccountOverviewResponse.builder()
                .viewId(viewId)
                .groupingFields(new HashMap<>())
                .sodNavUSD(BigDecimal.ZERO)
                .currentNavUSD(BigDecimal.ZERO)
                .expectedNavUSD(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .recordCount(0)
                .build();
    }
    
    /**
     * Aggregate UnifiedMarketValue into AccountOverviewResponse
     */
    private AccountOverviewResponse aggregateUnifiedMV(String key, UnifiedMarketValue value, AccountOverviewResponse current) {
        log.debug("Aggregating UnifiedMV for key {}: {} - {} - MV: {}", key, value.getRecordType(), value.getInstrumentId(), value.getMarketValueUSD());
        
        if (current == null) {
            current = createEmptyResponse(value.getAccountId()); // Use accountId as fallback viewId
        }
        
        // Update grouping fields
        Map<String, String> groupingFields = new HashMap<>(current.getGroupingFields());
        groupingFields.put("accountName", value.getAccountId()); // TODO: Enrich with actual account name
        groupingFields.put("instrumentName", value.getInstrumentName());
        
        // Aggregate market values based on record type
        BigDecimal sodNav = current.getSodNavUSD() != null ? current.getSodNavUSD() : BigDecimal.ZERO;
        BigDecimal currentNav = current.getCurrentNavUSD() != null ? current.getCurrentNavUSD() : BigDecimal.ZERO;
        BigDecimal expectedNav = current.getExpectedNavUSD() != null ? current.getExpectedNavUSD() : BigDecimal.ZERO;
        
        if (value.isHolding()) {
            // Holdings contribute to SOD NAV
            sodNav = sodNav.add(value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO);
            currentNav = currentNav.add(value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO);
            expectedNav = expectedNav.add(value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO);
        } else if (value.isOrder()) {
            // Orders contribute to current (filled) and expected (full order) NAV
            BigDecimal filledValue = value.getFilledMarketValueUSD() != null ? value.getFilledMarketValueUSD() : BigDecimal.ZERO;
            BigDecimal orderValue = value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO;
            
            currentNav = currentNav.add(filledValue);
            expectedNav = expectedNav.add(orderValue);
        }
        
        AccountOverviewResponse result = current.toBuilder()
                .rowKey(key)
                .groupingFields(groupingFields)
                .sodNavUSD(sodNav)
                .currentNavUSD(currentNav)
                .expectedNavUSD(expectedNav)
                .lastUpdated(LocalDateTime.now())
                .recordCount(current.getRecordCount() + 1)
                .build();
        
        log.debug("Aggregation result for key {}: SOD={}, Current={}, Expected={}, Count={}", 
                key, result.getSodNavUSD(), result.getCurrentNavUSD(), result.getExpectedNavUSD(), result.getRecordCount());
        
        return result;
    }
    
    /**
     * Notify change listener about row changes
     */
    private void notifyRowChange(String viewId, String rowKey, AccountOverviewResponse response) {
        ViewChangeListener listener = changeListeners.get(viewId);
        if (listener != null) {
            try {
                GridUpdate.RowChange rowChange = GridUpdate.insertRow(rowKey, response);
                listener.onRowChange(viewId, rowChange);
            } catch (Exception e) {
                log.error("Error notifying row change for view {}: {}", viewId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Generate unique view ID
     */
    private String generateViewId() {
        return "view_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Create Serde for UnifiedMarketValue
     */
    private Serde<UnifiedMarketValue> createUnifiedMVSerde() {
        return Serdes.serdeFrom(
                (topic, data) -> {
                    try {
                        return objectMapper.writeValueAsBytes(data);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (topic, data) -> {
                    try {
                        return objectMapper.readValue(data, UnifiedMarketValue.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }
    
    /**
     * Create Serde for AccountOverviewResponse
     */
    private Serde<AccountOverviewResponse> createAccountOverviewResponseSerde() {
        return Serdes.serdeFrom(
                (topic, data) -> {
                    try {
                        return objectMapper.writeValueAsBytes(data);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (topic, data) -> {
                    try {
                        return objectMapper.readValue(data, AccountOverviewResponse.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }
    
    /**
     * Destroy a view and clean up resources
     */
    public void destroyView(String viewId) {
        log.info("Destroying Account Overview view: {}", viewId);
        cleanupView(viewId);
    }
    
    /**
     * Clean up view resources
     */
    private void cleanupView(String viewId) {
        // Stop Kafka Streams
        KafkaStreams streams = activeStreams.remove(viewId);
        if (streams != null) {
            streams.close(Duration.ofSeconds(10));
        }
        
        // Remove metadata and listeners
        ViewMetadata metadata = viewMetadata.remove(viewId);
        if (metadata != null) {
            metadata.setStatus(ViewMetadata.ViewStatus.DESTROYED);
        }
        
        changeListeners.remove(viewId);
        
        log.info("View cleanup completed: {}", viewId);
    }
    
    /**
     * Get view metadata
     */
    public ViewMetadata getViewMetadata(String viewId) {
        return viewMetadata.get(viewId);
    }
    
    /**
     * Get all active views
     */
    public Collection<ViewMetadata> getAllActiveViews() {
        return viewMetadata.values();
    }
    
    /**
     * Add connection to view
     */
    public void addConnection(String viewId) {
        ViewMetadata metadata = viewMetadata.get(viewId);
        if (metadata != null) {
            metadata.addConnection();
        }
    }
    
    /**
     * Remove connection from view
     */
    public void removeConnection(String viewId) {
        ViewMetadata metadata = viewMetadata.get(viewId);
        if (metadata != null) {
            metadata.removeConnection();
        }
    }
    
    /**
     * Cleanup on service shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AccountOverviewViewService...");
        
        for (String viewId : new ArrayList<>(activeStreams.keySet())) {
            cleanupView(viewId);
        }
        
        log.info("AccountOverviewViewService shutdown completed");
    }
    
    /**
     * Get current view data for initial WebSocket load
     */
    public List<AccountOverviewResponse> getCurrentViewData(String viewId) {
        log.debug("Getting current view data for: {}", viewId);
        
        KafkaStreams streams = activeStreams.get(viewId);
        if (streams == null) {
            log.warn("No Kafka Streams found for view: {}", viewId);
            return new ArrayList<>();
        }
        
        KafkaStreams.State state = streams.state();
        log.debug("Kafka Streams state for view {}: {}", viewId, state);
        
        if (state != KafkaStreams.State.RUNNING) {
            log.warn("View {} is not running (state: {}), returning empty data", viewId, state);
            return new ArrayList<>();
        }
        
        try {
            String stateStoreName = "account-overview-store-" + viewId;
            log.debug("Querying state store: {}", stateStoreName);
            
            ReadOnlyKeyValueStore<String, AccountOverviewResponse> store = 
                streams.store(StoreQueryParameters.fromNameAndType(stateStoreName, QueryableStoreTypes.keyValueStore()));
            
            List<AccountOverviewResponse> results = new ArrayList<>();
            KeyValueIterator<String, AccountOverviewResponse> iterator = store.all();
            
            int count = 0;
            while (iterator.hasNext()) {
                KeyValue<String, AccountOverviewResponse> entry = iterator.next();
                count++;
                log.debug("Store entry {}: key={}, value={}", count, entry.key, entry.value != null ? "present" : "null");
                
                if (entry.value != null) {
                    results.add(entry.value);
                }
            }
            
            iterator.close();
            log.info("Retrieved {} rows from {} total entries for view {}", results.size(), count, viewId);
            return results;
            
        } catch (Exception e) {
            log.error("Error querying view data for {}: {}", viewId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Compute current view state by querying Redis cache
     */
    private List<AccountOverviewResponse> computeCurrentViewState(String viewId, ViewConfiguration config) {
        log.debug("Computing current state for view: {}", viewId);
        
        Set<UnifiedMarketValue> allUnifiedMV = cacheService.getAllLatestUnifiedMV(); // Use latest prices only
        Map<String, AccountOverviewResponse> aggregations = new HashMap<>();
        
        // Implementation of computeCurrentViewState method
        // This method should return a list of AccountOverviewResponse based on the current state of the view
        // and the configuration provided.
        
        return new ArrayList<>(); // Placeholder return, actual implementation needed
    }
} 