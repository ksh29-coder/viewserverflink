import React, { useState, useEffect, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'
import { apiService } from '../services/apiService'

const AggregationLayer = () => {
  const [holdingsMV, setHoldingsMV] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedAccount, setSelectedAccount] = useState('')
  const [accounts, setAccounts] = useState([])
  const [lastRefresh, setLastRefresh] = useState(new Date())
  
  // Grid state management
  const gridRef = useRef()
  const [gridState, setGridState] = useState(null)

  const fetchData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      // Save current grid state before refreshing data
      if (gridRef.current && gridRef.current.api) {
        try {
          const currentState = {
            filterModel: gridRef.current.api.getFilterModel ? gridRef.current.api.getFilterModel() : null,
            sortModel: gridRef.current.api.getSortModel ? gridRef.current.api.getSortModel() : null,
            columnState: gridRef.current.api.getColumnState ? gridRef.current.api.getColumnState() : null
          }
          setGridState(currentState)
        } catch (gridError) {
          console.warn('Failed to save grid state:', gridError)
        }
      }
      
      const [holdingsMVData, accountsData] = await Promise.all([
        apiService.getAllHoldingsMV(),
        apiService.getAccounts()
      ])
      
      setHoldingsMV(holdingsMVData)
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
  const onGridReady = (params) => {
    gridRef.current = params
    
    // Restore state if it exists
    if (gridState) {
      setTimeout(() => {
        if (params.api) {
          try {
            if (gridState.filterModel && params.api.setFilterModel) {
              params.api.setFilterModel(gridState.filterModel)
            }
            if (gridState.sortModel && params.api.setSortModel) {
              params.api.setSortModel(gridState.sortModel)
            }
            if (gridState.columnState && params.api.applyColumnState) {
              params.api.applyColumnState({ state: gridState.columnState })
            }
          } catch (gridError) {
            console.warn('Failed to restore grid state:', gridError)
          }
        }
      }, 100) // Small delay to ensure data is loaded
    }
  }

  useEffect(() => {
    fetchData()
    
    // Auto-refresh every 10 seconds
    const interval = setInterval(fetchData, 10000)
    return () => clearInterval(interval)
  }, [])

  // Restore grid state when data changes
  useEffect(() => {
    if (gridRef.current && gridRef.current.api && gridState) {
      setTimeout(() => {
        try {
          if (gridState.filterModel && gridRef.current.api.setFilterModel) {
            gridRef.current.api.setFilterModel(gridState.filterModel)
          }
          if (gridState.sortModel && gridRef.current.api.setSortModel) {
            gridRef.current.api.setSortModel(gridState.sortModel)
          }
          if (gridState.columnState && gridRef.current.api.applyColumnState) {
            gridRef.current.api.applyColumnState({ state: gridState.columnState })
          }
        } catch (gridError) {
          console.warn('Failed to restore grid state in useEffect:', gridError)
        }
      }, 100)
    }
  }, [holdingsMV, gridState])

  const handleRefresh = () => {
    fetchData()
  }

  // Filter holdings by selected account
  const filteredHoldings = selectedAccount 
    ? holdingsMV.filter(holding => holding.accountId === selectedAccount)
    : holdingsMV

  // Calculate summary statistics
  const totalMarketValueUSD = filteredHoldings.reduce((sum, holding) => {
    return sum + (holding.marketValueUSD || 0)
  }, 0)
  
  const totalMarketValueLocal = filteredHoldings.reduce((sum, holding) => {
    return sum + (holding.marketValueLocal || 0)
  }, 0)

  const instrumentTypes = [...new Set(filteredHoldings.map(h => h.instrumentType))]

  const columnDefs = [
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
        <h2 style={{ margin: '0 0 20px 0', color: '#333' }}>Aggregation Layer - Holdings with Market Values</h2>
        
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
            <h4 style={{ margin: '0 0 5px 0', color: '#004085' }}>Total Market Value (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#004085' }}>
              ${totalMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e8f5e8', 
            borderRadius: '8px', 
            border: '1px solid #b8e6b8' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#155724' }}>Total Market Value (Local)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#155724' }}>
              {totalMarketValueLocal.toLocaleString()}
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
            <h4 style={{ margin: '0 0 5px 0', color: '#721c24' }}>Instrument Types</h4>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: '#721c24' }}>
              {instrumentTypes.join(', ')}
            </div>
          </div>
        </div>
      </div>

      {/* Data Grid */}
      <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
        <AgGridReact
          columnDefs={columnDefs}
          rowData={filteredHoldings}
          defaultColDef={defaultColDef}
          pagination={true}
          paginationPageSize={20}
          suppressRowClickSelection={true}
          rowSelection="multiple"
          animateRows={true}
          onGridReady={onGridReady}
        />
      </div>

      {/* Data Pipeline Information */}
      <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ color: '#495057', marginBottom: '15px' }}>📊 Data Pipeline Information</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          <div>
            <h4 style={{ color: '#007bff', marginBottom: '10px' }}>🔄 Data Flow</h4>
            <ol style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Base Data:</strong> SOD Holdings + Instruments + Prices</li>
              <li><strong>Flink Processing:</strong> Stream joins and market value calculations</li>
              <li><strong>Kafka Topic:</strong> aggregation.holding-mv</li>
              <li><strong>View Server:</strong> Consumes and caches in Redis</li>
              <li><strong>React UI:</strong> Real-time display with auto-refresh</li>
            </ol>
          </div>
          
          <div>
            <h4 style={{ color: '#28a745', marginBottom: '10px' }}>💰 Market Value Calculations</h4>
            <ul style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Equity/Fund:</strong> Price × Quantity</li>
              <li><strong>Bond:</strong> (Price/100) × Face Value × Quantity</li>
              <li><strong>Currency:</strong> Quantity only</li>
              <li><strong>FX Conversion:</strong> Local → USD using exchange rates</li>
            </ul>
          </div>
        </div>
        
        {filteredHoldings.length === 0 && (
          <div style={{ 
            marginTop: '20px', 
            padding: '20px', 
            backgroundColor: '#fff3cd', 
            borderRadius: '8px',
            border: '1px solid #ffeaa7'
          }}>
            <h4 style={{ color: '#856404', marginBottom: '10px' }}>🚀 Getting Started</h4>
            <p style={{ margin: '0', lineHeight: '1.6', color: '#856404' }}>
              No HoldingMV data found. To populate this view:
              <br />1. Ensure the Mock Data Generator is running and has initialized base data
              <br />2. Run the Flink HoldingMV job to process base data into market value records
              <br />3. The data will automatically appear here once processing is complete
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

export default AggregationLayer 