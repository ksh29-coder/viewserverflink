#!/bin/bash

# Open View Server Diagnostic UI
# Opens the UI in the default browser

echo "🌐 Opening View Server Diagnostic UI"
echo "===================================="
echo "URL: http://localhost:8080"
echo
echo "Make sure the view server is running on port 8080"

# Check if view server is running
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ View server is accessible"
    echo "📱 Opening in browser..."
    
    # Open in default browser based on OS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        open http://localhost:8080
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        xdg-open http://localhost:8080
    elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
        # Windows
        start http://localhost:8080
    else
        echo "Please open http://localhost:8080 in your browser"
    fi
else
    echo "❌ View server is not accessible at http://localhost:8080"
    echo "   Please start it with: ./scripts/start-view-server.sh"
fi 