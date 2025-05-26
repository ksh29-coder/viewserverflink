import React, { useState, useEffect, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'
import { apiService } from '../services/apiService'

const AggregationLayer = () => {
  const [holdingsMV, setHoldingsMV] = useState([])
  const [ordersMV, setOrdersMV] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedAccount, setSelectedAccount] = useState('')
  const [accounts, setAccounts] = useState([])
  const [lastRefresh, setLastRefresh] = useState(new Date())
  
  // Grid state management for both grids
  const holdingsGridRef = useRef()
  const ordersGridRef = useRef()
  const [holdingsGridState, setHoldingsGridState] = useState(null)
  const [ordersGridState, setOrdersGridState] = useState(null)

  const fetchData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      // Save current grid states before refreshing data
      if (holdingsGridRef.current && holdingsGridRef.current.api) {
        try {
          const currentState = {
            filterModel: holdingsGridRef.current.api.getFilterModel ? holdingsGridRef.current.api.getFilterModel() : null,
            sortModel: holdingsGridRef.current.api.getSortModel ? holdingsGridRef.current.api.getSortModel() : null,
            columnState: holdingsGridRef.current.api.getColumnState ? holdingsGridRef.current.api.getColumnState() : null
          }
          setHoldingsGridState(currentState)
        } catch (gridError) {
          console.warn('Failed to save holdings grid state:', gridError)
        }
      }
      
      if (ordersGridRef.current && ordersGridRef.current.api) {
        try {
          const currentState = {
            filterModel: ordersGridRef.current.api.getFilterModel ? ordersGridRef.current.api.getFilterModel() : null,
            sortModel: ordersGridRef.current.api.getSortModel ? ordersGridRef.current.api.getSortModel() : null,
            columnState: ordersGridRef.current.api.getColumnState ? ordersGridRef.current.api.getColumnState() : null
          }
          setOrdersGridState(currentState)
        } catch (gridError) {
          console.warn('Failed to save orders grid state:', gridError)
        }
      }
      
      const [holdingsMVData, ordersMVData, accountsData] = await Promise.all([
        apiService.getAllHoldingsMV(),
        apiService.getAllOrdersMV(),
        apiService.getAccounts()
      ])
      
      setHoldingsMV(holdingsMVData)
      setOrdersMV(ordersMVData)
      setAccounts(accountsData)
      setLastRefresh(new Date())
    } catch (err) {
      setError('Failed to fetch aggregation data: ' + err.message)
      console.error('Error fetching aggregation data:', err)
    } finally {
      setLoading(false)
    }
  }

  // Restore grid state after data update
  const onHoldingsGridReady = (params) => {
    holdingsGridRef.current = params
    
    // Restore state if it exists
    if (holdingsGridState) {
      setTimeout(() => {
        if (params.api) {
          try {
            if (holdingsGridState.filterModel && params.api.setFilterModel) {
              params.api.setFilterModel(holdingsGridState.filterModel)
            }
            if (holdingsGridState.sortModel && params.api.setSortModel) {
              params.api.setSortModel(holdingsGridState.sortModel)
            }
            if (holdingsGridState.columnState && params.api.applyColumnState) {
              params.api.applyColumnState({ state: holdingsGridState.columnState })
            }
          } catch (gridError) {
            console.warn('Failed to restore holdings grid state:', gridError)
          }
        }
      }, 100)
    }
  }

  const onOrdersGridReady = (params) => {
    ordersGridRef.current = params
    
    // Restore state if it exists
    if (ordersGridState) {
      setTimeout(() => {
        if (params.api) {
          try {
            if (ordersGridState.filterModel && params.api.setFilterModel) {
              params.api.setFilterModel(ordersGridState.filterModel)
            }
            if (ordersGridState.sortModel && params.api.setSortModel) {
              params.api.setSortModel(ordersGridState.sortModel)
            }
            if (ordersGridState.columnState && params.api.applyColumnState) {
              params.api.applyColumnState({ state: ordersGridState.columnState })
            }
          } catch (gridError) {
            console.warn('Failed to restore orders grid state:', gridError)
          }
        }
      }, 100)
    }
  }

  useEffect(() => {
    fetchData()
    
    // Auto-refresh every 10 seconds
    const interval = setInterval(fetchData, 10000)
    return () => clearInterval(interval)
  }, [])

  // Restore grid states when data changes
  useEffect(() => {
    if (holdingsGridRef.current && holdingsGridRef.current.api && holdingsGridState) {
      setTimeout(() => {
        try {
          if (holdingsGridState.filterModel && holdingsGridRef.current.api.setFilterModel) {
            holdingsGridRef.current.api.setFilterModel(holdingsGridState.filterModel)
          }
          if (holdingsGridState.sortModel && holdingsGridRef.current.api.setSortModel) {
            holdingsGridRef.current.api.setSortModel(holdingsGridState.sortModel)
          }
          if (holdingsGridState.columnState && holdingsGridRef.current.api.applyColumnState) {
            holdingsGridRef.current.api.applyColumnState({ state: holdingsGridState.columnState })
          }
        } catch (gridError) {
          console.warn('Failed to restore holdings grid state in useEffect:', gridError)
        }
      }, 100)
    }
  }, [holdingsMV, holdingsGridState])

  useEffect(() => {
    if (ordersGridRef.current && ordersGridRef.current.api && ordersGridState) {
      setTimeout(() => {
        try {
          if (ordersGridState.filterModel && ordersGridRef.current.api.setFilterModel) {
            ordersGridRef.current.api.setFilterModel(ordersGridState.filterModel)
          }
          if (ordersGridState.sortModel && ordersGridRef.current.api.setSortModel) {
            ordersGridRef.current.api.setSortModel(ordersGridState.sortModel)
          }
          if (ordersGridState.columnState && ordersGridRef.current.api.applyColumnState) {
            ordersGridRef.current.api.applyColumnState({ state: ordersGridState.columnState })
          }
        } catch (gridError) {
          console.warn('Failed to restore orders grid state in useEffect:', gridError)
        }
      }, 100)
    }
  }, [ordersMV, ordersGridState])

  const handleRefresh = () => {
    fetchData()
  }

  // Filter data by selected account
  const filteredHoldings = selectedAccount 
    ? holdingsMV.filter(holding => holding.accountId === selectedAccount)
    : holdingsMV

  const filteredOrders = selectedAccount 
    ? ordersMV.filter(order => order.accountId === selectedAccount)
    : ordersMV

  // Calculate summary statistics for holdings
  const totalHoldingsMarketValueUSD = filteredHoldings.reduce((sum, holding) => {
    return sum + (holding.marketValueUSD || 0)
  }, 0)
  
  const totalHoldingsMarketValueLocal = filteredHoldings.reduce((sum, holding) => {
    return sum + (holding.marketValueLocal || 0)
  }, 0)

  // Calculate summary statistics for orders
  const totalOrdersMarketValueUSD = filteredOrders.reduce((sum, order) => {
    return sum + (order.orderMarketValueUSD || 0)
  }, 0)

  const totalFilledMarketValueUSD = filteredOrders.reduce((sum, order) => {
    return sum + (order.filledMarketValueUSD || 0)
  }, 0)

  const instrumentTypes = [...new Set([...filteredHoldings.map(h => h.instrumentType), ...filteredOrders.map(o => o.instrumentType)])]

  // Holdings column definitions
  const holdingsColumnDefs = [
    { 
      headerName: 'Account', 
      field: 'accountId', 
      width: 100,
      pinned: 'left'
    },
    { 
      headerName: 'Instrument', 
      field: 'instrumentId', 
      width: 120,
      pinned: 'left'
    },
    { 
      headerName: 'Name', 
      field: 'instrumentName', 
      width: 200,
      pinned: 'left'
    },
    { 
      headerName: 'Type', 
      field: 'instrumentType', 
      width: 100
    },
    { 
      headerName: 'Currency', 
      field: 'currency', 
      width: 100
    },
    { 
      headerName: 'Position', 
      field: 'position', 
      width: 120,
      valueFormatter: params => params.value ? params.value.toLocaleString() : '0'
    },
    { 
      headerName: 'Price', 
      field: 'price', 
      width: 120,
      valueFormatter: params => params.value ? `$${params.value.toFixed(2)}` : '$0.00'
    },
    { 
      headerName: 'Market Value (Local)', 
      field: 'marketValueLocal', 
      width: 180,
      valueFormatter: params => params.value ? `${params.value.toLocaleString('en-US', {
        style: 'currency',
        currency: params.data.currency || 'USD'
      })}` : '$0.00',
      cellStyle: { backgroundColor: '#e8f5e8', fontWeight: 'bold' }
    },
    { 
      headerName: 'Market Value (USD)', 
      field: 'marketValueUSD', 
      width: 180,
      valueFormatter: params => params.value ? `$${params.value.toLocaleString()}` : '$0.00',
      cellStyle: { backgroundColor: '#e8f4fd', fontWeight: 'bold' }
    },
    { 
      headerName: 'Date', 
      field: 'date', 
      width: 120
    },
    { 
      headerName: 'Calculation Time', 
      field: 'calculationTimestamp', 
      width: 180,
      valueFormatter: params => params.value ? new Date(params.value).toLocaleString() : ''
    }
  ]

  // Orders column definitions
  const ordersColumnDefs = [
    { 
      headerName: 'Account', 
      field: 'accountId', 
      width: 100,
      pinned: 'left'
    },
    { 
      headerName: 'Order ID', 
      field: 'orderId', 
      width: 120,
      pinned: 'left'
    },
    { 
      headerName: 'Instrument', 
      field: 'instrumentId', 
      width: 120,
      pinned: 'left'
    },
    { 
      headerName: 'Name', 
      field: 'instrumentName', 
      width: 200
    },
    { 
      headerName: 'Type', 
      field: 'instrumentType', 
      width: 100
    },
    { 
      headerName: 'Side', 
      field: 'side', 
      width: 80,
      cellStyle: params => ({
        backgroundColor: params.value === 'BUY' ? '#d4edda' : '#f8d7da',
        fontWeight: 'bold'
      })
    },
    { 
      headerName: 'Currency', 
      field: 'currency', 
      width: 100
    },
    { 
      headerName: 'Quantity', 
      field: 'quantity', 
      width: 120,
      valueFormatter: params => params.value ? params.value.toLocaleString() : '0'
    },
    { 
      headerName: 'Filled Qty', 
      field: 'filledQuantity', 
      width: 120,
      valueFormatter: params => params.value ? params.value.toLocaleString() : '0'
    },
    { 
      headerName: 'Price', 
      field: 'price', 
      width: 120,
      valueFormatter: params => params.value ? `$${params.value.toFixed(2)}` : '$0.00'
    },
    { 
      headerName: 'Order MV (Local)', 
      field: 'orderMarketValueLocal', 
      width: 160,
      valueFormatter: params => params.value ? `${params.value.toLocaleString('en-US', {
        style: 'currency',
        currency: params.data.currency || 'USD'
      })}` : '$0.00',
      cellStyle: { backgroundColor: '#fff3cd', fontWeight: 'bold' }
    },
    { 
      headerName: 'Order MV (USD)', 
      field: 'orderMarketValueUSD', 
      width: 160,
      valueFormatter: params => params.value ? `$${params.value.toLocaleString()}` : '$0.00',
      cellStyle: { backgroundColor: '#d1ecf1', fontWeight: 'bold' }
    },
    { 
      headerName: 'Filled MV (Local)', 
      field: 'filledMarketValueLocal', 
      width: 160,
      valueFormatter: params => params.value ? `${params.value.toLocaleString('en-US', {
        style: 'currency',
        currency: params.data.currency || 'USD'
      })}` : '$0.00',
      cellStyle: { backgroundColor: '#e8f5e8', fontWeight: 'bold' }
    },
    { 
      headerName: 'Filled MV (USD)', 
      field: 'filledMarketValueUSD', 
      width: 160,
      valueFormatter: params => params.value ? `$${params.value.toLocaleString()}` : '$0.00',
      cellStyle: { backgroundColor: '#e8f4fd', fontWeight: 'bold' }
    },
    { 
      headerName: 'Status', 
      field: 'status', 
      width: 100,
      cellStyle: params => {
        const status = params.value;
        if (status === 'FILLED') return { backgroundColor: '#d4edda', fontWeight: 'bold' };
        if (status === 'PARTIAL') return { backgroundColor: '#fff3cd', fontWeight: 'bold' };
        if (status === 'PENDING') return { backgroundColor: '#d1ecf1', fontWeight: 'bold' };
        return { fontWeight: 'bold' };
      }
    },
    { 
      headerName: 'Order Time', 
      field: 'orderTimestamp', 
      width: 180,
      valueFormatter: params => params.value ? new Date(params.value).toLocaleString() : ''
    },
    { 
      headerName: 'Calculation Time', 
      field: 'calculationTimestamp', 
      width: 180,
      valueFormatter: params => params.value ? new Date(params.value).toLocaleString() : ''
    }
  ]

  const defaultColDef = {
    sortable: true,
    filter: true,
    resizable: true,
    floatingFilter: true
  }

  if (loading) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <div>Loading aggregation data...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: '20px', color: 'red' }}>
        <h3>Error</h3>
        <p>{error}</p>
        <button onClick={handleRefresh} style={{ 
          padding: '10px 20px', 
          backgroundColor: '#007bff', 
          color: 'white', 
          border: 'none', 
          borderRadius: '4px',
          cursor: 'pointer'
        }}>
          Retry
        </button>
      </div>
    )
  }

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ marginBottom: '20px' }}>
        <h2 style={{ margin: '0 0 20px 0', color: '#333' }}>Aggregation Layer - Holdings & Orders with Market Values</h2>
        
        {/* Controls Section - Account Filter and Refresh Button */}
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
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <label htmlFor="account-filter" style={{ fontWeight: 'bold', color: '#495057' }}>
              Filter by Account:
            </label>
            <select
              id="account-filter"
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
              <option value="">All Accounts</option>
              {accounts.map(account => (
                <option key={account.accountId} value={account.accountId}>
                  {account.accountId} - {account.accountName}
                </option>
              ))}
            </select>
          </div>
          
          <button
            onClick={handleRefresh}
            style={{
              padding: '8px 16px',
              backgroundColor: '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '5px'
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
            <h4 style={{ margin: '0 0 5px 0', color: '#004085' }}>Holdings MV (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#004085' }}>
              ${totalHoldingsMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#d1ecf1', 
            borderRadius: '8px', 
            border: '1px solid #bee5eb' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#0c5460' }}>Orders MV (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#0c5460' }}>
              ${totalOrdersMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e8f5e8', 
            borderRadius: '8px', 
            border: '1px solid #b8e6b8' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#155724' }}>Filled MV (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#155724' }}>
              ${totalFilledMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#fff3cd', 
            borderRadius: '8px', 
            border: '1px solid #ffeaa7' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#856404' }}>Holdings Count</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#856404' }}>
              {filteredHoldings.length}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#f8d7da', 
            borderRadius: '8px', 
            border: '1px solid #f5c6cb' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#721c24' }}>Orders Count</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#721c24' }}>
              {filteredOrders.length}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e2e3e5', 
            borderRadius: '8px', 
            border: '1px solid #d6d8db' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#383d41' }}>Instrument Types</h4>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: '#383d41' }}>
              {instrumentTypes.join(', ')}
            </div>
          </div>
        </div>
      </div>

      {/* Holdings Grid */}
      <div style={{ marginBottom: '30px' }}>
        <h3 style={{ margin: '0 0 15px 0', color: '#333' }}>📊 Holdings with Market Values</h3>
        <div className="ag-theme-alpine" style={{ height: '400px', width: '100%' }}>
          <AgGridReact
            columnDefs={holdingsColumnDefs}
            rowData={filteredHoldings}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={15}
            suppressRowClickSelection={true}
            rowSelection="multiple"
            animateRows={true}
            onGridReady={onHoldingsGridReady}
          />
        </div>
      </div>

      {/* Orders Grid */}
      <div style={{ marginBottom: '30px' }}>
        <h3 style={{ margin: '0 0 15px 0', color: '#333' }}>📋 Orders with Market Values</h3>
        <div className="ag-theme-alpine" style={{ height: '400px', width: '100%' }}>
          <AgGridReact
            columnDefs={ordersColumnDefs}
            rowData={filteredOrders}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={15}
            suppressRowClickSelection={true}
            rowSelection="multiple"
            animateRows={true}
            onGridReady={onOrdersGridReady}
          />
        </div>
      </div>

      {/* Data Pipeline Information */}
      <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ color: '#495057', marginBottom: '15px' }}>📊 Data Pipeline Information</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          <div>
            <h4 style={{ color: '#007bff', marginBottom: '10px' }}>🔄 Holdings Data Flow</h4>
            <ol style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Base Data:</strong> SOD Holdings + Instruments + Prices</li>
              <li><strong>Flink Processing:</strong> Stream joins and market value calculations</li>
              <li><strong>Kafka Topic:</strong> aggregation.holding-mv</li>
              <li><strong>View Server:</strong> Consumes and caches in Redis</li>
              <li><strong>React UI:</strong> Real-time display with auto-refresh</li>
            </ol>
          </div>
          
          <div>
            <h4 style={{ color: '#28a745', marginBottom: '10px' }}>🔄 Orders Data Flow</h4>
            <ol style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Base Data:</strong> Orders + Instruments + Prices</li>
              <li><strong>Flink Processing:</strong> Stream joins and market value calculations</li>
              <li><strong>Kafka Topic:</strong> aggregation.order-mv</li>
              <li><strong>View Server:</strong> Consumes and caches in Redis</li>
              <li><strong>React UI:</strong> Real-time display with auto-refresh</li>
            </ol>
          </div>
          
          <div>
            <h4 style={{ color: '#dc3545', marginBottom: '10px' }}>💰 Market Value Calculations</h4>
            <ul style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Equity/Fund:</strong> Price × Quantity</li>
              <li><strong>Bond:</strong> (Price/100) × Face Value × Quantity</li>
              <li><strong>Currency:</strong> Quantity only</li>
              <li><strong>FX Conversion:</strong> Local → USD using exchange rates</li>
              <li><strong>Orders:</strong> Both full order and filled quantities</li>
            </ul>
          </div>
        </div>
        
        {(filteredHoldings.length === 0 && filteredOrders.length === 0) && (
          <div style={{ 
            marginTop: '20px', 
            padding: '20px', 
            backgroundColor: '#fff3cd', 
            borderRadius: '8px',
            border: '1px solid #ffeaa7'
          }}>
            <h4 style={{ color: '#856404', marginBottom: '10px' }}>🚀 Getting Started</h4>
            <p style={{ margin: '0', lineHeight: '1.6', color: '#856404' }}>
              No aggregation data found. To populate this view:
              <br />1. Ensure the Mock Data Generator is running and has initialized base data
              <br />2. Run the Flink HoldingMV and OrderMV jobs to process base data into market value records
              <br />3. The data will automatically appear here once processing is complete
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

export default AggregationLayer 