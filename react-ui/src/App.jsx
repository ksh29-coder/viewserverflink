import React from 'react'
import { Routes, Route, Link, useLocation } from 'react-router-dom'
import BaseDataLayer from './pages/BaseDataLayer'
import AggregationLayer from './pages/AggregationLayer'
import AccountOverview from './pages/AccountOverview'

// Error Boundary Component
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null, errorInfo: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true }
  }

  componentDidCatch(error, errorInfo) {
    console.error('React Error Boundary caught an error:', error, errorInfo)
    this.setState({
      error: error,
      errorInfo: errorInfo
    })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          padding: '40px',
          backgroundColor: '#f8d7da',
          color: '#721c24',
          borderRadius: '8px',
          border: '1px solid #f5c6cb',
          margin: '20px',
          fontFamily: 'monospace'
        }}>
          <h2>⚠️ Something went wrong</h2>
          <p>The application encountered an unexpected error. Please refresh the page or check the console for more details.</p>
          
          <div style={{ marginTop: '20px' }}>
            <button 
              onClick={() => window.location.reload()} 
              style={{
                padding: '10px 20px',
                backgroundColor: '#007bff',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                marginRight: '10px'
              }}
            >
              🔄 Refresh Page
            </button>
            
            <button 
              onClick={() => this.setState({ hasError: false, error: null, errorInfo: null })} 
              style={{
                padding: '10px 20px',
                backgroundColor: '#28a745',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              🔁 Try Again
            </button>
          </div>

          <details style={{ marginTop: '20px', fontSize: '12px' }}>
            <summary style={{ cursor: 'pointer', fontWeight: 'bold' }}>🔍 Error Details</summary>
            <pre style={{ 
              backgroundColor: '#f8f9fa', 
              padding: '10px', 
              borderRadius: '4px',
              marginTop: '10px',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word'
            }}>
              {this.state.error && this.state.error.toString()}
              <br />
              {this.state.errorInfo.componentStack}
            </pre>
          </details>
        </div>
      )
    }

    return this.props.children
  }
}

function App() {
  const location = useLocation()

  const isActive = (path) => {
    return location.pathname === path
  }

  return (
    <ErrorBoundary>
      <div className="app">
        {/* Sidebar */}
        <div className="sidebar">
          <div className="sidebar-header">
            <h1 className="sidebar-title">View Server</h1>
            <p className="sidebar-subtitle">Financial Data Dashboard</p>
          </div>
          
          <nav className="sidebar-nav">
            <div className="nav-section">
              <div className="nav-section-title">Data Layers</div>
              <Link 
                to="/" 
                className={`nav-item ${isActive('/') ? 'active' : ''}`}
              >
                <span className="nav-item-icon">📊</span>
                Base Data Layer
              </Link>
              <Link 
                to="/aggregation" 
                className={`nav-item ${isActive('/aggregation') ? 'active' : ''}`}
              >
                <span className="nav-item-icon">🔄</span>
                Aggregation Layer
              </Link>
            </div>
            
            <div className="nav-section">
              <div className="nav-section-title">Views</div>
              <Link 
                to="/account-overview" 
                className={`nav-item ${isActive('/account-overview') ? 'active' : ''}`}
              >
                <span className="nav-item-icon">📈</span>
                Account Overview
              </Link>
            </div>
            
            <div className="nav-section">
              <div className="nav-section-title">System</div>
              <a 
                href="http://localhost:8080/api/health" 
                target="_blank" 
                rel="noopener noreferrer"
                className="nav-item"
              >
                <span className="nav-item-icon">❤️</span>
                Health Check
              </a>
              <a 
                href="http://localhost:8081/api/data-generation/status" 
                target="_blank" 
                rel="noopener noreferrer"
                className="nav-item"
              >
                <span className="nav-item-icon">🏭</span>
                Data Generator
              </a>
            </div>
          </nav>
        </div>

        {/* Main Content */}
        <div className="main-content">
          <ErrorBoundary>
            <Routes>
              <Route path="/" element={<BaseDataLayer />} />
              <Route path="/aggregation" element={<AggregationLayer />} />
              <Route path="/account-overview" element={<AccountOverview />} />
            </Routes>
          </ErrorBoundary>
        </div>
      </div>
    </ErrorBoundary>
  )
}

export default App 