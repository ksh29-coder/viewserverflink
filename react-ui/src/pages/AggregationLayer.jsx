import React, { useState, useEffect, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'
import { apiService } from '../services/apiService'

const AggregationLayer = () => {
  const [unifiedMV, setUnifiedMV] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedAccount, setSelectedAccount] = useState('')
  const [selectedInstrument, setSelectedInstrument] = useState('')
  const [accounts, setAccounts] = useState([])
  const [instruments, setInstruments] = useState([])
  const [lastRefresh, setLastRefresh] = useState(new Date())
  
  // Grid refs
  const unifiedGridRef = useRef()

  // Load filters from localStorage on component mount
  useEffect(() => {
    const savedAccountFilter = localStorage.getItem('aggregation-account-filter')
    const savedInstrumentFilter = localStorage.getItem('aggregation-instrument-filter')
    
    if (savedAccountFilter) {
      setSelectedAccount(savedAccountFilter)
    }
    if (savedInstrumentFilter) {
      setSelectedInstrument(savedInstrumentFilter)
    }
  }, [])

  // Save filters to localStorage whenever they change
  useEffect(() => {
    localStorage.setItem('aggregation-account-filter', selectedAccount)
  }, [selectedAccount])

  useEffect(() => {
    localStorage.setItem('aggregation-instrument-filter', selectedInstrument)
  }, [selectedInstrument])

  const fetchData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      const [unifiedMVData, accountsData, instrumentsData] = await Promise.all([
        apiService.getAllUnifiedMV(),
        apiService.getAccounts(),
        apiService.getInstruments()
      ])
      
      setUnifiedMV(unifiedMVData)
      setAccounts(accountsData)
      setInstruments(instrumentsData)
      setLastRefresh(new Date())
    } catch (err) {
      setError('Failed to fetch aggregation data: ' + err.message)
      console.error('Error fetching aggregation data:', err)
    } finally {
      setLoading(false)
    }
  }

  const onUnifiedGridReady = (params) => {
    unifiedGridRef.current = params
  }

  useEffect(() => {
    fetchData()
  }, [])

  const handleRefresh = () => {
    fetchData()
  }

  // Filter data by selected account and instrument
  const filteredUnified = unifiedMV.filter(unified => {
    const accountMatch = !selectedAccount || unified.accountId === selectedAccount
    const instrumentMatch = !selectedInstrument || unified.instrumentId === selectedInstrument
    return accountMatch && instrumentMatch
  })

  // Calculate summary statistics for unified data
  const unifiedHoldings = filteredUnified.filter(u => u.recordType === 'HOLDING')
  const unifiedOrders = filteredUnified.filter(u => u.recordType === 'ORDER')
  
  const totalUnifiedHoldingsMarketValueUSD = unifiedHoldings.reduce((sum, holding) => {
    return sum + (holding.marketValueUSD || 0)
  }, 0)
  
  const totalUnifiedOrdersMarketValueUSD = unifiedOrders.reduce((sum, order) => {
    return sum + (order.marketValueUSD || 0)
  }, 0)
  
  const totalUnifiedFilledMarketValueUSD = unifiedOrders.reduce((sum, order) => {
    return sum + (order.filledMarketValueUSD || 0)
  }, 0)

  const instrumentTypes = [...new Set([...filteredUnified.map(u => u.instrumentType)])]



  // Unified Market Value column definitions
  const unifiedColumnDefs = [
    { 
      headerName: 'Type', 
      field: 'recordType', 
      width: 100,
      pinned: 'left',
      cellStyle: params => ({
        backgroundColor: params.value === 'HOLDING' ? '#e8f5e8' : '#e8f4fd',
        fontWeight: 'bold'
      })
    },
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
    // Holding-specific fields
    { 
      headerName: 'Position', 
      field: 'position', 
      width: 120,
      valueFormatter: params => {
        if (params.data.recordType === 'HOLDING' && params.value) {
          return params.value.toLocaleString()
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'HOLDING' ? { backgroundColor: '#f0f8f0' } : {}
    },
    { 
      headerName: 'Date', 
      field: 'date', 
      width: 120,
      valueFormatter: params => {
        if (params.data.recordType === 'HOLDING' && params.value) {
          return params.value
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'HOLDING' ? { backgroundColor: '#f0f8f0' } : {}
    },
    // Order-specific fields
    { 
      headerName: 'Order ID', 
      field: 'orderId', 
      width: 120,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return params.value
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'ORDER' ? { backgroundColor: '#f0f8ff' } : {}
    },
    { 
      headerName: 'Side', 
      field: 'orderQuantity', 
      width: 80,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return params.value > 0 ? 'BUY' : 'SELL'
        }
        return ''
      },
      cellStyle: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return {
            backgroundColor: params.value > 0 ? '#d4edda' : '#f8d7da',
            fontWeight: 'bold'
          }
        }
        return {}
      }
    },
    { 
      headerName: 'Order Qty', 
      field: 'orderQuantity', 
      width: 120,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return Math.abs(params.value).toLocaleString()
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'ORDER' ? { backgroundColor: '#f0f8ff' } : {}
    },
    { 
      headerName: 'Filled Qty', 
      field: 'filledQuantity', 
      width: 120,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return params.value.toLocaleString()
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'ORDER' ? { backgroundColor: '#f0f8ff' } : {}
    },
    { 
      headerName: 'Status', 
      field: 'orderStatus', 
      width: 100,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return params.value
        }
        return ''
      },
      cellStyle: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          const status = params.value;
          if (status === 'FILLED') return { backgroundColor: '#d4edda', fontWeight: 'bold' };
          if (status === 'PARTIAL') return { backgroundColor: '#fff3cd', fontWeight: 'bold' };
          if (status === 'PENDING') return { backgroundColor: '#d1ecf1', fontWeight: 'bold' };
          return { fontWeight: 'bold' };
        }
        return {}
      }
    },
    // Common price and market value fields
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
    // Order-specific filled market values
    { 
      headerName: 'Filled MV (Local)', 
      field: 'filledMarketValueLocal', 
      width: 160,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return `${params.value.toLocaleString('en-US', {
        style: 'currency',
        currency: params.data.currency || 'USD'
          })}`
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'ORDER' ? { backgroundColor: '#fff3cd', fontWeight: 'bold' } : {}
    },
    { 
      headerName: 'Filled MV (USD)', 
      field: 'filledMarketValueUSD', 
      width: 160,
      valueFormatter: params => {
        if (params.data.recordType === 'ORDER' && params.value) {
          return `$${params.value.toLocaleString()}`
        }
        return ''
      },
      cellStyle: params => params.data.recordType === 'ORDER' ? { backgroundColor: '#d1ecf1', fontWeight: 'bold' } : {}
    },
    { 
      headerName: 'Price Source', 
      field: 'priceSource', 
      width: 120
    },
    { 
      headerName: 'Price Time', 
      field: 'priceTimestamp', 
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
    <div style={{ 
      padding: '20px',
      height: '100vh',
      overflowY: 'auto',
      overflowX: 'hidden'
    }}>
      <div style={{ marginBottom: '20px' }}>
        <h2 style={{ margin: '0 0 20px 0', color: '#333' }}>Aggregation Layer - Holdings & Orders with Market Values</h2>
        
        {/* Controls Section - Account Filter, Instrument Filter and Refresh Button */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '20px', 
          marginBottom: '20px',
          padding: '15px',
          backgroundColor: '#f8f9fa',
          borderRadius: '8px',
          border: '1px solid #dee2e6',
          flexWrap: 'wrap'
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
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <label htmlFor="instrument-filter" style={{ fontWeight: 'bold', color: '#495057' }}>
              Filter by Instrument:
            </label>
            <select
              id="instrument-filter"
              value={selectedInstrument}
              onChange={(e) => setSelectedInstrument(e.target.value)}
              style={{
                padding: '8px 12px',
                border: '1px solid #ced4da',
                borderRadius: '4px',
                backgroundColor: 'white',
                minWidth: '200px'
              }}
            >
              <option value="">All Instruments</option>
              {instruments.map(instrument => (
                <option key={instrument.instrumentId} value={instrument.instrumentId}>
                  {instrument.instrumentId} - {instrument.instrumentName}
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
          
          <button
            onClick={() => {
              setSelectedAccount('')
              setSelectedInstrument('')
            }}
            style={{
              padding: '8px 16px',
              backgroundColor: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '5px'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = '#545b62'}
            onMouseOut={(e) => e.target.style.backgroundColor = '#6c757d'}
          >
            🗑️ Clear Filters
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
            backgroundColor: '#e2e3e5', 
            borderRadius: '8px', 
            border: '1px solid #d6d8db' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#383d41' }}>Instrument Types</h4>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: '#383d41' }}>
              {instrumentTypes.join(', ')}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#e7e3ff', 
            borderRadius: '8px', 
            border: '1px solid #c3b5f7' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#6f42c1' }}>Unified Holdings MV (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#6f42c1' }}>
              ${totalUnifiedHoldingsMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#f3e5f5', 
            borderRadius: '8px', 
            border: '1px solid #e1bee7' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#8e24aa' }}>Unified Orders MV (USD)</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#8e24aa' }}>
              ${totalUnifiedOrdersMarketValueUSD.toLocaleString()}
            </div>
          </div>
          
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#fce4ec', 
            borderRadius: '8px', 
            border: '1px solid #f8bbd9' 
          }}>
            <h4 style={{ margin: '0 0 5px 0', color: '#c2185b' }}>Unified Count</h4>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#c2185b' }}>
              {filteredUnified.length}
            </div>
          </div>
        </div>
      </div>



      {/* Unified Market Value Grid */}
      <div style={{ 
        marginBottom: '20px',
        border: '2px solid #6f42c1',
        borderRadius: '8px',
        padding: '15px',
        backgroundColor: '#f8f9fa'
      }}>
        <h3 style={{ 
          margin: '0 0 10px 0', 
          color: '#6f42c1',
          fontSize: '20px',
          fontWeight: 'bold'
        }}>
          🔗 Unified Market Values - Price Consistent Holdings & Orders ({filteredUnified.length} records)
          {(selectedAccount || selectedInstrument) && (
            <span style={{ fontSize: '14px', color: '#6c757d', fontWeight: 'normal' }}>
              {' '}- Filtered by {selectedAccount && `Account: ${selectedAccount}`}
              {selectedAccount && selectedInstrument && ', '}
              {selectedInstrument && `Instrument: ${selectedInstrument}`}
            </span>
          )}
        </h3>
        <div style={{ 
          marginBottom: '10px', 
          padding: '10px', 
          backgroundColor: '#e7e3ff', 
          borderRadius: '6px',
          fontSize: '14px',
          color: '#6f42c1'
        }}>
          <strong>🎯 Price Consistency Guarantee:</strong> This unified view ensures holdings and orders use identical prices and timestamps for market value calculations, eliminating price inconsistencies between separate processing streams.
        </div>
        <div className="ag-theme-alpine" style={{ height: '600px', width: '100%' }}>
          <AgGridReact
            columnDefs={unifiedColumnDefs}
            rowData={filteredUnified}
            defaultColDef={defaultColDef}
            pagination={true}
            paginationPageSize={25}
            suppressRowClickSelection={true}
            rowSelection="multiple"
            animateRows={true}
            onGridReady={onUnifiedGridReady}
          />
        </div>
      </div>

      {/* Data Pipeline Information */}
      <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
        <h3 style={{ color: '#495057', marginBottom: '15px' }}>📊 Data Pipeline Information</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          <div>
            <h4 style={{ color: '#6f42c1', marginBottom: '10px' }}>🔗 Unified Data Flow</h4>
            <ol style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Base Data:</strong> SOD Holdings + Orders + Instruments + Prices</li>
              <li><strong>Flink Processing:</strong> Unified stream with shared price state</li>
              <li><strong>Kafka Topic:</strong> aggregation.unified-mv</li>
              <li><strong>View Server:</strong> Consumes and caches in Redis</li>
              <li><strong>React UI:</strong> Price-consistent display</li>
            </ol>
          </div>
          
          <div>
            <h4 style={{ color: '#28a745', marginBottom: '10px' }}>🎯 Price Consistency Benefits</h4>
            <ul style={{ paddingLeft: '20px', lineHeight: '1.6' }}>
              <li><strong>Shared State:</strong> Holdings and orders use identical price data</li>
              <li><strong>Atomic Updates:</strong> Price changes update all records simultaneously</li>
              <li><strong>Simplified Architecture:</strong> Single job instead of multiple streams</li>
              <li><strong>Operational Efficiency:</strong> Easier monitoring and maintenance</li>
              <li><strong>Data Integrity:</strong> Eliminates price inconsistencies</li>
            </ul>
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
        
        {(filteredUnified.length === 0) && (
          <div style={{ 
            marginTop: '20px', 
            padding: '20px', 
            backgroundColor: '#fff3cd', 
            borderRadius: '8px',
            border: '1px solid #ffeaa7'
          }}>
            <h4 style={{ color: '#856404', marginBottom: '10px' }}>🚀 Getting Started</h4>
            <p style={{ margin: '0', lineHeight: '1.6', color: '#856404' }}>
              No unified market value data found. To populate this view:
              <br />1. Ensure the Mock Data Generator is running and has initialized base data
              <br />2. Run the UnifiedMarketValue Flink job to process base data into unified market value records
              <br />3. The data will automatically appear here once processing is complete
              <br />4. The Unified view provides price consistency between holdings and orders
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

export default AggregationLayer 