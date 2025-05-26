# Account Overview Implementation Plan

## Overview
This document outlines the complete implementation plan for the Account Overview feature, including the unified Flink job for price consistency, dynamic Kafka Streams views, and real-time WebSocket grid updates.

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

## Phase 2: Account Overview Backend Services

### 2.1 Data Models
**Location**: `view-server/src/main/java/com/viewserver/computation/model/`

#### Files to Create:
```
AccountOverviewRequest.java     # User selection input
AccountOverviewResult.java      # Aggregated output
ViewMetadata.java              # View lifecycle tracking
GridChange.java                # WebSocket change messages
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
```

### 2.2 Core Services
**Location**: `view-server/src/main/java/com/viewserver/computation/service/`

#### Files to Create:
```
AccountOverviewViewService.java     # Dynamic view creation
ViewLifecycleManager.java          # View management
ChangeDetectionService.java        # Incremental updates
PriceConsistencyValidator.java     # Timestamp validation
```

#### Key Implementation - Dynamic View Creation:
```java
@Service
public class AccountOverviewViewService {
    
    public String createView(AccountOverviewRequest request) {
        // 1. Generate unique viewId
        String viewId = generateViewId(request);
        
        // 2. Create dynamic Kafka Streams topology
        StreamsBuilder builder = new StreamsBuilder();
        
        // 3. Consume unified-mv topic
        KStream<String, UnifiedMarketValue> unifiedStream = builder.stream("aggregation.unified-mv");
        
        // 4. Split into holdings and orders
        KStream<String, HoldingMV> holdings = unifiedStream
            .filter((key, value) -> "HOLDING".equals(value.getType()))
            .mapValues(value -> value.getHoldingMV());
            
        KStream<String, OrderMV> orders = unifiedStream
            .filter((key, value) -> "ORDER".equals(value.getType()))
            .mapValues(value -> value.getOrderMV());
        
        // 5. Join with price timestamp validation
        KStream<String, JoinedData> joined = holdings.leftJoin(orders,
            (holding, order) -> new JoinedData(holding, order),
            JoinWindows.of(Duration.ofSeconds(1)),
            StreamJoined.with(Serdes.String(), holdingSerde, orderSerde)
        ).filter((key, joined) -> validatePriceConsistency(joined));
        
        // 6. Filter by selected accounts
        KStream<String, JoinedData> filtered = joined
            .filter((key, data) -> request.getAccountIds().contains(data.getAccountId()));
        
        // 7. Dynamic grouping
        KGroupedStream<String, JoinedData> grouped = filtered
            .groupBy((key, data) -> buildGroupKey(data, request.getGroupByFields()));
        
        // 8. Aggregate exposures
        KTable<String, AccountOverviewResult> results = grouped.aggregate(
            AccountOverviewResult::new,
            (key, data, aggregate) -> updateAggregate(aggregate, data, request.getExposureTypes())
        );
        
        // 9. Enable change detection
        results.toStream().foreach((key, result) -> 
            changeDetectionService.detectChanges(viewId, key, result));
        
        // 10. Start topology
        KafkaStreams streams = new KafkaStreams(builder.build(), getStreamsConfig(viewId));
        streams.start();
        
        // 11. Store view metadata
        viewLifecycleManager.registerView(viewId, request, streams);
        
        return viewId;
    }
}
```

### 2.3 Change Detection Service
```java
@Service
public class ChangeDetectionService {
    
    private final Map<String, Map<String, AccountOverviewResult>> viewCache = new ConcurrentHashMap<>();
    private final AccountOverviewWebSocketHandler webSocketHandler;
    
    public void detectChanges(String viewId, String rowKey, AccountOverviewResult newResult) {
        AccountOverviewResult oldResult = getFromCache(viewId, rowKey);
        
        List<GridChange> changes = calculateChanges(rowKey, oldResult, newResult);
        if (!changes.isEmpty()) {
            // Batch changes for 100ms
            batchAndSendChanges(viewId, changes);
        }
        
        updateCache(viewId, rowKey, newResult);
    }
    
    private List<GridChange> calculateChanges(String rowKey, 
                                            AccountOverviewResult oldResult, 
                                            AccountOverviewResult newResult) {
        List<GridChange> changes = new ArrayList<>();
        
        if (oldResult == null) {
            // New row
            changes.add(GridChange.builder()
                .changeType(ChangeType.INSERT)
                .rowKey(rowKey)
                .changedFields(getAllFields(newResult))
                .build());
        } else {
            // Check each field for changes
            Map<String, FieldChange> fieldChanges = new HashMap<>();
            
            if (!Objects.equals(oldResult.getSodNavUSD(), newResult.getSodNavUSD())) {
                fieldChanges.put("sodNavUSD", new FieldChange(oldResult.getSodNavUSD(), newResult.getSodNavUSD()));
            }
            // ... check other fields
            
            if (!fieldChanges.isEmpty()) {
                changes.add(GridChange.builder()
                    .changeType(ChangeType.UPDATE)
                    .rowKey(rowKey)
                    .changedFields(fieldChanges)
                    .build());
            }
        }
        
        return changes;
    }
}
```

