package com.viewserver.computation.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.aggregation.model.UnifiedMarketValue;
import com.viewserver.computation.model.*;
import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account Overview View Service using Redis Cache pattern.
 * 
 * This service creates logical views by querying the Redis cache populated by UnifiedMVConsumer
 * and provides instant access to current unified-mv data.
 * 
 * Key Benefits:
 * - Instant view creation with current state from Redis
 * - No Kafka Streams topology conflicts
 * - Leverages existing caching infrastructure
 * - Fast random access to data
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountOverviewViewService {
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    private CacheService cacheService;
    
    // Active view configurations (no Kafka Streams instances)
    private final Map<String, ViewConfiguration> activeViews = new ConcurrentHashMap<>();
    
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
    
    @PostConstruct
    public void initialize() {
        log.info("✅ AccountOverviewViewService initialized with Redis Cache");
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
            // Validate cache is ready (has data)
            if (cacheService.getAllUnifiedMV().isEmpty()) {
                throw new RuntimeException("Cache is not ready yet - no unified-mv data available");
            }
            
            // Create view configuration (no Kafka Streams topology)
            ViewConfiguration config = ViewConfiguration.builder()
                    .viewId(viewId)
                    .request(request)
                    .createdAt(LocalDateTime.now())
                    .build();
            
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
            
            // Store view configuration and metadata
            activeViews.put(viewId, config);
            viewMetadata.put(viewId, metadata);
            changeListeners.put(viewId, listener);
            
            // 🚀 INSTANTLY compute current state from Redis cache
            List<AccountOverviewResponse> initialData = computeCurrentViewState(viewId, config);
            
            // Update metadata
            metadata.setStatus(ViewMetadata.ViewStatus.ACTIVE);
            metadata.setRowCount(initialData.size());
            metadata.setLastDataUpdate(LocalDateTime.now());
            
            // Notify listener of initial data
            listener.onViewReady(viewId, initialData);
            
            log.info("✅ Account Overview view created successfully: {} with {} rows", viewId, initialData.size());
            return viewId;
            
        } catch (Exception e) {
            log.error("❌ Failed to create Account Overview view: {}", e.getMessage(), e);
            cleanupView(viewId);
            throw new RuntimeException("Failed to create view: " + e.getMessage(), e);
        }
    }
    
    /**
     * Compute current view state by querying Redis cache
     */
    private List<AccountOverviewResponse> computeCurrentViewState(String viewId, ViewConfiguration config) {
        log.debug("Computing current state for view: {}", viewId);
        
        Set<UnifiedMarketValue> allUnifiedMV = cacheService.getAllUnifiedMV();
        Map<String, AccountOverviewResponse> aggregations = new HashMap<>();
        
        long recordsProcessed = 0;
        long recordsMatched = 0;
        
        // Process ALL current records in the Redis cache
        for (UnifiedMarketValue umv : allUnifiedMV) {
            recordsProcessed++;
            
            // Apply view-specific filters
            if (!isRelevantToView(umv, config)) {
                continue;
            }
            recordsMatched++;
            
            // Generate grouping key based on view configuration
            String groupKey = generateGroupKey(umv, config.getRequest().getGroupByFields());
            
            // Aggregate into view response
            AccountOverviewResponse current = aggregations.computeIfAbsent(
                groupKey, 
                k -> createEmptyResponse(viewId)
            );
            
            AccountOverviewResponse updated = aggregateUnifiedMV(groupKey, umv, current);
            aggregations.put(groupKey, updated);
        }
        
        log.info("Computed {} aggregated rows from {} total records ({} matched filters) for view: {}", 
                 aggregations.size(), recordsProcessed, recordsMatched, viewId);
        
        return new ArrayList<>(aggregations.values());
    }
    
    /**
     * Check if a UnifiedMarketValue record is relevant to a view
     */
    private boolean isRelevantToView(UnifiedMarketValue umv, ViewConfiguration config) {
        AccountOverviewRequest request = config.getRequest();
        
        return request.getSelectedAccounts().contains(umv.getAccountId()) 
            && umv.hasValidPrice();
    }
    
    /**
     * Compute aggregation for a specific group within a view
     */
    private AccountOverviewResponse computeGroupAggregation(String viewId, ViewConfiguration config, String targetGroupKey) {
        Set<UnifiedMarketValue> allUnifiedMV = cacheService.getAllUnifiedMV();
        AccountOverviewResponse aggregation = createEmptyResponse(viewId);
        
        // Process all records and filter for this specific group
        for (UnifiedMarketValue umv : allUnifiedMV) {
            // Only process records relevant to this view
            if (!isRelevantToView(umv, config)) {
                continue;
            }
            
            // Only process records that belong to the target group
            String recordGroupKey = generateGroupKey(umv, config.getRequest().getGroupByFields());
            if (targetGroupKey.equals(recordGroupKey)) {
                aggregation = aggregateUnifiedMV(recordGroupKey, umv, aggregation);
            }
        }
        
        return aggregation;
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
            // Holdings contribute to SOD and Current NAV
            BigDecimal holdingValue = value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO;
            sodNav = sodNav.add(holdingValue);
            currentNav = currentNav.add(holdingValue);
            expectedNav = expectedNav.add(holdingValue);
        } else if (value.isOrder()) {
            // Orders contribute to Expected NAV
            BigDecimal orderValue = value.getMarketValueUSD() != null ? value.getMarketValueUSD() : BigDecimal.ZERO;
            expectedNav = expectedNav.add(orderValue);
            
            // Filled portion contributes to Current NAV
            BigDecimal filledValue = value.getFilledMarketValueUSD() != null ? value.getFilledMarketValueUSD() : BigDecimal.ZERO;
            currentNav = currentNav.add(filledValue);
        }
        
        return AccountOverviewResponse.builder()
                .viewId(current.getViewId())
                .rowKey(key)
                .groupingFields(groupingFields)
                .sodNavUSD(sodNav)
                .currentNavUSD(currentNav)
                .expectedNavUSD(expectedNav)
                .lastUpdated(LocalDateTime.now())
                .recordCount(current.getRecordCount() + 1)
                .build();
    }
    
    /**
     * Get current view data (for WebSocket initial load)
     */
    public List<AccountOverviewResponse> getCurrentViewData(String viewId) {
        log.debug("Getting current view data for: {}", viewId);
        
        ViewConfiguration config = activeViews.get(viewId);
        if (config == null) {
            log.warn("No view configuration found for: {}", viewId);
            return new ArrayList<>();
        }
        
        if (cacheService.getAllUnifiedMV().isEmpty()) {
            log.warn("Cache is empty, returning empty data for view: {}", viewId);
            return new ArrayList<>();
        }
        
        return computeCurrentViewState(viewId, config);
    }
    
    /**
     * Destroy a view and clean up resources
     */
    public void destroyView(String viewId) {
        log.info("Destroying view: {}", viewId);
        cleanupView(viewId);
    }
    
    /**
     * Clean up view resources
     */
    private void cleanupView(String viewId) {
        activeViews.remove(viewId);
        changeListeners.remove(viewId);
        
        ViewMetadata metadata = viewMetadata.remove(viewId);
        if (metadata != null) {
            metadata.setStatus(ViewMetadata.ViewStatus.DESTROYED);
        }
        
        log.debug("Cleaned up view: {}", viewId);
    }
    
    /**
     * Get view metadata
     */
    public ViewMetadata getViewMetadata(String viewId) {
        return viewMetadata.get(viewId);
    }
    
    /**
     * Get all active view IDs
     */
    public Set<String> getActiveViewIds() {
        return new HashSet<>(activeViews.keySet());
    }
    
    /**
     * Add connection to view and register WebSocket listener
     */
    public void addConnection(String viewId) {
        ViewMetadata metadata = viewMetadata.get(viewId);
        if (metadata != null) {
            metadata.addConnection();
        }
    }
    
    /**
     * Add connection to view with WebSocket listener registration
     */
    public void addConnection(String viewId, ViewChangeListener listener) {
        ViewMetadata metadata = viewMetadata.get(viewId);
        if (metadata != null) {
            metadata.addConnection();
        }
        
        // Register the WebSocket handler as a listener for real-time updates
        if (listener != null) {
            changeListeners.put(viewId, listener);
            log.debug("Registered WebSocket listener for view: {}", viewId);
        }
    }
    
    /**
     * Remove connection from view
     */
    public void removeConnection(String viewId) {
        ViewMetadata metadata = viewMetadata.get(viewId);
        if (metadata != null) {
            metadata.removeConnection();
            
            // If no more connections, we could keep the listener for a grace period
            // or remove it immediately - for now keeping it for potential reconnections
            log.debug("Removed connection from view: {}, remaining connections: {}", 
                     viewId, metadata.getActiveConnections());
        }
    }
    
    /**
     * Generate unique view ID
     */
    private String generateViewId() {
        return "view_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    // ==================== Real-time Update Methods ====================
    
    /**
     * Handle UnifiedMV update from Kafka consumer
     * This triggers incremental updates to affected views
     */
    public void onUnifiedMVUpdate(UnifiedMarketValue unifiedMV) {
        log.debug("Processing UnifiedMV update: {} {} for account {}", 
                 unifiedMV.getRecordType(), unifiedMV.getInstrumentId(), unifiedMV.getAccountId());
        
        if (activeViews.isEmpty()) {
            log.debug("No active views, skipping UnifiedMV update processing");
            return;
        }
        
        // Process each active view to see if this update affects it
        for (Map.Entry<String, ViewConfiguration> entry : activeViews.entrySet()) {
            String viewId = entry.getKey();
            ViewConfiguration config = entry.getValue();
            
            try {
                processUnifiedMVUpdateForView(viewId, config, unifiedMV);
            } catch (Exception e) {
                log.error("Error processing UnifiedMV update for view {}: {}", viewId, e.getMessage(), e);
                
                // Notify view of error
                ViewChangeListener listener = changeListeners.get(viewId);
                if (listener != null) {
                    listener.onError(viewId, e);
                }
            }
        }
    }
    
    /**
     * Process UnifiedMV update for a specific view
     */
    private void processUnifiedMVUpdateForView(String viewId, ViewConfiguration config, UnifiedMarketValue unifiedMV) {
        // Check if this UnifiedMV update is relevant to the view
        if (!isRelevantToView(unifiedMV, config)) {
            log.debug("UnifiedMV update not relevant to view {}: account {} not selected", 
                     viewId, unifiedMV.getAccountId());
            return;
        }
        
        log.debug("Processing relevant UnifiedMV update for view {}: {} {} for account {}", 
                 viewId, unifiedMV.getRecordType(), unifiedMV.getInstrumentId(), unifiedMV.getAccountId());
        
        // Get the listener for this view
        ViewChangeListener listener = changeListeners.get(viewId);
        if (listener == null) {
            log.warn("No listener found for view {}", viewId);
            return;
        }
        
        try {
            // Compute the affected group aggregation
            String groupKey = generateGroupKey(unifiedMV, config.getRequest().getGroupByFields());
            AccountOverviewResponse updatedAggregation = computeGroupAggregation(viewId, config, groupKey);
            
            // Create row change notification
            GridUpdate.RowChange rowChange = GridUpdate.RowChange.builder()
                    .changeType(GridUpdate.RowChange.ChangeType.UPDATE)
                    .rowKey(groupKey)
                    .completeRow(updatedAggregation)
                    .build();
            
            // Send update to WebSocket
            listener.onRowChange(viewId, rowChange);
            
            // Update view metadata
            ViewMetadata metadata = viewMetadata.get(viewId);
            if (metadata != null) {
                metadata.setLastDataUpdate(LocalDateTime.now());
            }
            
            log.debug("Sent incremental update to view {} for group {}: ${} USD", 
                     viewId, groupKey, updatedAggregation.getCurrentNavUSD());
            
        } catch (Exception e) {
            log.error("Error computing incremental update for view {}: {}", viewId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Handle price updates that affect all portfolio values
     * This triggers a refresh notification to views rather than incremental updates
     * since price changes can affect many aggregations
     */
    public void notifyViewsOfPriceUpdate() {
        if (activeViews.isEmpty()) {
            log.debug("No active views, skipping price update notification");
            return;
        }
        
        log.debug("Notifying {} active views of price update", activeViews.size());
        
        // For price updates, we'll send a refresh signal rather than trying to compute
        // incremental updates since prices can affect many rows
        for (String viewId : activeViews.keySet()) {
            try {
                ViewChangeListener listener = changeListeners.get(viewId);
                if (listener != null) {
                    // Send a special message type for price refresh
                    // The WebSocket handler can decide whether to do full refresh or ignore
                    log.debug("Notifying view {} of price update", viewId);
                }
                
                // Update view metadata
                ViewMetadata metadata = viewMetadata.get(viewId);
                if (metadata != null) {
                    metadata.setLastDataUpdate(LocalDateTime.now());
                }
                
            } catch (Exception e) {
                log.error("Error notifying view {} of price update: {}", viewId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Cleanup on service shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down AccountOverviewViewService...");
        
        // Clean up all views
        for (String viewId : new ArrayList<>(activeViews.keySet())) {
            cleanupView(viewId);
        }
        
        log.info("✅ AccountOverviewViewService shutdown completed");
    }
} 