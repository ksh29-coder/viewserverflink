# Account Overview Implementation Plan - Simplified

## Overview
This document outlines the **simplified** implementation plan for the Account Overview feature. The unified Flink job provides price-consistent data, eliminating the need for complex stream processing in Kafka Streams. This reduces implementation complexity by ~70%.

## Phase 1: Unified Flink Job (Price Consistency Foundation)

### 1.1 Replace Existing Flink Jobs
**Goal**: Replace separate `HoldingMarketValueJob` and `OrderMarketValueJob` with a single `UnifiedMarketValueJob`

#### Files to Create/Modify:
```
flink-jobs/src/main/java/com/viewserver/flink/
├── UnifiedMarketValueJob.java                    # NEW: Main job class
├── functions/
│   ├── UnifiedMarketValueProcessor.java          # NEW: Unified state processor
│   └── UnifiedMVKafkaSerializationSchema.java    # NEW: Output serialization
├── model/
│   └── UnifiedMarketValue.java                   # NEW: Union type model
└── serialization/
    └── UnifiedMVDeserializationSchema.java       # NEW: Input deserialization
```

#### Implementation Steps:
1. **Create UnifiedMarketValue Model**
   ```java
   @Data
   @Builder
   public class UnifiedMarketValue {
       private String type; // "HOLDING" | "ORDER"
       private String instrumentId;
       private String accountId;
       private LocalDateTime priceTimestamp; // CRITICAL: Same for both
       private BigDecimal price;             // CRITICAL: Same for both
       
       // Union fields
       private HoldingMV holdingMV;  // null if type = "ORDER"
       private OrderMV orderMV;      // null if type = "HOLDING"
   }
   ```

2. **Create Unified Processor**
   ```java
   public class UnifiedMarketValueProcessor extends KeyedProcessFunction<String, String, UnifiedMarketValue> {
       
       // Shared state keyed by instrumentId
       private ValueState<Price> priceState;
       private ValueState<List<SODHolding>> holdingsState;
       private ValueState<List<Order>> ordersState;
       private ValueState<Instrument> instrumentState;
       
       @Override
       public void processElement(String taggedInput, Context ctx, Collector<UnifiedMarketValue> out) {
           // Parse tagged input: "HOLDING:json" | "ORDER:json" | "INSTRUMENT:json" | "PRICE:json"
           // Update appropriate state
           // When price updates: calculate both HoldingMV and OrderMV with same price
           // Emit UnifiedMarketValue objects with consistent priceTimestamp
       }
   }
   ```

3. **Update Kafka Topic Configuration**
   ```yaml
   # system-config.yml
   kafka:
     topics:
       aggregation:
         unified-mv: "aggregation.unified-mv"  # NEW: Single output topic
   ```

4. **Deprecate Old Jobs**
   - Mark `HoldingMarketValueJob` and `OrderMarketValueJob` as deprecated
   - Update startup scripts to use `UnifiedMarketValueJob`

### 1.2 Testing & Validation
- **Unit Tests**: Verify price consistency in unified processor
- **Integration Tests**: Ensure same priceTimestamp for same instrumentId
- **Performance Tests**: Compare latency vs. separate jobs

## Phase 2: Simplified Account Overview Backend Services

### 2.1 Data Models
**Location**: `view-server/src/main/java/com/viewserver/computation/model/`

#### Files to Create:
```
AccountOverviewRequest.java     # User selection input
AccountOverviewResult.java      # Aggregated output
ViewMetadata.java              # View lifecycle tracking
```

