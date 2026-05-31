#!/bin/bash

# Enclave Local Development Setup Script
# This script sets up Enclave backend and Android client for local testing

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║          Enclave Local Development Setup                      ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
echo -e "${BLUE}1️⃣  Checking Docker...${NC}"
if ! docker ps &> /dev/null; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"

# Start Backend Services
echo ""
echo -e "${BLUE}2️⃣  Starting Supabase Stack...${NC}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../backend/server"

if docker-compose ps | grep -q "supabase-db.*Up"; then
    echo "✓ Services already running"
else
    echo "Starting Docker Compose..."
    docker-compose up -d
    echo "Waiting for services to stabilize..."
    sleep 20
fi

# Verify services
echo ""
echo -e "${BLUE}3️⃣  Verifying Services...${NC}"

echo -n "  PostgreSQL: "
docker exec supabase-db pg_isready > /dev/null && echo -e "${GREEN}✓${NC}" || echo -e "${YELLOW}⚠${NC}"

echo -n "  Kong Gateway (port 8000): "
curl -s http://localhost:8000/ > /dev/null && echo -e "${GREEN}✓${NC}" || echo -e "${YELLOW}⚠${NC}"

echo -n "  Signaling Server (port 8085): "
curl -s http://localhost:8085/healthz > /dev/null && echo -e "${GREEN}✓${NC}" || echo -e "${YELLOW}⚠${NC}"

echo -n "  Supabase Studio (port 3000): "
curl -s http://localhost:3000/ > /dev/null && echo -e "${GREEN}✓${NC}" || echo -e "${YELLOW}⚠${NC}"

echo -n "  Ntfy Push Server (port 2586): "
curl -s http://localhost:2586/v1/health > /dev/null && echo -e "${GREEN}✓${NC}" || echo -e "${YELLOW}⚠${NC}"

# Setup Android Client
echo ""
echo -e "${BLUE}4️⃣  Setting up Android Client...${NC}"
cd "$SCRIPT_DIR/../apps/android"

if [ ! -f "local.properties" ]; then
    echo "Creating local.properties..."
    cp local.properties.example local.properties
    
    # Update with local dev configuration
    sed -i 's|SIGNALING_SERVER_URL=.*|SIGNALING_SERVER_URL=ws://10.0.2.2:8085|' local.properties
    sed -i 's|SUPABASE_URL=.*|SUPABASE_URL=http://10.0.2.2:8000|' local.properties
    
    echo -e "${GREEN}✓ local.properties created${NC}"
    echo "  Edit to add your configuration if needed"
else
    echo "✓ local.properties already exists"
fi

# Summary
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo -e "${GREEN}✅ SETUP COMPLETE!${NC}"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

echo "📊 Access Points:"
echo "   Supabase Studio: http://localhost:3000"
echo "   Kong Gateway:    http://localhost:8000"
echo "   Signaling:       http://localhost:8085"
echo "   PostgreSQL:      localhost:5432"
echo ""

echo "📱 Next Steps for Android Testing:"
echo "   1. Update apps/android/local.properties if needed"
echo "   2. Build APK: cd apps/android && ./gradlew assembleDebug"
echo "   3. Install: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "   4. Test sending messages in the app"
echo ""

echo "📚 For complete setup guide:"
echo "   See: docs/SETUP_GUIDE.md"
echo ""

echo "🔍 To check service health anytime:"
echo "   curl http://localhost:8085/healthz"
echo "   curl http://localhost:8000/"
echo ""
