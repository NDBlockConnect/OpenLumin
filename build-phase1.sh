#!/bin/bash
# OpenLumin 批量构建脚本 - Phase 1: 现代版本 (26.2 → 1.20.2)

set -e

echo "========================================="
echo "OpenLumin 多版本构建系统"
echo "Phase 1: 现代版本 (26.2 → 1.20.2)"
echo "========================================="

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 构建计数器
TOTAL=0
SUCCESS=0
FAILED=0

build_module() {
    local module=$1
    TOTAL=$((TOTAL + 1))

    echo ""
    echo -e "${YELLOW}[${TOTAL}] 构建 ${module}...${NC}"

    if ./gradlew :${module}:build --stacktrace; then
        echo -e "${GREEN}✓ ${module} 构建成功${NC}"
        SUCCESS=$((SUCCESS + 1))
    else
        echo -e "${RED}✗ ${module} 构建失败${NC}"
        FAILED=$((FAILED + 1))
    fi
}

# Phase 1 模块列表
echo ""
echo "准备构建以下模块:"
echo "  - common (核心模块)"
echo "  - neoforge-26.2"
echo "  - neoforge-26.1"
echo "  - neoforge-1.21.10, fabric-1.21.10, forge-1.21.10"
echo "  - neoforge-1.21.4, fabric-1.21.4, forge-1.21.4"
echo "  - neoforge-1.21.3, fabric-1.21.3, forge-1.21.3"
echo "  - neoforge-1.21.1, fabric-1.21.1, forge-1.21.1"
echo "  - neoforge-1.20.6, fabric-1.20.6, forge-1.20.6"
echo "  - neoforge-1.20.5, fabric-1.20.5, forge-1.20.5"
echo "  - neoforge-1.20.4, fabric-1.20.4, forge-1.20.4"
echo "  - neoforge-1.20.2, fabric-1.20.2, forge-1.20.2"
echo ""
read -p "按 Enter 开始构建..."

# 构建 common
build_module "common"

# 2026.x
build_module "neoforge-26.2"
build_module "neoforge-26.1"

# 1.21.10
build_module "neoforge-1.21.10"
build_module "fabric-1.21.10"
build_module "forge-1.21.10"

# 1.21.4
build_module "neoforge-1.21.4"
build_module "fabric-1.21.4"
build_module "forge-1.21.4"

# 1.21.3
build_module "neoforge-1.21.3"
build_module "fabric-1.21.3"
build_module "forge-1.21.3"

# 1.21.1
build_module "neoforge-1.21.1"
build_module "fabric-1.21.1"
build_module "forge-1.21.1"

# 1.20.6
build_module "neoforge-1.20.6"
build_module "fabric-1.20.6"
build_module "forge-1.20.6"

# 1.20.5
build_module "neoforge-1.20.5"
build_module "fabric-1.20.5"
build_module "forge-1.20.5"

# 1.20.4
build_module "neoforge-1.20.4"
build_module "fabric-1.20.4"
build_module "forge-1.20.4"

# 1.20.2
build_module "neoforge-1.20.2"
build_module "fabric-1.20.2"
build_module "forge-1.20.2"

# 构建报告
echo ""
echo "========================================="
echo "构建完成"
echo "========================================="
echo -e "总计: ${TOTAL}"
echo -e "${GREEN}成功: ${SUCCESS}${NC}"
echo -e "${RED}失败: ${FAILED}${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有模块构建成功！${NC}"
    exit 0
else
    echo -e "${RED}✗ 部分模块构建失败，请检查日志${NC}"
    exit 1
fi