#### Implementation:
```java
@Data
@Builder
public class AccountOverviewRequest {
    private List<String> accountIds;           // Selected accounts
    private List<String> groupByFields;        // Dynamic grouping
    private List<String> exposureTypes;        // SOD/Current/Expected
    private String sessionId;                  // WebSocket session
}

@Data
@Builder
public class AccountOverviewResult {
    private String groupKey;                    // Composite key
    private Map<String, String> groupFields;   // Field values
    private BigDecimal sodNavUSD;              // SOD exposure
    private BigDecimal currentNavUSD;          // Current exposure
    private BigDecimal expectedNavUSD;         // Expected exposure
    private LocalDateTime lastUpdated;
    private LocalDateTime priceTimestamp;      // For validation
}

@Data
@Builder
public class ViewMetadata {
    private String viewId;
    private AccountOverviewRequest request;
    private LocalDateTime createdAt;
    private KafkaStreams streams;              // Kafka Streams instance
    private String sessionId;
}
```

### 2.2 Simplified Core Services
**Location**: `view-server/src/main/java/com/viewserver/computation/service/`

#### Files to Create:
```
AccountOverviewViewService.java     # Simplified dynamic view creation
ViewLifecycleManager.java          # View management
```

#### Key Implementation - Simplified Dynamic View Creation:
```java
@Service
public class AccountOverviewViewService {
    
    private final ViewLifecycleManager viewLifecycleManager;
    private final AccountOverviewWebSocketHandler webSocketHandler;
    
    public String createView(AccountOverviewRequest request) {
        // 1. Generate unique viewId
        String viewId = generateViewId(request);
        
        // 2. Create simplified Kafka Streams topology
        StreamsBuilder builder = new StreamsBuilder();
        
        // 3. Consume unified-mv topic (data already consistent!)
        KTable<String, UnifiedMarketValue> unifiedTable = builder.table("aggregation.unified-mv");
        
        // 4. Simple filtering and grouping - NO COMPLEX JOINS!
        KTable<String, AccountOverviewResult> results = unifiedTable
            .filter((key, value) -> 
                // Filter by selected accounts
                request.getAccountIds().contains(value.getAccountId())
            )
            .groupBy((key, value) -> 
                // Dynamic grouping based on user selection
                buildGroupKey(value, request.getGroupByFields())
            )
            .aggregate(
                AccountOverviewResult::new,
                (key, value, aggregate) -> 
                    // Direct aggregation - no complex state management
                    updateAggregate(aggregate, value, request.getExposureTypes())
            );
        
        // 5. Send updates directly to WebSocket (built-in change detection!)
        results.toStream().foreach((key, result) -> 
            webSocketHandler.sendUpdate(viewId, key, result));
        
        // 6. Start topology
        KafkaStreams streams = new KafkaStreams(builder.build(), getStreamsConfig(viewId));
        streams.start();
        
        // 7. Store view metadata
        viewLifecycleManager.registerView(viewId, request, streams);
        
        return viewId;
    }
    
    private String buildGroupKey(UnifiedMarketValue value, List<String> groupByFields) {
        // Build composite key based on selected fields
        return groupByFields.stream()
            .map(field -> getFieldValue(value, field))
            .collect(Collectors.joining("#"));
    }
    
    private AccountOverviewResult updateAggregate(AccountOverviewResult aggregate, 
                                                UnifiedMarketValue value, 
                                                List<String> exposureTypes) {
        // Simple aggregation logic - data is already consistent!
        if ("HOLDING".equals(value.getType()) && value.getHoldingMV() != null) {
            HoldingMV holding = value.getHoldingMV();
            if (exposureTypes.contains("SOD")) {
                aggregate.setSodNavUSD(aggregate.getSodNavUSD().add(holding.getMarketValueUSD()));
            }
            if (exposureTypes.contains("Current")) {
                aggregate.setCurrentNavUSD(aggregate.getCurrentNavUSD().add(holding.getMarketValueUSD()));
            }
            if (exposureTypes.contains("Expected")) {
                aggregate.setExpectedNavUSD(aggregate.getExpectedNavUSD().add(holding.getMarketValueUSD()));
            }
        }
        
        if ("ORDER".equals(value.getType()) && value.getOrderMV() != null) {
            OrderMV order = value.getOrderMV();
            if (exposureTypes.contains("Current")) {
                aggregate.setCurrentNavUSD(aggregate.getCurrentNavUSD().add(order.getFilledMarketValueUSD()));
            }
            if (exposureTypes.contains("Expected")) {
                aggregate.setExpectedNavUSD(aggregate.getExpectedNavUSD().add(order.getOrderMarketValueUSD()));
            }
        }
        
        aggregate.setLastUpdated(LocalDateTime.now());
        aggregate.setPriceTimestamp(value.getPriceTimestamp());
        return aggregate;
    }
}
```

