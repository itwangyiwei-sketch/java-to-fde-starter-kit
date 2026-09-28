#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "========================================="
echo " 🚀 正在启动 Java to FDE Starter Kit...  "
echo "========================================="

# 编译应用
mvn compile -q

# 后台启动 Spring Boot
mvn spring-boot:run -q &
APP_PID=$!

cleanup() {
    echo ""
    echo "🧹 正在停止后台 Spring Boot 服务 (PID: $APP_PID)..."
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# 轮询探测 8080 端口就绪 (最多等待 30 秒)
echo "⏳ 等待微服务监听 127.0.0.1:8080 就绪..."
READY=0
for i in {1..30}; do
    if curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/mcp 2>/dev/null | grep -q '200\|400\|405'; then
        READY=1
        break
    fi
    sleep 1
done

if [ "$READY" -ne 1 ]; then
    echo "❌ 服务启动超时，请检查日志！"
    exit 1
fi

echo "✅ 服务已就绪！开始运行业务 Eval 评测："
echo "-----------------------------------------"
python3 eval/run.py

echo ""
echo "-----------------------------------------"
echo "✅ 运行 MCP 协议冒烟验证："
echo "-----------------------------------------"
python3 scripts/mcp_smoke.py

echo ""
echo "🎉 全套验收完成！"
