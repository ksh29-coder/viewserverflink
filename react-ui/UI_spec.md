# UI Specification

## Account Overview Page

### Overview
The Account Overview page provides a dynamic, real-time view of portfolio positions with configurable grouping and exposure calculations. The page ensures **price consistency** by using a unified data processing approach where both holdings and orders are processed together using identical price data from the `aggregation.unified-mv` Kafka topic.

### UI Components

#### 1. Account Selector
- **Type**: Multi-select dropdown
- **Default**: All accounts selected (ACC001-ACC005)
- **Data Source**: Account data from Redis cache via `/api/accounts`
- **Behavior**: 
  - Shows account ID and name (e.g., "ACC001 - Global Growth Fund")
  - Minimum 1 account must be selected
  - Changes trigger immediate view recreation

#### 2. Group By Selector
- **Type**: Single-select dropdown
- **Default**: `[accountName, instrumentName]`
- **Available Fields** (from UnifiedMarketValue records):
  - `accountName` (always first option)
  - `instrumentName`
  - `instrumentType`
  - `currency`
  - `countryOfRisk`
  - `countryOfDomicile`
  - `sector`
  - `orderStatus` (when orders exist)
  - `orderType` (when orders exist)
  - `venue` (when orders exist)
- **Behavior**: Changes trigger immediate view recreation

#### 3. Account Exposure Selector
- **Type**: Multi-select checkboxes
- **Options**: 
  - SOD (Start of Day)
  - Current 
  - Expected
- **Default**: All three selected
- **Behavior**: Changes trigger immediate view recreation

#### 4. Dynamic Grid
- **Technology**: AG-Grid with WebSocket real-time updates
- **Update Strategy**: **Incremental cell-level updates only**
- **Visual Indicators**: Brief green highlight (2 seconds) for changed cells
- **Columns**: Dynamic based on selections
  - Group by fields (dynamic)
  - SOD NAV USD (if selected)
  - Current NAV USD (if selected)
  - Expected NAV USD (if selected)
  - Last Updated timestamp

### Data Processing Logic

#### Unified Data Source
The Account Overview consumes data from the **`aggregation.unified-mv`** Kafka topic, which contains both holdings and orders with guaranteed price consistency. Each record has a `recordType` field indicating whether it's a "HOLDING" or "ORDER".

#### Calculation Formulas
```sql
-- Based on user selection, the view aggregates from unified stream:
SELECT 
  [dynamic_group_fields],
  SUM(CASE WHEN recordType = 'HOLDING' THEN marketValueUSD ELSE 0 END) as SOD_NAV_USD,
  SUM(CASE WHEN recordType = 'HOLDING' THEN marketValueUSD 
           WHEN recordType = 'ORDER' THEN filledMarketValueUSD 
           ELSE 0 END) as CURRENT_NAV_USD,
  SUM(CASE WHEN recordType = 'HOLDING' THEN marketValueUSD 
           WHEN recordType = 'ORDER' THEN marketValueUSD 
           ELSE 0 END) as EXPECTED_NAV_USD
FROM aggregation.unified-mv 
WHERE accountId IN [selected_accounts]
  AND priceTimestamp IS NOT NULL  -- Ensure valid price data
GROUP BY [dynamic_group_fields]
HAVING [filter_empty_groups]
```

#### Data Consistency Guarantees
- **Price Consistency**: All holdings and orders for the same instrument use identical `price` and `priceTimestamp`
- **Atomic Updates**: When prices change, both holdings and orders are updated simultaneously
- **Single Source**: Eliminates join complexity and potential inconsistencies
- **Validation**: Records without valid prices are filtered out

#### Record Types and Market Values
- **HOLDING records**:
  - `marketValueUSD`: SOD position value (position × price)
  - Used for SOD NAV calculation
- **ORDER records**:
  - `marketValueUSD`: Full order value (orderQuantity × price)
  - `filledMarketValueUSD`: Filled portion value (filledQuantity × price)
  - Used for Current/Expected NAV calculations

#### Error Handling
- **No holdings for selected accounts**: Show accounts with SOD NAV = $0
- **Orders exist but no holdings**: Show rows with SOD NAV = $0, Current/Expected from orders only
- **Invalid price data**: Filter out records with null prices or timestamps
- **Empty groups**: Filter out automatically (don't display)

### Real-Time Updates

#### WebSocket Communication
- **Endpoint**: `/ws/account-overview/{viewId}` (unique per view)
- **Data Source**: Live stream from `aggregation.unified-mv` topic
- **Message Format**:
```json
{
  "type": "GRID_UPDATE",
  "viewId": "view_12345",
  "changes": [
    {
      "changeType": "UPDATE",
      "rowKey": "Global Growth Fund#Apple Inc",
      "changedFields": {
        "currentNavUSD": {
          "oldValue": 150000.00,
          "newValue": 151250.00
        },
        "expectedNavUSD": {
          "oldValue": 155000.00,
          "newValue": 156250.00
        }
      }
    }
  ]
}
```

#### Update Batching
- **Frequency**: 100ms batching for rapid changes
- **Strategy**: Collect all unified-mv changes within 100ms window, send as single message
- **Visual**: Individual cell highlighting for 2 seconds

#### View Lifecycle
- **Creation**: New view created immediately when selection changes
- **Destruction**: Old view destroyed immediately upon new selection
- **Cleanup**: Views auto-deleted 1 minute after WebSocket disconnection
- **Monitoring**: API endpoint to view active views and their configurations

### Technical Architecture

#### Frontend (React)
```
AccountOverview.tsx
├── AccountSelector.tsx (multi-select)
├── GroupBySelector.tsx (single-select)
├── ExposureSelector.tsx (multi-checkbox)
├── DynamicGrid.tsx (AG-Grid + WebSocket)
└── ViewConfigPanel.tsx (view management)
```

#### Backend (View Server)
```
Computation Layer (Kafka Streams)
├── AccountOverviewViewService (dynamic topology creation)
├── ViewLifecycleManager (view creation/cleanup)
├── UnifiedMVChangeDetectionService (incremental updates)
└── WebSocketHandler (real-time communication)
```

#### Data Flow
```
UnifiedMarketValueJob → aggregation.unified-mv → Kafka Streams View → WebSocket → React Grid
```

### API Endpoints

#### View Management
- `POST /api/account-overview/create-view` - Create new view
- `GET /api/account-overview/active-views` - List active views
- `DELETE /api/account-overview/views/{viewId}` - Delete view
- `GET /api/account-overview/views/{viewId}/config` - Get view configuration

#### Data APIs
- `GET /api/accounts` - Get all accounts (for selector)
- `GET /api/account-overview/groupby-fields` - Get available grouping fields from unified-mv schema
- `GET /api/unified-mv` - Get current unified market value data (for testing/debugging)

### Performance Requirements
- **View Creation**: < 500ms from selection change to first data
- **Real-time Updates**: < 100ms from unified-mv change to grid update
- **Price Consistency**: 100% consistency guaranteed by unified data source
- **Concurrent Views**: Support multiple simultaneous views per user
- **Memory**: Automatic cleanup of disconnected views

### Benefits of Unified Approach
- **Simplified Architecture**: Single data stream instead of complex joins
- **Guaranteed Consistency**: Holdings and orders always use identical prices
- **Better Performance**: No join overhead, direct aggregation
- **Easier Debugging**: Single source of truth for all market value calculations
- **Reduced Complexity**: Eliminates price timestamp validation logic