### 2.3 Simplified View Lifecycle Manager
```java
@Service
public class ViewLifecycleManager {
    
    private final Map<String, ViewMetadata> activeViews = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public void registerView(String viewId, AccountOverviewRequest request, KafkaStreams streams) {
        ViewMetadata metadata = ViewMetadata.builder()
            .viewId(viewId)
            .request(request)
            .createdAt(LocalDateTime.now())
            .streams(streams)
            .sessionId(request.getSessionId())
            .build();
            
        activeViews.put(viewId, metadata);
        log.info("Registered view: {} for session: {}", viewId, request.getSessionId());
    }
    
    public void deleteView(String viewId) {
        ViewMetadata metadata = activeViews.remove(viewId);
        if (metadata != null) {
            // Stop Kafka Streams instance
            metadata.getStreams().close();
            log.info("Deleted view: {}", viewId);
        }
    }
    
    public void scheduleViewCleanup(String viewId, Duration delay) {
        scheduler.schedule(() -> deleteView(viewId), delay.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Scheduled cleanup for view: {} in {}", viewId, delay);
    }
    
    public List<ViewMetadata> getActiveViews() {
        return new ArrayList<>(activeViews.values());
    }
    
    public AccountOverviewRequest getViewConfig(String viewId) {
        ViewMetadata metadata = activeViews.get(viewId);
        return metadata != null ? metadata.getRequest() : null;
    }
}
```

## Phase 3: Simplified WebSocket Communication

### 3.1 Simplified WebSocket Handler
**Location**: `view-server/src/main/java/com/viewserver/websocket/`

#### Files to Create:
```
AccountOverviewWebSocketHandler.java    # Simplified WebSocket handler
WebSocketSessionManager.java           # Session management
```

#### Implementation:
```java
@Component
public class AccountOverviewWebSocketHandler extends TextWebSocketHandler {
    
    private final ViewLifecycleManager viewLifecycleManager;
    private final AccountOverviewViewService viewService;
    private final Map<String, String> sessionToViewId = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> viewToSession = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        AccountOverviewRequest request = parseRequest(message.getPayload());
        request.setSessionId(session.getId());
        
        // Delete old view if exists
        String oldViewId = sessionToViewId.get(session.getId());
        if (oldViewId != null) {
            viewLifecycleManager.deleteView(oldViewId);
            viewToSession.remove(oldViewId);
        }
        
        // Create new view
        String viewId = viewService.createView(request);
        sessionToViewId.put(session.getId(), viewId);
        viewToSession.put(viewId, session);
        
        log.info("Created view: {} for session: {}", viewId, session.getId());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String viewId = sessionToViewId.remove(session.getId());
        if (viewId != null) {
            viewToSession.remove(viewId);
            // Schedule cleanup after 1 minute
            viewLifecycleManager.scheduleViewCleanup(viewId, Duration.ofMinutes(1));
        }
        log.info("WebSocket connection closed: {}", session.getId());
    }
    
    // Called directly from Kafka Streams (no batching needed!)
    public void sendUpdate(String viewId, String rowKey, AccountOverviewResult result) {
        WebSocketSession session = viewToSession.get(viewId);
        if (session != null && session.isOpen()) {
            try {
                GridUpdateMessage message = GridUpdateMessage.builder()
                    .type("GRID_UPDATE")
                    .viewId(viewId)
                    .rowKey(rowKey)
                    .data(result)
                    .build();
                    
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (Exception e) {
                log.error("Failed to send update for view: {}", viewId, e);
            }
        }
    }
    
    private AccountOverviewRequest parseRequest(String payload) {
        try {
            return objectMapper.readValue(payload, AccountOverviewRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse request", e);
        }
    }
}
```

