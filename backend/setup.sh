#!/bin/bash
# MOPR Backend Setup Script

echo "🚀 MOPR Backend Setup"
echo "===================="

# Step 1: Install dependencies
echo ""
echo "📦 Installing dependencies..."
npm install

# Step 2: Create .env file if not exists
if [ ! -f .env ]; then
    echo ""
    echo "📝 Creating .env file..."
    cp .env.example .env
    echo "⚠️  Please update .env with your configuration"
    echo "   - DB_HOST, DB_USER, DB_PASSWORD"
    echo "   - JWT_SECRET (change to something secure)"
    echo "   - SMTP credentials (already set with Gmail)"
else
    echo "✅ .env already exists"
fi

# Step 3: Show next steps
echo ""
echo "===================="
echo "✨ Setup Complete!"
echo ""
echo "📋 Next Steps:"
echo "1. Update .env with your database credentials"
echo "2. Run the schema.sql on your database"
echo "3. Start the server:"
echo ""
echo "   npm run dev     (Development mode)"
echo "   npm start       (Production mode)"
echo ""
echo "📚 Documentation:"
echo "   - README_OTP_FLOW.md (Backend flow details)"
echo "   - MOBILE_INTEGRATION_GUIDE.md (Mobile implementation)"
echo "   - ARCHITECTURE_BEST_PRACTICES.md (System design)"
echo ""

