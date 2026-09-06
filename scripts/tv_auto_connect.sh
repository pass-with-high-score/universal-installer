#!/usr/bin/env bash

# ==============================================================================
# Android TV ADB Auto-Reconnect & Watchdog Script
# ==============================================================================

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

DEFAULT_IP="192.168.1.128"
DEFAULT_PORT="5555"

echo -e "${CYAN}${BOLD}====================================================${NC}"
echo -e "${CYAN}${BOLD}   📺 Android TV ADB Auto-Connect & Watchdog       ${NC}"
echo -e "${CYAN}${BOLD}====================================================${NC}"

# Input Target IP / Address
TARGET_INPUT="$1"
if [ -z "$TARGET_INPUT" ]; then
    read -r -p "👉 Nhập IP của TV (mặc định: $DEFAULT_IP): " TARGET_INPUT
fi

if [ -z "$TARGET_INPUT" ]; then
    TARGET_INPUT="$DEFAULT_IP"
fi

# Parse IP and Port
if [[ "$TARGET_INPUT" == *":"* ]]; then
    IP="${TARGET_INPUT%%:*}"
    PORT="${TARGET_INPUT##*:}"
else
    IP="$TARGET_INPUT"
    PORT="$DEFAULT_PORT"
fi

# Cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Đang dừng watchdog...${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM

echo -e "\n${BLUE}ℹ️  Mục tiêu kết nối: ${BOLD}$IP:$PORT${NC}"

# Initial connection & switch to port 5555 if using random port
if [ "$PORT" != "5555" ]; then
    echo -e "${YELLOW}🔄 Đang thử kết nối cổng không dây ban đầu $IP:$PORT...${NC}"
    adb connect "$IP:$PORT" > /dev/null 2>&1
    sleep 1
    
    # Try enabling standard port 5555
    echo -e "${CYAN}⚡ Đang kích hoạt chuyển sang cổng chuẩn 5555...${NC}"
    adb -s "$IP:$PORT" tcpip 5555 > /dev/null 2>&1
    sleep 2
    adb disconnect "$IP:$PORT" > /dev/null 2>&1
    PORT="5555"
    echo -e "${GREEN}✓ Đã chuyển sang cổng $PORT thành công!${NC}"
fi

TARGET="$IP:$PORT"

# Function to check connection state
is_connected() {
    adb devices | grep -F "$TARGET" | grep -q "device"
}

# Initial connect attempt
echo -e "\n${CYAN}🚀 Đang kết nối tới $TARGET...${NC}"
adb connect "$TARGET"

echo -e "\n${GREEN}👀 Bắt đầu lắng nghe và tự động kết nối lại khi TV rớt mạng...${NC}"
echo -e "${YELLOW}(Nhấn Ctrl+C để dừng script bất kỳ lúc nào)${NC}\n"

was_connected=false

while true; do
    if is_connected; then
        if [ "$was_connected" = false ]; then
            echo -e "[$(date '+%H:%M:%S')] ${GREEN}✓ Đã kết nối với TV ($TARGET)!${NC}"
            was_connected=true
        fi
    else
        if [ "$was_connected" = true ]; then
            echo -e "\n[$(date '+%H:%M:%S')] ${RED}⚠️  Mất kết nối với TV ($TARGET)!${NC}"
            echo -e "[$(date '+%H:%M:%S')] ${YELLOW}Đang theo dõi và chờ TV online lại...${NC}"
            was_connected=false
        fi

        # Try pinging the IP first
        if ping -c 1 -W 1 "$IP" > /dev/null 2>&1; then
            echo -e "[$(date '+%H:%M:%S')] ${CYAN}📡 Đã thấy TV trong mạng, đang kết nối lại ADB...${NC}"
            adb connect "$TARGET" > /dev/null 2>&1
            sleep 1
            if is_connected; then
                echo -e "[$(date '+%H:%M:%S')] ${GREEN}🎉 Kết nối lại thành công!${NC}"
                was_connected=true
            fi
        fi
    fi
    sleep 3
done