### 3.2 Simplified Message Model
```java
@Data
@Builder
public class GridUpdateMessage {
    private String type;                    // "GRID_UPDATE"
    private String viewId;
    private String rowKey;
    private AccountOverviewResult data;     // Direct data (no complex change detection)
}
```

## Phase 4: Frontend Implementation (Unchanged)

### 4.1 React Components
**Location**: `react-ui/src/pages/AccountOverview/`

#### Files to Create:
```
AccountOverview.tsx              # Main page component
components/
├── AccountSelector.tsx          # Multi-select accounts
├── GroupBySelector.tsx          # Single-select grouping
├── ExposureSelector.tsx         # Multi-checkbox exposures
├── DynamicGrid.tsx             # AG-Grid with WebSocket
└── ViewConfigPanel.tsx         # View management UI
hooks/
├── useWebSocket.ts             # WebSocket connection
├── useViewManagement.ts        # View lifecycle
└── useGridUpdates.ts           # Incremental updates
```

### 4.2 Simplified Grid Updates
```typescript
// DynamicGrid.tsx - Simplified update handling
const DynamicGrid: React.FC<{ viewId: string }> = ({ viewId }) => {
  const [gridApi, setGridApi] = useState<GridApi | null>(null);
  const [rowData, setRowData] = useState<any[]>([]);
  const { sendMessage, lastMessage } = useWebSocket(`ws://localhost:8080/ws/account-overview/${viewId}`);
  
  useEffect(() => {
    if (lastMessage) {
      const message = JSON.parse(lastMessage.data);
      if (message.type === 'GRID_UPDATE') {
        // Simple update - just replace the row data
        updateRow(message.rowKey, message.data);
      }
    }
  }, [lastMessage]);
  
  const updateRow = (rowKey: string, newData: AccountOverviewResult) => {
    if (!gridApi) return;
    
    const rowNode = gridApi.getRowNode(rowKey);
    if (rowNode) {
      // Update existing row
      rowNode.setData(newData);
      highlightChangedRow(rowNode);
    } else {
      // Add new row
      gridApi.applyTransaction({ add: [{ ...newData, groupKey: rowKey }] });
    }
  };
  
  const highlightChangedRow = (rowNode: RowNode) => {
    // Simple row highlighting
    const rowElement = gridApi?.getRowElement(rowNode.rowIndex!);
    if (rowElement) {
      rowElement.classList.add('row-changed');
      setTimeout(() => {
        rowElement.classList.remove('row-changed');
      }, 2000);
    }
  };
  
  return (
    <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
      <AgGridReact
        onGridReady={(params) => setGridApi(params.api)}
        rowData={rowData}
        columnDefs={dynamicColumnDefs}
        getRowId={(params) => params.data.groupKey}
        // ... other AG-Grid props
      />
    </div>
  );
};
```

## Phase 5: Simplified API Endpoints

### 5.1 REST Controllers
**Location**: `view-server/src/main/java/com/viewserver/controller/`

#### Files to Create:
```
AccountOverviewController.java      # Main API endpoints
```

#### Implementation:
```java
@RestController
@RequestMapping("/api/account-overview")
public class AccountOverviewController {
    
    private final AccountOverviewViewService viewService;
    private final ViewLifecycleManager viewLifecycleManager;
    
    @PostMapping("/create-view")
    public ResponseEntity<ViewResponse> createView(@RequestBody AccountOverviewRequest request) {
        String viewId = viewService.createView(request);
        return ResponseEntity.ok(ViewResponse.builder().viewId(viewId).build());
    }
    
    @GetMapping("/active-views")
    public ResponseEntity<List<ViewMetadata>> getActiveViews() {
        return ResponseEntity.ok(viewLifecycleManager.getActiveViews());
    }
    
