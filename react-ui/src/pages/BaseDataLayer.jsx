import React, { useState, useEffect, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'
import { apiService } from '../services/apiService'

const BaseDataLayer = () => {
  const [accounts, setAccounts] = useState([])
  const [instruments, setInstruments] = useState([])
  const [prices, setPrices] = useState([])
  const [orders, setOrders] = useState([])
  const [selectedAccount, setSelectedAccount] = useState('')
  const [accountHoldings, setAccountHoldings] = useState([])
  const [accountCash, setAccountCash] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastRefresh, setLastRefresh] = useState(new Date())
  const [activeTab, setActiveTab] = useState('overview')

  // Grid state management for different grids
  const gridRefs = useRef({})
  const [gridStates, setGridStates] = useState({})

  const saveGridState = (gridId) => {
    if (gridRefs.current[gridId] && gridRefs.current[gridId].api) {
      try {
        const currentState = {
          filterModel: gridRefs.current[gridId].api.getFilterModel ? gridRefs.current[gridId].api.getFilterModel() : null,
          sortModel: gridRefs.current[gridId].api.getSortModel ? gridRefs.current[gridId].api.getSortModel() : null,
          columnState: gridRefs.current[gridId].api.getColumnState ? gridRefs.current[gridId].api.getColumnState() : null
        }
        setGridStates(prev => ({ ...prev, [gridId]: currentState }))
      } catch (gridError) {
        console.warn(`Failed to save grid state for ${gridId}:`, gridError)
      }
    }
  }

  const restoreGridState = (gridId, params) => {
    const state = gridStates[gridId]
    if (state && params.api) {
      setTimeout(() => {
        try {
          if (state.filterModel && params.api.setFilterModel) {
            params.api.setFilterModel(state.filterModel)
          }
          if (state.sortModel && params.api.setSortModel) {
            params.api.setSortModel(state.sortModel)
          }
          if (state.columnState && params.api.applyColumnState) {
            params.api.applyColumnState({ state: state.columnState })
          }
        } catch (gridError) {
          console.warn(`Failed to restore grid state for ${gridId}:`, gridError)
        }
      }, 100)
    }
  }

  const onGridReady = (gridId) => (params) => {
    gridRefs.current[gridId] = params
    restoreGridState(gridId, params)
  }

  const fetchBaseData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      // Save current grid states before refreshing data
      Object.keys(gridRefs.current).forEach(gridId => {
        saveGridState(gridId)
      })
      
      const [accountsData, instrumentsData, pricesData, ordersData] = await Promise.all([
        apiService.getAccounts(),
        apiService.getInstruments(),
        apiService.getPrices(),
        apiService.getOrders()
      ])
      
      setAccounts(accountsData)
      setInstruments(instrumentsData)
      setPrices(pricesData)
      setOrders(ordersData)
      setLastRefresh(new Date())
    } catch (err) {
      setError('Failed to fetch base data: ' + err.message)
      console.error('Error fetching base data:', err)
    } finally {
      setLoading(false)
    }
  }

  const fetchAccountSpecificData = async (accountId) => {
    if (!accountId) {
      setAccountHoldings([])
      setAccountCash([])
      return
    }

    try {
      const [holdingsData, cashData] = await Promise.all([
        apiService.getHoldings(accountId).catch(err => {
          console.warn(`Holdings API failed for account ${accountId}:`, err.message)
          return []
        }),
        apiService.getCashMovements(accountId).catch(err => {
          console.warn(`Cash movements API failed for account ${accountId}:`, err.message)
          return []
        })
      ])
      
      setAccountHoldings(holdingsData)
      setAccountCash(cashData)
    } catch (err) {
      console.error('Error fetching account-specific data:', err)
    }
  }

  useEffect(() => {
    fetchBaseData()
    
    // Auto-refresh every 10 seconds
    const interval = setInterval(fetchBaseData, 10000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    fetchAccountSpecificData(selectedAccount)
  }, [selectedAccount])

  // Restore grid states when data changes
  useEffect(() => {
    Object.keys(gridRefs.current).forEach(gridId => {
      if (gridRefs.current[gridId] && gridStates[gridId]) {
        try {
          restoreGridState(gridId, gridRefs.current[gridId])
        } catch (gridError) {
          console.warn(`Failed to restore grid state for ${gridId} in useEffect:`, gridError)
        }
      }
    })
  }, [accounts, instruments, prices, orders, accountHoldings, accountCash, gridStates])

  const handleRefresh = () => {
    fetchBaseData()
    if (selectedAccount) {
      fetchAccountSpecificData(selectedAccount)
    }
  }

  // Column definitions
  const accountColumns = [
    { field: 'accountId', headerName: 'Account ID', width: 120, pinned: 'left' },
    { field: 'accountName', headerName: 'Account Name', flex: 1 },
    { field: 'timestamp', headerName: 'Last Updated', width: 180, 
      valueFormatter: (params) => params.value ? new Date(params.value).toLocaleString() : '' }
  ]

  const instrumentColumns = [
    { field: 'instrumentId', headerName: 'Instrument ID', width: 120, pinned: 'left' },
    { field: 'instrumentName', headerName: 'Name', flex: 1 },
    { field: 'instrumentType', headerName: 'Type', width: 100 },
    { field: 'currency', headerName: 'Currency', width: 80 },
    { field: 'sector', headerName: 'Sector', width: 120 },
    { field: 'countryOfRisk', headerName: 'Country', width: 100 }
  ]

  const priceColumns = [
    { field: 'instrumentId', headerName: 'Instrument', width: 120, pinned: 'left' },
    { field: 'price', headerName: 'Price', width: 120, 
      valueFormatter: (params) => params.value ? `$${Number(params.value).toFixed(2)}` : '$0.00',
      cellStyle: { backgroundColor: '#e8f4fd', fontWeight: 'bold' } },
    { field: 'currency', headerName: 'Currency', width: 80 },
    { field: 'source', headerName: 'Source', width: 120 },
    { field: 'date', headerName: 'Date', width: 120 }
  ]

  const orderColumns = [
    { field: 'orderId', headerName: 'Order ID', width: 120, pinned: 'left' },
    { field: 'accountId', headerName: 'Account', width: 100 },
    { field: 'instrumentId', headerName: 'Instrument', width: 120 },
    { field: 'orderType', headerName: 'Type', width: 80 },
    { field: 'orderStatus', headerName: 'Status', width: 100,
      cellStyle: (params) => {
        if (params.value === 'FILLED') return { backgroundColor: '#d4edda', color: '#155724' }
        if (params.value === 'PENDING') return { backgroundColor: '#fff3cd', color: '#856404' }
        if (params.value === 'CANCELLED') return { backgroundColor: '#f8d7da', color: '#721c24' }
        return {}
      }
    },
    { field: 'quantity', headerName: 'Quantity', width: 100,
      valueFormatter: (params) => params.value ? Number(params.value).toLocaleString() : '0' },
    { field: 'price', headerName: 'Price', width: 100,
      valueFormatter: (params) => params.value ? `$${Number(params.value).toFixed(2)}` : '-' },
    { field: 'timestamp', headerName: 'Time', width: 150,
      valueFormatter: (params) => params.value ? new Date(params.value).toLocaleString() : '' }
  ]

  const holdingColumns = [
    { field: 'instrumentId', headerName: 'Instrument', width: 120, pinned: 'left' },
    { field: 'position', headerName: 'Position', width: 120, 
      valueFormatter: (params) => params.value ? Number(params.value).toLocaleString() : '0',
      cellStyle: { backgroundColor: '#e8f5e8', fontWeight: 'bold' } },
    { field: 'date', headerName: 'Date', width: 120 }
  ]

  const cashColumns = [
    { field: 'instrumentId', headerName: 'Currency', width: 100, pinned: 'left' },
    { field: 'movementType', headerName: 'Type', width: 120 },
    { field: 'quantity', headerName: 'Amount', width: 120,
      valueFormatter: (params) => params.value ? Number(params.value).toLocaleString() : '0',
      cellStyle: { backgroundColor: '#fff3cd', fontWeight: 'bold' } },
    { field: 'date', headerName: 'Date', width: 120 }
  ]

  const defaultColDef = {
    sortable: true,
    filter: true,
    resizable: true,
    floatingFilter: true
  }

  if (loading && accounts.length === 0) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <div>Loading base data...</div>
      </div>
    )
  }

  const TabButton = ({ id, label, count, isActive, onClick }) => (
    <button
      onClick={() => onClick(id)}
      style={{
        padding: '10px 20px',
        backgroundColor: isActive ? '#007bff' : '#f8f9fa',
        color: isActive ? 'white' : '#495057',
        border: '1px solid #dee2e6',
        borderRadius: '4px',
        cursor: 'pointer',
        fontWeight: isActive ? 'bold' : 'normal',
        marginRight: '10px'
      }}
      onMouseOver={(e) => {
        if (!isActive) {
          e.target.style.backgroundColor = '#e9ecef'
        }
      }}
      onMouseOut={(e) => {
        if (!isActive) {
          e.target.style.backgroundColor = '#f8f9fa'
        }
      }}
    >
      {label} ({count})
    </button>
  )

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ marginBottom: '20px' }}>
        <h2 style={{ margin: '0 0 10px 0', color: '#333' }}>Base Data Layer</h2>
        <p style={{ margin: '0 0 20px 0', color: '#666' }}>
          Raw financial data from the Mock Data Generator - Real-time feeds from Kafka topics
        </p>
        
        {/* Controls Section */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '20px', 
          marginBottom: '20px',
          padding: '15px',
          backgroundColor: '#f8f9fa',
          borderRadius: '8px',
          border: '1px solid #dee2e6'
        }}>
          <button
            onClick={handleRefresh}
            style={{
              padding: '8px 16px',
              backgroundColor: '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = '#218838'}
            onMouseOut={(e) => e.target.style.backgroundColor = '#28a745'}
          >
            🔄 Refresh Data
          </button>
          
          <div style={{ color: '#6c757d', fontSize: '14px' }}>
            Last updated: {lastRefresh.toLocaleTimeString()}
          </div>
        </div>

        {error && (
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#f8d7da', 
            color: '#721c24', 
            borderRadius: '8px',
            marginBottom: '20px'
          }}>
            {error}
          </div>
        )}

        {/* Summary Statistics */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
          gap: '15px', 
          marginBottom: '20px' 
        }}>
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e8f4fd', 
            borderRadius: '8px', 
            border: '1px solid #b8daff' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#004085' }}>Accounts</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#004085' }}>
              {accounts.length}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e8f5e8', 
            borderRadius: '8px', 
            border: '1px solid #b8e6b8' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#155724' }}>Instruments</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#155724' }}>
              {instruments.length}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#fff3cd', 
            borderRadius: '8px', 
            border: '1px solid #ffeaa7' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#856404' }}>Current Prices</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#856404' }}>
              {prices.length}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#f8d7da', 
            borderRadius: '8px', 
            border: '1px solid #f5c6cb' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#721c24' }}>Active Orders</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#721c24' }}>
              {orders.length}
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div style={{ marginBottom: '20px' }}>
          <TabButton 
            id="overview" 
            label="Overview" 
            count={accounts.length + instruments.length + prices.length + orders.length}
            isActive={activeTab === 'overview'} 
            onClick={setActiveTab} 
          />
          <TabButton 
            id="accounts" 
            label="Accounts" 
            count={accounts.length}
            isActive={activeTab === 'accounts'} 
            onClick={setActiveTab} 
          />
          <TabButton 
            id="instruments" 
            label="Instruments" 
            count={instruments.length}
            isActive={activeTab === 'instruments'} 
            onClick={setActiveTab} 
          />
          <TabButton 
            id="prices" 
            label="Prices" 
            count={prices.length}
            isActive={activeTab === 'prices'} 
            onClick={setActiveTab} 
          />
          <TabButton 
            id="orders" 
            label="Orders" 
            count={orders.length}
            isActive={activeTab === 'orders'} 
            onClick={setActiveTab} 
          />
          <TabButton 
            id="account-data" 
            label="Account Data" 
            count={accountHoldings.length + accountCash.length}
            isActive={activeTab === 'account-data'} 
            onClick={setActiveTab} 
          />
        </div>
      </div>

      {/* Tab Content */}
      {activeTab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '20px' }}>
          {/* Accounts Preview */}
          <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
            <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>📊 Accounts ({accounts.length})</h3>
            <div className="ag-theme-alpine" style={{ height: '200px', width: '100%' }}>
              <AgGridReact
                columnDefs={accountColumns}
                rowData={accounts.slice(0, 5)}
                defaultColDef={defaultColDef}
                suppressPaginationPanel={true}
                onGridReady={onGridReady('accounts-preview')}
              />
            </div>
          </div>

          {/* Instruments Preview */}
          <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
            <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>🏢 Instruments ({instruments.length})</h3>
            <div className="ag-theme-alpine" style={{ height: '200px', width: '100%' }}>
              <AgGridReact
                columnDefs={instrumentColumns}
                rowData={instruments.slice(0, 5)}
                defaultColDef={defaultColDef}
                suppressPaginationPanel={true}
                onGridReady={onGridReady('instruments-preview')}
              />
            </div>
          </div>

          {/* Prices Preview */}
          <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
            <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>💰 Current Prices ({prices.length})</h3>
            <div className="ag-theme-alpine" style={{ height: '200px', width: '100%' }}>
              <AgGridReact
                columnDefs={priceColumns}
                rowData={prices.slice(0, 5)}
                defaultColDef={defaultColDef}
                suppressPaginationPanel={true}
                onGridReady={onGridReady('prices-preview')}
              />
            </div>
          </div>

          {/* Orders Preview */}
          <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
            <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>📋 Recent Orders ({orders.length})</h3>
            <div className="ag-theme-alpine" style={{ height: '200px', width: '100%' }}>
              <AgGridReact
                columnDefs={orderColumns}
                rowData={orders.slice(0, 5)}
                defaultColDef={defaultColDef}
                suppressPaginationPanel={true}
                onGridReady={onGridReady('orders-preview')}
              />
            </div>
          </div>
        </div>
      )}

      {activeTab === 'accounts' && (
        <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
          <AgGridReact
            columnDefs={accountColumns}
            rowData={accounts}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={20}
            onGridReady={onGridReady('accounts-main')}
          />
        </div>
      )}

      {activeTab === 'instruments' && (
        <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
          <AgGridReact
            columnDefs={instrumentColumns}
            rowData={instruments}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={20}
            onGridReady={onGridReady('instruments-main')}
          />
        </div>
      )}

      {activeTab === 'prices' && (
        <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
          <AgGridReact
            columnDefs={priceColumns}
            rowData={prices}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={20}
            onGridReady={onGridReady('prices-main')}
          />
        </div>
      )}

      {activeTab === 'orders' && (
        <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
          <AgGridReact
            columnDefs={orderColumns}
            rowData={orders}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={20}
            onGridReady={onGridReady('orders-main')}
          />
        </div>
      )}

      {activeTab === 'account-data' && (
        <div>
          {/* Account Selector */}
          <div style={{ 
            marginBottom: '20px',
            padding: '15px',
            backgroundColor: '#f8f9fa',
            borderRadius: '8px',
            border: '1px solid #dee2e6'
          }}>
            <label htmlFor="account-select" style={{ fontWeight: 'bold', color: '#495057', marginRight: '10px' }}>
              Select Account:
            </label>
            <select
              id="account-select"
              value={selectedAccount}
              onChange={(e) => setSelectedAccount(e.target.value)}
              style={{
                padding: '8px 12px',
                border: '1px solid #ced4da',
                borderRadius: '4px',
                backgroundColor: 'white',
                minWidth: '200px'
              }}
            >
              <option value="">Select an account...</option>
              {accounts.map(account => (
                <option key={account.accountId} value={account.accountId}>
                  {account.accountId} - {account.accountName}
                </option>
              ))}
            </select>
          </div>

          {selectedAccount && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
              {/* Holdings */}
              <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
                <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>
                  📈 Holdings for {selectedAccount} ({accountHoldings.length})
                </h3>
                <div className="ag-theme-alpine" style={{ height: '400px', width: '100%' }}>
                  <AgGridReact
                    columnDefs={holdingColumns}
                    rowData={accountHoldings}
                    defaultColDef={defaultColDef}
                    pagination={true}
                    paginationPageSize={10}
                    onGridReady={onGridReady('holdings')}
                  />
                </div>
              </div>

              {/* Cash Movements */}
              <div style={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #dee2e6', padding: '20px' }}>
                <h3 style={{ margin: '0 0 15px 0', color: '#495057' }}>
                  💵 Cash Movements for {selectedAccount} ({accountCash.length})
                </h3>
                <div className="ag-theme-alpine" style={{ height: '400px', width: '100%' }}>
                  <AgGridReact
                    columnDefs={cashColumns}
                    rowData={accountCash}
                    defaultColDef={defaultColDef}
                    pagination={true}
                    paginationPageSize={10}
                    onGridReady={onGridReady('cash-movements')}
                  />
                </div>
              </div>
            </div>
          )}

          {!selectedAccount && (
            <div style={{ 
              padding: '40px', 
              textAlign: 'center', 
              backgroundColor: '#f8f9fa', 
              borderRadius: '8px',
              border: '1px solid #dee2e6'
            }}>
              <h4 style={{ color: '#6c757d' }}>Select an account to view holdings and cash movements</h4>
            </div>
          )}
        </div>
      )}

      {/* Data Pipeline Information */}
      <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ color: '#495057', marginBottom: '15px' }}>🔄 Data Pipeline Information</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          <div>
            <h4 style={{ color: '#007bff', marginBottom: '10px' }}>📡 Kafka Topics</h4>
            <ul style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>base.account:</strong> Account reference data</li>
              <li><strong>base.instrument:</strong> Instrument master data</li>
              <li><strong>base.sod-holding:</strong> Start-of-day positions</li>
              <li><strong>base.price:</strong> Real-time market prices (5s)</li>
              <li><strong>base.order-events:</strong> Trading orders</li>
              <li><strong>base.intraday-cash:</strong> Cash movements (2m)</li>
            </ul>
          </div>
          
          <div>
            <h4 style={{ color: '#28a745', marginBottom: '10px' }}>⚡ Real-time Updates</h4>
            <ul style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Prices:</strong> Updated every 5 seconds</li>
              <li><strong>Orders:</strong> New orders every 30s, updates every 10s</li>
              <li><strong>Cash:</strong> Movements every 2 minutes</li>
              <li><strong>UI Refresh:</strong> Auto-refresh every 10 seconds</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}

export default BaseDataLayer 