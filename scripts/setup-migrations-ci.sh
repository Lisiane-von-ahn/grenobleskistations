#!/usr/bin/env bash
#
# GrenobleSki Migration CI/CD Setup Script
# Configures pre-commit hooks and validates local environment
#

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║  GrenobleSki Migration CI/CD Setup                     ║"
echo "║  Setting up local development environment              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check if we're in the right directory
if [ ! -f "manage.py" ]; then
    echo -e "${RED}❌ Error: manage.py not found${NC}"
    echo "Please run this script from the project root directory"
    exit 1
fi

echo -e "${BLUE}📋 Checking environment...${NC}"

# Check Python
if ! command -v python &> /dev/null && ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python not found${NC}"
    exit 1
fi

PYTHON=$(command -v python3 || command -v python)
PYTHON_VERSION=$($PYTHON --version 2>&1 | awk '{print $2}')
echo -e "${GREEN}✓ Python $PYTHON_VERSION${NC}"

# Check Django
if $PYTHON -m django --version &> /dev/null; then
    DJANGO_VERSION=$($PYTHON -m django --version)
    echo -e "${GREEN}✓ Django $DJANGO_VERSION${NC}"
else
    echo -e "${YELLOW}⚠ Django not installed${NC}"
    echo "  Run: pip install -r requirements.txt"
fi

# Check git
if ! command -v git &> /dev/null; then
    echo -e "${RED}❌ Git not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Git $(git --version | awk '{print $3}')${NC}"

echo ""
echo -e "${BLUE}🔧 Setting up pre-commit hooks...${NC}"

# Create git hooks directory if it doesn't exist
if [ ! -d ".git/hooks" ]; then
    mkdir -p ".git/hooks"
    echo -e "${GREEN}✓ Created .git/hooks${NC}"
fi

# Copy pre-commit hook
if [ -f ".githooks/pre-commit" ]; then
    cp .githooks/pre-commit .git/hooks/pre-commit 2>/dev/null || true
    chmod +x .git/hooks/pre-commit
    echo -e "${GREEN}✓ Installed pre-commit hook${NC}"
else
    echo -e "${YELLOW}⚠ .githooks/pre-commit not found${NC}"
fi

# Copy post-merge hook (for updating dependencies after pulls)
if [ -f ".githooks/post-merge" ]; then
    cp .githooks/post-merge .git/hooks/post-merge 2>/dev/null || true
    chmod +x .git/hooks/post-merge
    echo -e "${GREEN}✓ Installed post-merge hook${NC}"
fi

echo ""
echo -e "${BLUE}🧪 Testing Django environment...${NC}"

# Test Django check
if $PYTHON manage.py check &> /dev/null; then
    echo -e "${GREEN}✓ Django system checks passed${NC}"
else
    echo -e "${YELLOW}⚠ Django system checks found warnings${NC}"
    $PYTHON manage.py check
fi

# Test migrations
if $PYTHON manage.py makemigrations --check --noinput &> /dev/null; then
    echo -e "${GREEN}✓ All models are properly migrated${NC}"
else
    echo -e "${YELLOW}⚠ Unmigrated model changes detected${NC}"
    echo "  Run: python manage.py makemigrations && python manage.py migrate"
fi

# Test database
DB_CONFIGURED=false
if $PYTHON manage.py migrate --noinput &> /dev/null; then
    echo -e "${GREEN}✓ Database connection successful${NC}"
    DB_CONFIGURED=true
else
    echo -e "${YELLOW}⚠ Database connection failed${NC}"
    echo "  Make sure your database is running and configured"
fi

echo ""
echo -e "${BLUE}📚 Useful Commands Reference:${NC}"
echo ""
echo "  Create migrations:"
echo "    ${YELLOW}python manage.py makemigrations${NC}"
echo ""
echo "  Apply migrations:"
echo "    ${YELLOW}python manage.py migrate${NC}"
echo ""
echo "  Check for unmigrated changes:"
echo "    ${YELLOW}python manage.py makemigrations --check${NC}"
echo ""
echo "  View migration status:"
echo "    ${YELLOW}python manage.py showmigrations${NC}"
echo ""
echo "  Plan migrations (dry-run):"
echo "    ${YELLOW}python manage.py migrate --plan${NC}"
echo ""
echo "  Check Django system:"
echo "    ${YELLOW}python manage.py check${NC}"
echo ""

echo -e "${BLUE}📖 Documentation:${NC}"
echo "  See MIGRATIONS_CI_CD.md for detailed guides and troubleshooting"
echo ""

echo -e "${GREEN}✅ Setup complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Review MIGRATIONS_CI_CD.md for best practices"
echo "  2. Start developing with automatic migration checks"
echo "  3. Commit your migration files with model changes"
echo ""
echo -e "${BLUE}Happy coding! 🚀${NC}"
