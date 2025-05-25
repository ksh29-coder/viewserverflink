import React from 'react'
import { Routes, Route, Link, useLocation } from 'react-router-dom'
import BaseDataLayer from './pages/BaseDataLayer'
import AggregationLayer from './pages/AggregationLayer'

function App() {
  const location = useLocation()

  const isActive = (path) => {
    return location.pathname === path
  }

  return (
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
        <Routes>
          <Route path="/" element={<BaseDataLayer />} />
          <Route path="/aggregation" element={<AggregationLayer />} />
        </Routes>
      </div>
    </div>
  )
}

export default App 