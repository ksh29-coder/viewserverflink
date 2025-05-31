import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './index.css'

// Global error handler to catch uncaught exceptions
window.addEventListener('error', (event) => {
  console.error('🚨 Global JavaScript Error:', event.error)
  console.error('Error details:', {
    message: event.message,
    filename: event.filename,
    lineno: event.lineno,
    colno: event.colno,
    error: event.error
  })
})

// Handle unhandled promise rejections
window.addEventListener('unhandledrejection', (event) => {
  console.error('🚨 Unhandled Promise Rejection:', event.reason)
  console.error('Promise rejection details:', event)
})

// Add some debugging info
console.log('🚀 View Server React UI starting...')
console.log('Current URL:', window.location.href)
console.log('Base URL:', window.location.origin)

try {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </React.StrictMode>,
  )
  console.log('✅ React app rendered successfully')
} catch (error) {
  console.error('❌ Failed to render React app:', error)
  
  // Fallback error display
  const root = document.getElementById('root')
  if (root) {
    root.innerHTML = `
      <div style="padding: 40px; background-color: #f8d7da; color: #721c24; border-radius: 8px; margin: 20px; font-family: monospace;">
        <h2>⚠️ Failed to Load Application</h2>
        <p>The React application failed to start. Please check the browser console for more details.</p>
        <button onclick="window.location.reload()" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
          🔄 Refresh Page
        </button>
        <details style="margin-top: 20px; font-size: 12px;">
          <summary style="cursor: pointer; font-weight: bold;">🔍 Error Details</summary>
          <pre style="background-color: #f8f9fa; padding: 10px; border-radius: 4px; margin-top: 10px; white-space: pre-wrap; word-break: break-word;">
            ${error.toString()}
          </pre>
        </details>
      </div>
    `
  }
} 