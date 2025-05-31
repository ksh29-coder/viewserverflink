import React, { useState, useEffect, useMemo, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'

// CSS styles for cell flashing animations
const cellFlashStyles = `
  .cell-flash-green {
    animation: flashGreen 1.5s ease-out;
  }
  
  .cell-flash-red {
    animation: flashRed 1.5s ease-out;
  }
  
  .cell-flash-blue {
    animation: flashBlue 1.5s ease-out;
  }
  
  @keyframes flashGreen {
    0% { background-color: #d4edda; }
    25% { background-color: #c3e6cb; }
    50% { background-color: #b1dfbb; }
    75% { background-color: #c3e6cb; }
    100% { background-color: transparent; }
  }
  
  @keyframes flashRed {
    0% { background-color: #f8d7da; }
    25% { background-color: #f5c6cb; }
    50% { background-color: #f1b0b7; }
    75% { background-color: #f5c6cb; }
    100% { background-color: transparent; }
  }
  
  @keyframes flashBlue {
    0% { background-color: #d1ecf1; }
    25% { background-color: #bee5eb; }
    50% { background-color: #abdde5; }
    75% { background-color: #bee5eb; }
    100% { background-color: transparent; }
  }
`;

// Safely inject CSS styles into the document
const injectStyles = () => {
  try {
    if (typeof document !== 'undefined') {
      const existingStyle = document.head.querySelector('style[data-component="dynamic-grid"]');
      if (!existingStyle) {
        const styleElement = document.createElement('style');
        styleElement.textContent = cellFlashStyles;
        styleElement.setAttribute('data-component', 'dynamic-grid');
        document.head.appendChild(styleElement);
      }
    }
  } catch (error) {
    console.warn('Failed to inject CSS styles:', error);
  }
};

// Inject styles
injectStyles();

const DynamicGrid = ({ data = [], groupByFields = [], exposureTypes = [], loading = false }) => {
  const [gridApi, setGridApi] = useState(null)
  const [previousData, setPreviousData] = useState({})
  const flashingCells = useRef(new Map())
  const [error, setError] = useState(null)

  // Helper functions - moved before useMemo to avoid reference errors
  const getFieldDisplayName = (fieldName) => {
    const displayNames = {
      accountName: 'Account Name',
      instrumentName: 'Instrument',
      instrumentType: 'Type',
      currency: 'Currency',
      countryOfRisk: 'Risk Country',
      countryOfDomicile: 'Domicile',
      sector: 'Sector',
      orderStatus: 'Order Status',
      orderType: 'Order Type',
      venue: 'Venue'
    }
    return displayNames[fieldName] || fieldName
  }

  const getExposureConfig = (exposureType) => {
    const configs = {
      SOD: {
        displayName: 'SOD NAV',
        fieldName: 'sodNavUSD',
        color: '#28a745'
      },
      CURRENT: {
        displayName: 'Current NAV',
        fieldName: 'currentNavUSD',
        color: '#007bff'
      },
      EXPECTED: {
        displayName: 'Expected NAV',
        fieldName: 'expectedNavUSD',
        color: '#ffc107'
      }
    }
    return configs[exposureType] || {
      displayName: exposureType,
      fieldName: exposureType.toLowerCase() + 'NavUSD',
      color: '#6c757d'
    }
  }

  // Function to determine flash color based on change type
  const getFlashColor = (fieldName, oldValue, newValue) => {
    try {
      // For NAV fields, use green for increases, red for decreases
      if (fieldName && (fieldName.includes('Nav') || fieldName.includes('marketValue'))) {
        const oldNum = parseFloat(oldValue) || 0;
        const newNum = parseFloat(newValue) || 0;
        if (newNum > oldNum) return 'green'
        if (newNum < oldNum) return 'red'
      }
      // For other fields, use blue for general changes
      return 'blue'
    } catch (error) {
      console.warn('Error determining flash color:', error);
      return 'blue'
    }
  }

  // Custom cell renderer that handles flashing
  const FlashingCellRenderer = (params) => {
    try {
      const { value, data, colDef } = params || {}
      if (!data || !colDef) return <div>{value || ''}</div>
      
      const cellKey = `${data?.rowKey || data?.viewId || 'unknown'}-${colDef.field || 'unknown'}`
      const shouldFlash = flashingCells.current.has(cellKey)

      // Get the appropriate flash class
      const flashClass = shouldFlash ? 
        flashingCells.current.get(`${cellKey}-class`) || 'cell-flash-blue' : ''

      const displayValue = (() => {
        try {
          if (colDef.valueFormatter) {
            return colDef.valueFormatter(params)
          }
          return value || ''
        } catch (error) {
          console.warn('Error formatting cell value:', error);
          return value || ''
        }
      })()

      return (
        <div className={flashClass} style={{ height: '100%', display: 'flex', alignItems: 'center' }}>
          {displayValue}
        </div>
      )
    } catch (error) {
      console.warn('Error in FlashingCellRenderer:', error);
      return <div>{params?.value || ''}</div>
    }
  }

  // Enhanced column definitions with flashing cells
  const columnDefs = useMemo(() => {
    try {
      const columns = []

      // Add grouping field columns
      if (Array.isArray(groupByFields)) {
        groupByFields.forEach((fieldName, index) => {
          try {
            const displayName = getFieldDisplayName(fieldName)
            
            columns.push({
              headerName: displayName,
              field: `groupingFields.${fieldName}`,
              valueGetter: (params) => {
                try {
                  return params.data?.groupingFields?.[fieldName] || ''
                } catch (error) {
                  console.warn('Error getting grouping field value:', error);
                  return ''
                }
              },
              cellRenderer: FlashingCellRenderer,
              pinned: index === 0 ? 'left' : null,
              width: index === 0 ? 200 : 150,
              sortable: true,
              filter: true,
              resizable: true
            })
          } catch (error) {
            console.warn('Error creating grouping column:', error);
          }
        })
      }

      // Add exposure type columns with enhanced flashing
      if (Array.isArray(exposureTypes)) {
        exposureTypes.forEach(exposureType => {
          try {
            const config = getExposureConfig(exposureType)
            
            columns.push({
              headerName: config.displayName,
              field: config.fieldName,
              valueGetter: (params) => {
                try {
                  const value = params.data?.[config.fieldName]
                  return value ? parseFloat(value) : 0
                } catch (error) {
                  console.warn('Error getting exposure value:', error);
                  return 0
                }
              },
              valueFormatter: (params) => {
                try {
                  if (params.value == null || isNaN(params.value)) return '$0.00'
                  return '$' + params.value.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                  })
                } catch (error) {
                  console.warn('Error formatting exposure value:', error);
                  return '$0.00'
                }
              },
              cellRenderer: FlashingCellRenderer,
              type: 'numericColumn',
              width: 150,
              sortable: true,
              filter: 'agNumberColumnFilter',
              resizable: true,
              cellStyle: {
                textAlign: 'right',
                fontWeight: 'bold',
                color: config.color
              },
              headerClass: 'numeric-header'
            })
          } catch (error) {
            console.warn('Error creating exposure column:', error);
          }
        })
      }

      // Add metadata columns with flashing
      columns.push(
        {
          headerName: 'Last Updated',
          field: 'lastUpdated',
          valueFormatter: (params) => {
            try {
              if (!params.value) return ''
              const date = new Date(params.value)
              return date.toLocaleTimeString()
            } catch (error) {
              console.warn('Error formatting timestamp:', error);
              return ''
            }
          },
          cellRenderer: FlashingCellRenderer,
          width: 120,
          sortable: true,
          filter: 'agDateColumnFilter'
        },
        {
          headerName: 'Records',
          field: 'recordCount',
          type: 'numericColumn',
          cellRenderer: FlashingCellRenderer,
          width: 100,
          sortable: true,
          filter: 'agNumberColumnFilter',
          cellStyle: { textAlign: 'center' }
        }
      )

      return columns
    } catch (error) {
      console.error('Error creating column definitions:', error);
      setError('Error creating column definitions');
      return []
    }
  }, [groupByFields, exposureTypes])

  // Enhanced change detection and flashing logic
  useEffect(() => {
    try {
      if (!data || !Array.isArray(data)) return

      // Build a map of current data for comparison
      const currentDataMap = {}
      data.forEach(row => {
        try {
          const rowKey = row?.rowKey || row?.viewId || JSON.stringify(row?.groupingFields) || Math.random().toString()
          currentDataMap[rowKey] = row
        } catch (error) {
          console.warn('Error processing row data:', error);
        }
      })

      // Compare with previous data and trigger flashes
      Object.entries(currentDataMap).forEach(([rowKey, currentRow]) => {
        try {
          const previousRow = previousData[rowKey]
          
          if (previousRow && currentRow) {
            // Check each field for changes
            Object.keys(currentRow).forEach(fieldName => {
              try {
                const currentValue = currentRow[fieldName]
                const previousValue = previousRow[fieldName]
                
                // Skip non-comparable values
                if (typeof currentValue === 'object' && currentValue !== null) return
                if (typeof previousValue === 'object' && previousValue !== null) return
                
                // Detect changes
                if (currentValue !== previousValue && previousValue !== undefined) {
                  const cellKey = `${rowKey}-${fieldName}`
                  const flashColor = getFlashColor(fieldName, previousValue, currentValue)
                  
                  // Add flash class
                  flashingCells.current.set(cellKey, true)
                  flashingCells.current.set(`${cellKey}-class`, `cell-flash-${flashColor}`)
                  
                  console.log(`🎯 Cell change detected: ${fieldName} changed from ${previousValue} to ${currentValue} (flash: ${flashColor})`)
                  
                  // Remove flash after animation duration
                  setTimeout(() => {
                    try {
                      flashingCells.current.delete(cellKey)
                      flashingCells.current.delete(`${cellKey}-class`)
                      
                      // Trigger grid refresh to remove flash class
                      if (gridApi && gridApi.refreshCells) {
                        gridApi.refreshCells()
                      }
                    } catch (error) {
                      console.warn('Error removing flash:', error);
                    }
                  }, 1500)
                }
              } catch (error) {
                console.warn('Error processing field change:', error);
              }
            })
            
            // Check groupingFields separately
            if (currentRow.groupingFields && previousRow.groupingFields) {
              Object.keys(currentRow.groupingFields).forEach(fieldName => {
                try {
                  const currentValue = currentRow.groupingFields[fieldName]
                  const previousValue = previousRow.groupingFields[fieldName]
                  
                  if (currentValue !== previousValue && previousValue !== undefined) {
                    const cellKey = `${rowKey}-groupingFields.${fieldName}`
                    flashingCells.current.set(cellKey, true)
                    flashingCells.current.set(`${cellKey}-class`, 'cell-flash-blue')
                    
                    console.log(`🎯 Grouping field change: ${fieldName} changed from ${previousValue} to ${currentValue}`)
                    
                    setTimeout(() => {
                      try {
                        flashingCells.current.delete(cellKey)
                        flashingCells.current.delete(`${cellKey}-class`)
                        if (gridApi && gridApi.refreshCells) {
                          gridApi.refreshCells()
                        }
                      } catch (error) {
                        console.warn('Error removing grouping flash:', error);
                      }
                    }, 1500)
                  }
                } catch (error) {
                  console.warn('Error processing grouping field change:', error);
                }
              })
            }
          }
        } catch (error) {
          console.warn('Error processing row changes:', error);
        }
      })

      // Update previous data reference
      setPreviousData(currentDataMap)
    } catch (error) {
      console.error('Error in change detection logic:', error);
      setError('Error detecting data changes');
    }
  }, [data, gridApi])

  // Grid options
  const gridOptions = useMemo(() => {
    try {
      return {
        columnDefs,
        rowData: data,
        defaultColDef: {
          sortable: true,
          filter: true,
          resizable: true,
          minWidth: 100
        },
        animateRows: true,
        enableRangeSelection: true,
        enableCellTextSelection: true,
        suppressRowClickSelection: true,
        pagination: true,
        paginationPageSize: 50,
        paginationPageSizeSelector: [25, 50, 100, 200],
        getRowId: (params) => {
          try {
            return params.data?.rowKey || params.data?.viewId || Math.random().toString()
          } catch (error) {
            console.warn('Error getting row ID:', error);
            return Math.random().toString()
          }
        },
        onGridReady: (params) => {
          try {
            setGridApi(params.api)
            if (params.api && params.api.sizeColumnsToFit) {
              params.api.sizeColumnsToFit()
            }
          } catch (error) {
            console.warn('Error in onGridReady:', error);
          }
        },
        onFirstDataRendered: (params) => {
          try {
            if (params.api && params.api.sizeColumnsToFit) {
              params.api.sizeColumnsToFit()
            }
          } catch (error) {
            console.warn('Error in onFirstDataRendered:', error);
          }
        }
      }
    } catch (error) {
      console.error('Error creating grid options:', error);
      setError('Error configuring grid');
      return {}
    }
  }, [columnDefs, data])

  // Update grid data when data changes
  useEffect(() => {
    try {
      if (gridApi && gridApi.setRowData) {
        gridApi.setRowData(data)
        // Small delay to ensure flashing is visible after data update
        setTimeout(() => {
          try {
            if (gridApi && gridApi.refreshCells) {
              gridApi.refreshCells()
            }
          } catch (error) {
            console.warn('Error refreshing cells:', error);
          }
        }, 50)
      }
    } catch (error) {
      console.warn('Error updating grid data:', error);
    }
  }, [data, gridApi])

  // Auto-size columns when configuration changes
  useEffect(() => {
    try {
      if (gridApi && gridApi.sizeColumnsToFit) {
        setTimeout(() => {
          try {
            gridApi.sizeColumnsToFit()
          } catch (error) {
            console.warn('Error sizing columns to fit:', error);
          }
        }, 100)
      }
    } catch (error) {
      console.warn('Error in column sizing effect:', error);
    }
  }, [groupByFields, exposureTypes, gridApi])

  // Error boundary for rendering
  if (error) {
    return (
      <div style={{ 
        padding: '20px',
        backgroundColor: '#f8d7da',
        color: '#721c24',
        borderRadius: '8px',
        border: '1px solid #f5c6cb'
      }}>
        <h4>Grid Error</h4>
        <p>{error}</p>
        <button onClick={() => setError(null)}>Retry</button>
      </div>
    )
  }

  if (loading) {
    return (
      <div style={{ 
        height: '400px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f8f9fa',
        borderRadius: '8px',
        border: '1px solid #dee2e6'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '18px', marginBottom: '10px' }}>🔄</div>
          <div>Loading view data...</div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ marginBottom: '20px' }}>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        marginBottom: '10px'
      }}>
        <h3 style={{ margin: 0, color: '#333' }}>
          Portfolio Data Grid
          <span style={{ fontSize: '12px', color: '#28a745', marginLeft: '10px' }}>
            ✨ Live Updates with Cell Flashing
          </span>
        </h3>
        <div style={{ fontSize: '14px', color: '#6c757d' }}>
          {data?.length || 0} rows • Real-time updates
        </div>
      </div>

      {/* Flash legend */}
      <div style={{ 
        marginBottom: '10px', 
        padding: '8px 12px', 
        backgroundColor: '#f8f9fa', 
        borderRadius: '4px',
        border: '1px solid #dee2e6',
        fontSize: '12px',
        color: '#6c757d'
      }}>
        <strong>💡 Cell Flash Legend:</strong>
        <span style={{ marginLeft: '10px', padding: '2px 6px', backgroundColor: '#d4edda', borderRadius: '3px' }}>
          Green = Value Increased
        </span>
        <span style={{ marginLeft: '6px', padding: '2px 6px', backgroundColor: '#f8d7da', borderRadius: '3px' }}>
          Red = Value Decreased
        </span>
        <span style={{ marginLeft: '6px', padding: '2px 6px', backgroundColor: '#d1ecf1', borderRadius: '3px' }}>
          Blue = Other Changes
        </span>
      </div>

      <div 
        className="ag-theme-alpine" 
        style={{ 
          height: '500px',
          width: '100%',
          border: '1px solid #dee2e6',
          borderRadius: '8px'
        }}
      >
        <AgGridReact {...gridOptions} />
      </div>

      {(!data || data.length === 0) && !loading && (
        <div style={{
          textAlign: 'center',
          padding: '40px',
          color: '#6c757d',
          backgroundColor: '#f8f9fa',
          borderRadius: '8px',
          border: '1px solid #dee2e6',
          marginTop: '10px'
        }}>
          <div style={{ fontSize: '48px', marginBottom: '10px' }}>📊</div>
          <div style={{ fontSize: '18px', marginBottom: '5px' }}>No Data Available</div>
          <div style={{ fontSize: '14px' }}>
            Configure your view and ensure the WebSocket connection is established
          </div>
        </div>
      )}

      {/* Grid controls */}
      <div style={{ 
        marginTop: '10px',
        display: 'flex',
        gap: '10px',
        flexWrap: 'wrap'
      }}>
        <button
          onClick={() => {
            try {
              if (gridApi && gridApi.exportDataAsCsv) {
                gridApi.exportDataAsCsv()
              }
            } catch (error) {
              console.warn('Error exporting CSV:', error);
            }
          }}
          disabled={!gridApi || !data || data.length === 0}
          style={{
            padding: '6px 12px',
            backgroundColor: '#28a745',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '12px',
            opacity: (!gridApi || !data || data.length === 0) ? 0.5 : 1
          }}
        >
          📥 Export CSV
        </button>
        
        <button
          onClick={() => {
            try {
              if (gridApi && gridApi.sizeColumnsToFit) {
                gridApi.sizeColumnsToFit()
              }
            } catch (error) {
              console.warn('Error fitting columns:', error);
            }
          }}
          disabled={!gridApi}
          style={{
            padding: '6px 12px',
            backgroundColor: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '12px',
            opacity: !gridApi ? 0.5 : 1
          }}
        >
          📐 Fit Columns
        </button>
        
        <button
          onClick={() => {
            try {
              if (gridApi && gridApi.autoSizeAllColumns) {
                gridApi.autoSizeAllColumns()
              }
            } catch (error) {
              console.warn('Error auto-sizing columns:', error);
            }
          }}
          disabled={!gridApi}
          style={{
            padding: '6px 12px',
            backgroundColor: '#6c757d',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '12px',
            opacity: !gridApi ? 0.5 : 1
          }}
        >
          📏 Auto Size
        </button>
      </div>
    </div>
  )
}

export default DynamicGrid 