## Phase 3: WebSocket Real-Time Communication

### 3.1 WebSocket Handler
**Location**: `view-server/src/main/java/com/viewserver/websocket/`

#### Files to Create:
```
AccountOverviewWebSocketHandler.java    # Main WebSocket handler
WebSocketSessionManager.java           # Session management
MessageBatcher.java                    # 100ms batching logic
```

#### Implementation:
```java
@Component
public class AccountOverviewWebSocketHandler extends TextWebSocketHandler {
    
    private final ViewLifecycleManager viewLifecycleManager;
    private final MessageBatcher messageBatcher;
    private final Map<String, String> sessionToViewId = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        // Session registered when view is created
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle view creation requests
        AccountOverviewRequest request = parseRequest(message.getPayload());
        String viewId = accountOverviewViewService.createView(request);
        
        sessionToViewId.put(session.getId(), viewId);
        
        // Send initial data
        sendInitialData(session, viewId);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String viewId = sessionToViewId.remove(session.getId());
        if (viewId != null) {
            // Schedule cleanup after 1 minute
            viewLifecycleManager.scheduleViewCleanup(viewId, Duration.ofMinutes(1));
        }
    }
    
    public void sendChanges(String viewId, List<GridChange> changes) {
        // Find session for viewId and send batched changes
        messageBatcher.batchChanges(viewId, changes);
    }
}
```

### 3.2 Message Batching
```java
@Component
public class MessageBatcher {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, List<GridChange>> pendingChanges = new ConcurrentHashMap<>();
    
    public void batchChanges(String viewId, List<GridChange> changes) {
        pendingChanges.computeIfAbsent(viewId, k -> new ArrayList<>()).addAll(changes);
        
        // Schedule send after 100ms
        scheduler.schedule(() -> sendBatchedChanges(viewId), 100, TimeUnit.MILLISECONDS);
    }
    
    private void sendBatchedChanges(String viewId) {
        List<GridChange> changes = pendingChanges.remove(viewId);
        if (changes != null && !changes.isEmpty()) {
            GridUpdateMessage message = GridUpdateMessage.builder()
                .type("GRID_UPDATE")
                .viewId(viewId)
                .changes(changes)
                .build();
                
            webSocketHandler.sendToView(viewId, message);
        }
    }
}
```

## Phase 4: Frontend Implementation

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

