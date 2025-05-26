# UI Specification

## Account Overview Page

### Overview
The Account Overview page provides a dynamic, real-time view of portfolio positions with configurable grouping and exposure calculations. The page ensures **price consistency** by using a unified data processing approach where both holdings and orders are calculated using identical price data.

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
- **Available Fields** (from HoldingMV/OrderMV):
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

#### Calculation Formulas
```sql
-- Based on user selection, the view aggregates:
SELECT 
  [dynamic_group_fields],
  SUM(holdingMv.marketValueUSD) as SOD_NAV_USD,
  SUM(holdingMv.marketValueUSD + orderMv.filledMarketValueUSD) as CURRENT_NAV_USD,
  SUM(holdingMv.marketValueUSD + orderMv.orderMarketValueUSD) as EXPECTED_NAV_USD
FROM holdingMV 
LEFT JOIN orderMV ON (
  holdingMv.instrumentId = orderMv.instrumentId AND 
  holdingMv.accountId = orderMv.accountId AND
  holdingMv.priceTimestamp = orderMv.priceTimestamp  -- PRICE CONSISTENCY
)
WHERE holdingMv.accountId IN [selected_accounts]
GROUP BY [dynamic_group_fields]
HAVING [filter_empty_groups]
```

#### Data Consistency Requirements
- **Critical**: HoldingMV and OrderMV must use **identical price data** for the same instrument
- **Implementation**: Unified Flink job ensures both calculations use the same price update
- **Validation**: Reject aggregations where price timestamps don't match

#### Error Handling
- **No holdings for selected accounts**: Show accounts with SOD NAV = $0
- **Orders exist but no holdings**: Show rows with SOD NAV = $0, Current/Expected from orders only
- **Empty groups**: Filter out automatically (don't display)

### Real-Time Updates

#### WebSocket Communication
- **Endpoint**: `/ws/account-overview/{viewId}` (unique per view)
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
        }
      }
    }
  ]
}
```

#### Update Batching
- **Frequency**: 100ms batching for rapid changes
- **Strategy**: Collect all changes within 100ms window, send as single message
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
├── ChangeDetectionService (incremental updates)
└── WebSocketHandler (real-time communication)
```

#### Data Flow
```
Unified Flink Job → aggregation.unified-mv → Kafka Streams → WebSocket → React Grid
```

### API Endpoints

#### View Management
- `POST /api/account-overview/create-view` - Create new view
- `GET /api/account-overview/active-views` - List active views
- `DELETE /api/account-overview/views/{viewId}` - Delete view
- `GET /api/account-overview/views/{viewId}/config` - Get view configuration

#### Data APIs
- `GET /api/accounts` - Get all accounts (for selector)
- `GET /api/account-overview/groupby-fields` - Get available grouping fields

### Performance Requirements
- **View Creation**: < 500ms from selection change to first data
- **Real-time Updates**: < 100ms from data change to grid update
- **Price Consistency**: 100% consistency between HoldingMV and OrderMV prices
- **Concurrent Views**: Support multiple simultaneous views per user
- **Memory**: Automatic cleanup of disconnected views