    @DeleteMapping("/views/{viewId}")
    public ResponseEntity<Void> deleteView(@PathVariable String viewId) {
        viewLifecycleManager.deleteView(viewId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/views/{viewId}/config")
    public ResponseEntity<AccountOverviewRequest> getViewConfig(@PathVariable String viewId) {
        return ResponseEntity.ok(viewLifecycleManager.getViewConfig(viewId));
    }
    
    @GetMapping("/groupby-fields")
    public ResponseEntity<List<String>> getAvailableGroupByFields() {
        // Return available fields from HoldingMV/OrderMV
        return ResponseEntity.ok(Arrays.asList(
            "accountName", "instrumentName", "instrumentType", 
            "currency", "sector", "countryOfRisk", "countryOfDomicile"
        ));
    }
}
```

## Phase 6: Testing & Integration

### 6.1 Unit Tests
- **UnifiedMarketValueProcessor**: Price consistency validation
- **AccountOverviewViewService**: Simplified topology creation
- **ViewLifecycleManager**: View management

### 6.2 Integration Tests
- **End-to-End**: Full flow from price update to grid change
- **WebSocket**: Real-time communication testing
- **Performance**: Latency and throughput validation

### 6.3 Load Testing
- **Concurrent Views**: Multiple users with different selections
- **High Frequency Updates**: Rapid price changes
- **Memory Usage**: View cleanup and state management

## Phase 7: Deployment & Monitoring

### 7.1 Configuration Updates
```yaml
# system-config.yml
view-server:
  account-overview:
    max-concurrent-views: 100
    view-cleanup-delay: 60000  # 1 minute
    websocket:
      endpoint: "/ws/account-overview"
      max-sessions: 1000
```

### 7.2 Monitoring & Metrics
- **View Lifecycle Metrics**: Creation/deletion rates
- **Price Consistency Metrics**: Timestamp validation success rate
- **Performance Metrics**: Update latency
- **WebSocket Metrics**: Connection count, message throughput

## Implementation Timeline (Reduced!)

### Week 1-2: Foundation (Phase 1)
- Implement UnifiedMarketValueJob
- Replace existing Flink jobs
- Validate price consistency

### Week 3: Simplified Backend Services (Phase 2)
- Implement simplified core services
- Dynamic view creation (much simpler!)

### Week 4: WebSocket Communication (Phase 3)
- Simplified WebSocket handlers
- Direct updates (no batching complexity)

### Week 5: Frontend Implementation (Phase 4)
- React components
- AG-Grid integration
- Simplified real-time updates

### Week 6: API & Testing (Phase 5-6)
- REST endpoints
- Unit and integration tests
- Performance validation

### Week 7: Deployment & Polish (Phase 7)
- Production deployment
- Monitoring setup
- Documentation

## Success Criteria

### Functional Requirements
- ✅ Multi-select account filtering
- ✅ Dynamic grouping by any HoldingMV/OrderMV field
- ✅ SOD/Current/Expected exposure calculations
- ✅ Real-time grid updates
- ✅ 100% price consistency between holdings and orders

### Performance Requirements
- ✅ View creation < 500ms
- ✅ Update latency < 100ms
- ✅ Automatic view cleanup after 1-minute disconnection

### Technical Requirements
- ✅ Unified Flink job for price consistency
- ✅ Simplified Kafka Streams topologies
- ✅ Direct WebSocket updates
- ✅ Scalable view management

## Key Simplifications Achieved

### **Eliminated Complexity:**
- ❌ **No stream splitting** (holdings vs orders)
- ❌ **No complex joins** with time windows
- ❌ **No price validation** (Flink guarantees consistency)
- ❌ **No change detection service** (KTable built-in)
- ❌ **No message batching** (direct updates)
- ❌ **No complex state management** (single aggregation)

### **Reduced Implementation:**
- 🔥 **~70% less code** in Kafka Streams layer
- 🔥 **~50% less complexity** overall
- 🔥 **1 week faster** implementation timeline
- 🔥 **Lower memory usage** (single state store)
- 🔥 **Better performance** (fewer processing hops) 