### 4.2 Key Implementation - Dynamic Grid
```typescript
// DynamicGrid.tsx
const DynamicGrid: React.FC<{ viewId: string }> = ({ viewId }) => {
  const [gridApi, setGridApi] = useState<GridApi | null>(null);
  const [rowData, setRowData] = useState<any[]>([]);
  const { sendMessage, lastMessage } = useWebSocket(`ws://localhost:8080/ws/account-overview/${viewId}`);
  
  useEffect(() => {
    if (lastMessage) {
      const message = JSON.parse(lastMessage.data);
      if (message.type === 'GRID_UPDATE') {
        applyIncrementalUpdates(message.changes);
      }
    }
  }, [lastMessage]);
  
  const applyIncrementalUpdates = (changes: GridChange[]) => {
    changes.forEach(change => {
      switch (change.changeType) {
        case 'INSERT':
          addNewRow(change);
          break;
        case 'UPDATE':
          updateExistingRow(change);
          break;
        case 'DELETE':
          removeRow(change.rowKey);
          break;
      }
    });
  };
  
  const updateExistingRow = (change: GridChange) => {
    if (!gridApi) return;
    
    const rowNode = gridApi.getRowNode(change.rowKey);
    if (rowNode) {
      Object.entries(change.changedFields).forEach(([field, fieldChange]) => {
        rowNode.setDataValue(field, fieldChange.newValue);
        highlightChangedCell(rowNode, field);
      });
    }
  };
  
  const highlightChangedCell = (rowNode: RowNode, field: string) => {
    const cellElement = gridApi?.getCellElement(rowNode.rowIndex!, field);
    if (cellElement) {
      cellElement.classList.add('cell-changed');
      setTimeout(() => {
        cellElement.classList.remove('cell-changed');
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

### 4.3 CSS for Visual Indicators
```css
/* AccountOverview.css */
.cell-changed {
  background-color: #90EE90 !important; /* Light green */
  transition: background-color 2s ease-out;
}

.ag-cell {
  transition: background-color 0.3s ease-in;
}

.account-overview-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.controls-panel {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 8px;
}
```

## Phase 5: API Endpoints

### 5.1 REST Controllers
**Location**: `view-server/src/main/java/com/viewserver/controller/`

#### Files to Create:
```
AccountOverviewController.java      # Main API endpoints
ViewManagementController.java       # View lifecycle APIs
```

#### Implementation:
```java
@RestController
@RequestMapping("/api/account-overview")
public class AccountOverviewController {
    
    @PostMapping("/create-view")
    public ResponseEntity<ViewResponse> createView(@RequestBody AccountOverviewRequest request) {
        String viewId = accountOverviewViewService.createView(request);
        return ResponseEntity.ok(ViewResponse.builder().viewId(viewId).build());
    }
    
    @GetMapping("/active-views")
    public ResponseEntity<List<ActiveViewInfo>> getActiveViews() {
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
    public ResponseEntity<List<GroupByField>> getAvailableGroupByFields() {
        return ResponseEntity.ok(getGroupByFields());
    }
}
```

## Phase 6: Testing & Integration

### 6.1 Unit Tests
- **UnifiedMarketValueProcessor**: Price consistency validation
- **ChangeDetectionService**: Incremental update logic
- **AccountOverviewViewService**: Dynamic topology creation
- **MessageBatcher**: 100ms batching behavior

### 6.2 Integration Tests
- **End-to-End**: Full flow from price update to grid change
- **WebSocket**: Real-time communication testing
- **Performance**: Latency and throughput validation
- **Consistency**: Price timestamp validation across HoldingMV/OrderMV

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
    batch-interval: 100        # 100ms
    websocket:
      endpoint: "/ws/account-overview"
      max-sessions: 1000
```

### 7.2 Monitoring & Metrics
- **View Lifecycle Metrics**: Creation/deletion rates
- **Price Consistency Metrics**: Timestamp validation success rate
- **Performance Metrics**: Update latency, batching efficiency
- **WebSocket Metrics**: Connection count, message throughput

### 7.3 Operational Procedures
- **View Cleanup**: Automated cleanup of orphaned views
- **Error Handling**: Graceful degradation for data inconsistencies
- **Scaling**: Horizontal scaling considerations for Kafka Streams

## Implementation Timeline

### Week 1-2: Foundation (Phase 1)
- Implement UnifiedMarketValueJob
- Replace existing Flink jobs
- Validate price consistency

### Week 3-4: Backend Services (Phase 2)
- Implement core services
- Dynamic view creation
- Change detection logic

### Week 5: WebSocket Communication (Phase 3)
- WebSocket handlers
- Message batching
- Session management

### Week 6: Frontend Implementation (Phase 4)
- React components
- AG-Grid integration
- Real-time updates

### Week 7: API & Testing (Phase 5-6)
- REST endpoints
- Unit and integration tests
- Performance validation

### Week 8: Deployment & Polish (Phase 7)
- Production deployment
- Monitoring setup
- Documentation and training

## Success Criteria

### Functional Requirements
- ✅ Multi-select account filtering
- ✅ Dynamic grouping by any HoldingMV/OrderMV field
- ✅ SOD/Current/Expected exposure calculations
- ✅ Real-time grid updates with cell-level changes
- ✅ 100% price consistency between holdings and orders

### Performance Requirements
- ✅ View creation < 500ms
- ✅ Update latency < 100ms
- ✅ 100ms batching for rapid changes
- ✅ 2-second visual change indicators
- ✅ Automatic view cleanup after 1-minute disconnection

### Technical Requirements
- ✅ Unified Flink job for price consistency
- ✅ Dynamic Kafka Streams topologies
- ✅ WebSocket incremental updates
- ✅ Scalable view management
- ✅ Comprehensive monitoring and observability 