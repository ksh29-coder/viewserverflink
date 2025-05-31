import React, { useState, useEffect, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'
import { apiService } from '../services/apiService'
import AccountSelector from '../components/AccountSelector'
import GroupBySelector from '../components/GroupBySelector'
import ExposureSelector from '../components/ExposureSelector'
import DynamicGrid from '../components/DynamicGrid'
import ViewConfigPanel from '../components/ViewConfigPanel'

// ==================== VIEW PERSISTENCE UTILITIES ====================

const VIEW_STORAGE_KEY = 'account_overview_current_view'
const VIEW_CONFIG_STORAGE_KEY = 'account_overview_view_config'

// Save current view ID to localStorage
const saveCurrentViewId = (viewId) => {
  try {
    localStorage.setItem(VIEW_STORAGE_KEY, viewId)
    console.log('📁 Saved current view ID to localStorage:', viewId)
  } catch (error) {
    console.warn('Failed to save view ID to localStorage:', error)
  }
}

// Get saved view ID from localStorage
const getSavedViewId = () => {
  try {
    const savedViewId = localStorage.getItem(VIEW_STORAGE_KEY)
    console.log('📁 Retrieved saved view ID from localStorage:', savedViewId)
    return savedViewId
  } catch (error) {
    console.warn('Failed to retrieve view ID from localStorage:', error)
    return null
  }
}

// Save view configuration to localStorage
const saveViewConfig = (config) => {
  try {
    localStorage.setItem(VIEW_CONFIG_STORAGE_KEY, JSON.stringify(config))
    console.log('📁 Saved view configuration to localStorage:', config)
  } catch (error) {
    console.warn('Failed to save view config to localStorage:', error)
  }
}

// Get saved view configuration from localStorage
const getSavedViewConfig = () => {
  try {
    const savedConfig = localStorage.getItem(VIEW_CONFIG_STORAGE_KEY)
    if (savedConfig) {
      const parsed = JSON.parse(savedConfig)
      console.log('📁 Retrieved saved view configuration from localStorage:', parsed)
      return parsed
    }
  } catch (error) {
    console.warn('Failed to retrieve view config from localStorage:', error)
  }
  return null
}

// Clear saved view data
const clearSavedViewData = () => {
  try {
    localStorage.removeItem(VIEW_STORAGE_KEY)
    localStorage.removeItem(VIEW_CONFIG_STORAGE_KEY)
    console.log('📁 Cleared saved view data from localStorage')
  } catch (error) {
    console.warn('Failed to clear view data from localStorage:', error)
  }
}

const AccountOverview = () => {
  // View configuration state
  const [selectedAccounts, setSelectedAccounts] = useState([])
  const [groupByFields, setGroupByFields] = useState(['accountName', 'instrumentName'])
  const [exposureTypes, setExposureTypes] = useState(['SOD', 'CURRENT', 'EXPECTED'])
  
  // Data and UI state
  const [accounts, setAccounts] = useState([])
  const [groupByFieldOptions, setGroupByFieldOptions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  // Current view state
  const [currentViewId, setCurrentViewId] = useState(null)
  const [viewData, setViewData] = useState([])
  const [activeViews, setActiveViews] = useState([])
  const [viewLoadedFromStorage, setViewLoadedFromStorage] = useState(false)
  
  // WebSocket connection
  const wsRef = useRef(null)
  const [wsConnected, setWsConnected] = useState(false)

  // Load initial data and check for saved view
  useEffect(() => {
    loadInitialData()
  }, [])

  // 🆕 Check for saved view on component mount
  useEffect(() => {
    checkForSavedView()
  }, [accounts, groupByFieldOptions])

  // Auto-select all accounts when accounts are loaded (only if no saved config)
  useEffect(() => {
    if (accounts.length > 0 && selectedAccounts.length === 0 && !viewLoadedFromStorage) {
      setSelectedAccounts(accounts.map(acc => acc.accountId))
    }
  }, [accounts, viewLoadedFromStorage])

  // Create new view when configuration changes (but not when loading from storage)
  useEffect(() => {
    if (selectedAccounts.length > 0 && groupByFields.length > 0 && exposureTypes.length > 0 && !viewLoadedFromStorage) {
      createNewView()
    }
  }, [selectedAccounts, groupByFields, exposureTypes, viewLoadedFromStorage])

  const loadInitialData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      const [accountsData, groupByData, activeViewsData] = await Promise.all([
        apiService.getAccounts(),
        apiService.getGroupByFields(),
        apiService.getActiveViews()
      ])
      
      setAccounts(Array.from(accountsData))
      setGroupByFieldOptions(groupByData)
      setActiveViews(activeViewsData)
      
    } catch (err) {
      setError('Failed to load initial data: ' + err.message)
      console.error('Error loading initial data:', err)
    } finally {
      setLoading(false)
    }
  }

  // 🆕 Check for saved view and restore it
  const checkForSavedView = async () => {
    if (accounts.length === 0 || groupByFieldOptions.length === 0) {
      return // Wait for initial data to load
    }

    const savedViewId = getSavedViewId()
    const savedConfig = getSavedViewConfig()

    if (savedViewId && savedConfig) {
      console.log('🔄 Attempting to restore saved view:', savedViewId)
      
      try {
        // Check if the saved view still exists on the server
        const currentActiveViews = await apiService.getActiveViews()
        const viewExists = currentActiveViews.includes(savedViewId)
        
        if (viewExists) {
          console.log('✅ Saved view exists on server, restoring...')
          
          // Restore configuration
          setSelectedAccounts(savedConfig.selectedAccounts || [])
          setGroupByFields(savedConfig.groupByFields || ['accountName', 'instrumentName'])
          setExposureTypes(savedConfig.exposureTypes || ['SOD', 'CURRENT', 'EXPECTED'])
          setCurrentViewId(savedViewId)
          setViewLoadedFromStorage(true)
          
          // Load view data
          const viewData = await apiService.getViewData(savedViewId)
          setViewData(viewData)
          
          // Connect to WebSocket
          connectWebSocket(savedViewId)
          
          console.log('✅ Successfully restored saved view with', viewData.length, 'rows')
        } else {
          console.log('❌ Saved view no longer exists on server, clearing saved data')
          clearSavedViewData()
          setViewLoadedFromStorage(false)
        }
      } catch (error) {
        console.error('❌ Failed to restore saved view:', error)
        clearSavedViewData()
        setViewLoadedFromStorage(false)
        setError('Failed to restore saved view: ' + error.message)
      }
    } else {
      console.log('📝 No saved view found, proceeding with normal flow')
      setViewLoadedFromStorage(false)
    }
  }

  const createNewView = async () => {
    try {
      // Close existing WebSocket connection
      if (wsRef.current) {
        wsRef.current.close()
        setWsConnected(false)
      }

      // Destroy existing view
      if (currentViewId) {
        try {
          await apiService.deleteView(currentViewId)
        } catch (err) {
          console.warn('Failed to delete previous view:', err.message)
        }
      }

      // Create new view
      const request = {
        selectedAccounts: Array.from(selectedAccounts),
        groupByFields: groupByFields,
        exposureTypes: Array.from(exposureTypes),
        userId: 'demo-user', // TODO: Get from authentication
        viewName: `Account Overview - ${new Date().toLocaleTimeString()}`
      }

      console.log('Creating new view with request:', request)
      const response = await apiService.createAccountOverviewView(request)
      const newViewId = response.viewId

      setCurrentViewId(newViewId)
      setViewData([]) // Clear previous data

      // 🆕 Save view ID and configuration to localStorage
      saveCurrentViewId(newViewId)
      saveViewConfig({
        selectedAccounts: Array.from(selectedAccounts),
        groupByFields: groupByFields,
        exposureTypes: Array.from(exposureTypes)
      })

      // Establish WebSocket connection
      connectWebSocket(newViewId)

      // Refresh active views
      const updatedViews = await apiService.getActiveViews()
      setActiveViews(updatedViews)

    } catch (err) {
      setError('Failed to create view: ' + err.message)
      console.error('Error creating view:', err)
    }
  }

  const connectWebSocket = (viewId) => {
    try {
      const wsUrl = `ws://localhost:8080/ws/account-overview/${viewId}`
      console.log('Connecting to WebSocket:', wsUrl)
      
      const ws = new WebSocket(wsUrl)
      wsRef.current = ws

      ws.onopen = () => {
        console.log('WebSocket connected for view:', viewId)
        setWsConnected(true)
        setError(null)
      }

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          handleWebSocketMessage(message)
        } catch (err) {
          console.error('Error parsing WebSocket message:', err)
        }
      }

      ws.onclose = (event) => {
        console.log('WebSocket closed:', event.reason)
        setWsConnected(false)
      }

      ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        setWsConnected(false)
        setError('WebSocket connection failed')
      }

    } catch (err) {
      console.error('Error connecting WebSocket:', err)
      setError('Failed to connect WebSocket: ' + err.message)
    }
  }

  const handleWebSocketMessage = (message) => {
    console.log('WebSocket message received:', message)

    switch (message.type) {
      case 'CONNECTION_ESTABLISHED':
        console.log('WebSocket connection established:', message.message)
        break

      case 'GRID_UPDATE':
        handleGridUpdate(message)
        break

      case 'VIEW_READY':
        console.log('View ready with initial data:', message.initialData)
        if (message.initialData) {
          setViewData(message.initialData)
        }
        break

      case 'ERROR':
        console.error('WebSocket error message:', message.error)
        setError('View error: ' + message.error)
        break

      case 'PONG':
        // Handle ping/pong for keep-alive
        break

      default:
        console.log('Unknown WebSocket message type:', message.type)
    }
  }

  const handleGridUpdate = (message) => {
    if (!message.changes || message.changes.length === 0) {
      return
    }

    setViewData(prevData => {
      const newData = [...prevData]

      message.changes.forEach(change => {
        switch (change.changeType) {
          case 'INSERT':
            // Add new row
            if (change.completeRow) {
              newData.push(change.completeRow)
            }
            break

          case 'UPDATE':
            // Update existing row
            const rowIndex = newData.findIndex(row => row.rowKey === change.rowKey)
            if (rowIndex >= 0 && change.changedFields) {
              const updatedRow = { ...newData[rowIndex] }
              
              Object.entries(change.changedFields).forEach(([fieldName, fieldChange]) => {
                updatedRow[fieldName] = fieldChange.newValue
              })
              
              newData[rowIndex] = updatedRow
            }
            break

          case 'DELETE':
            // Remove row
            const deleteIndex = newData.findIndex(row => row.rowKey === change.rowKey)
            if (deleteIndex >= 0) {
              newData.splice(deleteIndex, 1)
            }
            break
        }
      })

      return newData
    })
  }

  const handleRefresh = () => {
    loadInitialData()
    if (selectedAccounts.length > 0) {
      createNewView()
    }
  }

  const handleDeleteView = async (viewId) => {
    try {
      await apiService.deleteView(viewId)
      
      // If this was the current view, clear it
      if (viewId === currentViewId) {
        setCurrentViewId(null)
        setViewData([])
        clearSavedViewData() // 🆕 Clear saved data when view is deleted
        if (wsRef.current) {
          wsRef.current.close()
        }
      }
      
      // Refresh active views
      const updatedViews = await apiService.getActiveViews()
      setActiveViews(updatedViews)
      
    } catch (err) {
      setError('Failed to delete view: ' + err.message)
    }
  }

  // 🆕 Handle manual view configuration changes (reset storage flag)
  const handleConfigurationChange = (setter) => {
    return (value) => {
      setViewLoadedFromStorage(false)
      setter(value)
    }
  }

  if (loading) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <div>Loading Account Overview...</div>
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
        <h2 style={{ margin: '0 0 20px 0', color: '#333' }}>
          Account Overview - Real-time Portfolio View
        </h2>
        
        {error && (
          <div style={{ 
            padding: '10px', 
            backgroundColor: '#f8d7da', 
            color: '#721c24',
            borderRadius: '4px',
            marginBottom: '20px'
          }}>
            {error}
            <button 
              onClick={() => setError(null)}
              style={{ 
                marginLeft: '10px', 
                padding: '2px 8px',
                backgroundColor: 'transparent',
                border: '1px solid #721c24',
                borderRadius: '3px',
                cursor: 'pointer'
              }}
            >
              ✕
            </button>
          </div>
        )}

        {/* Configuration Panel */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', 
          gap: '20px', 
          marginBottom: '20px',
          padding: '20px',
          backgroundColor: '#f8f9fa',
          borderRadius: '8px',
          border: '1px solid #dee2e6'
        }}>
          <AccountSelector
            accounts={accounts}
            selectedAccounts={selectedAccounts}
            onSelectionChange={handleConfigurationChange(setSelectedAccounts)}
          />
          
          <GroupBySelector
            options={groupByFieldOptions}
            selectedFields={groupByFields}
            onSelectionChange={handleConfigurationChange(setGroupByFields)}
          />
          
          <ExposureSelector
            selectedTypes={exposureTypes}
            onSelectionChange={handleConfigurationChange(setExposureTypes)}
          />
        </div>

        {/* Enhanced Status Panel with View ID Display */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '20px', 
          marginBottom: '20px',
          padding: '15px',
          backgroundColor: '#e7f3ff',
          borderRadius: '8px',
          border: '1px solid #b3d9ff',
          flexWrap: 'wrap'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontWeight: 'bold' }}>WebSocket:</span>
            <span style={{ 
              color: wsConnected ? '#28a745' : '#dc3545',
              fontWeight: 'bold'
            }}>
              {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
            </span>
          </div>
          
          {/* 🆕 Enhanced View ID Display */}
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: '10px',
            backgroundColor: currentViewId ? '#d4edda' : '#f8d7da',
            padding: '8px 12px',
            borderRadius: '6px',
            border: currentViewId ? '1px solid #c3e6cb' : '1px solid #f5c6cb'
          }}>
            <span style={{ fontWeight: 'bold' }}>Current View ID:</span>
            <span style={{ 
              fontFamily: 'monospace',
              fontSize: '0.9em',
              color: currentViewId ? '#155724' : '#721c24',
              fontWeight: 'bold'
            }}>
              {currentViewId || 'None'}
            </span>
            {currentViewId && (
              <button
                onClick={() => navigator.clipboard.writeText(currentViewId)}
                style={{
                  padding: '2px 6px',
                  fontSize: '0.8em',
                  backgroundColor: 'transparent',
                  border: '1px solid #155724',
                  borderRadius: '3px',
                  cursor: 'pointer',
                  color: '#155724'
                }}
                title="Copy View ID"
              >
                📋
              </button>
            )}
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontWeight: 'bold' }}>Rows:</span>
            <span>{viewData.length}</span>
          </div>

          {/* 🆕 Persistence Status Indicator */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontWeight: 'bold' }}>Persistence:</span>
            <span style={{ 
              color: currentViewId ? '#28a745' : '#6c757d',
              fontSize: '0.9em'
            }}>
              {currentViewId ? '💾 Saved' : '📝 Unsaved'}
            </span>
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
              fontWeight: 'bold'
            }}
          >
            🔄 Refresh
          </button>

          {/* 🆕 Clear Saved Data Button */}
          {currentViewId && (
            <button
              onClick={() => {
                clearSavedViewData()
                setError('Saved view data cleared. Refresh to start fresh.')
              }}
              style={{
                padding: '8px 16px',
                backgroundColor: '#ffc107',
                color: '#212529',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
              title="Clear saved view data from localStorage"
            >
              🗑️ Clear Saved
            </button>
          )}
        </div>
      </div>

      {/* Dynamic Grid */}
      <DynamicGrid
        data={viewData}
        groupByFields={groupByFields}
        exposureTypes={exposureTypes}
        loading={!wsConnected && viewData.length === 0}
      />

      {/* View Management Panel */}
      <ViewConfigPanel
        activeViews={activeViews}
        currentViewId={currentViewId}
        onDeleteView={handleDeleteView}
        onRefreshViews={loadInitialData}
      />
    </div>
  )
}

export default AccountOverview 