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

# Input Target IP / Address and flags
TARGET_INPUT=""
ENABLE_SCRCPY=""

for arg in "$@"; do
    case "$arg" in
        -m|--mirror|--scrcpy)
            ENABLE_SCRCPY=true
            ;;
        --no-mirror)
            ENABLE_SCRCPY=false
            ;;
        *)
            if [ -z "$TARGET_INPUT" ]; then
                TARGET_INPUT="$arg"
            fi
            ;;
    esac
done

if [ -z "$TARGET_INPUT" ]; then
    read -r -p "👉 Nhập IP của TV (mặc định: $DEFAULT_IP): " TARGET_INPUT
fi

if [ -z "$TARGET_INPUT" ]; then
    TARGET_INPUT="$DEFAULT_IP"
fi

# Check scrcpy availability
HAS_SCRCPY=false
if command -v scrcpy &> /dev/null; then
    HAS_SCRCPY=true
fi

# Prompt for scrcpy if not specified via arguments
if [ -z "$ENABLE_SCRCPY" ]; then
    if [ "$HAS_SCRCPY" = true ]; then
        read -r -p "🖥️  Bạn có muốn xem và điều khiển màn hình TV (scrcpy) không? [Y/n]: " SCRCPY_PROMPT
        if [[ -z "$SCRCPY_PROMPT" || "$SCRCPY_PROMPT" =~ ^[yY] ]]; then
            ENABLE_SCRCPY=true
        else
            ENABLE_SCRCPY=false
        fi
    else
        ENABLE_SCRCPY=false
    fi
fi

# Parse IP and Port
if [[ "$TARGET_INPUT" == *":"* ]]; then
    IP="${TARGET_INPUT%%:*}"
    PORT="${TARGET_INPUT##*:}"
else
    IP="$TARGET_INPUT"
    PORT="$DEFAULT_PORT"
fi

TARGET="$IP:$PORT"
SCRCPY_PID=""

# Helper functions for scrcpy
start_scrcpy() {
    if [ "$ENABLE_SCRCPY" = true ] && [ "$HAS_SCRCPY" = true ]; then
        if [ -z "$SCRCPY_PID" ] || ! kill -0 "$SCRCPY_PID" 2>/dev/null; then
            echo -e "${CYAN}🖥️  Đang khởi chạy cửa sổ scrcpy cho $TARGET...${NC}"
            scrcpy -s "$TARGET" --video-bit-rate=8M --max-fps=60 --window-title="Android TV ($TARGET)" > /dev/null 2>&1 &
            SCRCPY_PID=$!
        fi
    fi
}

stop_scrcpy() {
    if [ -n "$SCRCPY_PID" ] && kill -0 "$SCRCPY_PID" 2>/dev/null; then
        echo -e "${YELLOW}Dừng scrcpy...${NC}"
        kill "$SCRCPY_PID" 2>/dev/null
        wait "$SCRCPY_PID" 2>/dev/null
        SCRCPY_PID=""
    fi
}

# Cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Đang dừng watchdog và dọn dẹp...${NC}"
    stop_scrcpy
    stty echo 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

# Remote helper functions
send_key() {
    local key_code="$1"
    local key_name="$2"
    echo -ne "\r\033[K${CYAN}🎮 [Remote] ${BOLD}$key_name${NC}"
    adb -s "$TARGET" shell input keyevent "$key_code" > /dev/null 2>&1 &
}

print_remote_help() {
    echo -e "\n${CYAN}┌────────────────────────────────────────────────────────┐${NC}"
    echo -e "${CYAN}│${NC}              ${BOLD}🎮 BÀN PHÍM ĐIỀU KHIỂN TV${NC}                 ${CYAN}│${NC}"
    echo -e "${CYAN}├────────────────────────────────────────────────────────┤${NC}"
    echo -e "${CYAN}│${NC}  ${YELLOW}[↑][↓][←][→]${NC} hoặc ${YELLOW}[W][S][A][D]${NC}  : Di chuyển D-Pad             ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}  ${GREEN}[Enter]${NC} hoặc ${GREEN}[Space]${NC}          : Chọn (OK / Center)          ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}  ${RED}[Esc]${NC} / ${RED}[Backspace]${NC} / ${RED}[B]${NC}   : Quay lại (Back)             ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}  ${BLUE}[H]${NC} : Trang chủ (Home)        ${BLUE}[M]${NC} : Menu                      ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}  ${YELLOW}[+] [-]${NC} : Tăng/Giảm âm lượng  ${YELLOW}[P]${NC} : Nguồn (Power)             ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}  ${RED}[Q]${NC} hoặc ${RED}[Ctrl+C]${NC}             : Thoát script                ${CYAN}│${NC}"
    echo -e "${CYAN}└────────────────────────────────────────────────────────┘${NC}"
    echo -e "${GREEN}👉 Nhấn phím bất kỳ trên bàn phím để điều khiển TV ngay lập tức!${NC}\n"
}

echo -e "\n${BLUE}ℹ️  Mục tiêu kết nối: ${BOLD}$TARGET${NC}"
if [ "$ENABLE_SCRCPY" = true ]; then
    echo -e "${GREEN}🖥️  Chế độ xem màn hình: ${BOLD}BẬT (scrcpy)${NC}"
fi

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
    TARGET="$IP:$PORT"
    echo -e "${GREEN}✓ Đã chuyển sang cổng $PORT thành công!${NC}"
fi

# Function to check connection state
is_connected() {
    adb devices | grep -F "$TARGET" | grep -q "device"
}

# Initial connect attempt
echo -e "\n${CYAN}🚀 Đang kết nối tới $TARGET...${NC}"
adb connect "$TARGET"

echo -e "\n${GREEN}👀 Bắt đầu lắng nghe và tự động kết nối lại khi TV rớt mạng...${NC}"

was_connected=false
last_check_time=0

while true; do
    current_time=$(date +%s)

    # Periodic connection watchdog check
    if (( current_time - last_check_time >= 3 )); then
        last_check_time=$current_time
        if is_connected; then
            if [ "$was_connected" = false ]; then
                echo -e "\n[$(date '+%H:%M:%S')] ${GREEN}✓ Đã kết nối với TV ($TARGET)!${NC}"
                was_connected=true
                start_scrcpy
                print_remote_help
            fi
        else
            if [ "$was_connected" = true ]; then
                echo -e "\n[$(date '+%H:%M:%S')] ${RED}⚠️  Mất kết nối với TV ($TARGET)!${NC}"
                echo -e "[$(date '+%H:%M:%S')] ${YELLOW}Đang theo dõi và chờ TV online lại...${NC}"
                was_connected=false
                stop_scrcpy
            fi

            # Try pinging the IP first
            if ping -c 1 -W 1 "$IP" > /dev/null 2>&1; then
                echo -e "[$(date '+%H:%M:%S')] ${CYAN}📡 Đã thấy TV trong mạng, đang kết nối lại ADB...${NC}"
                adb connect "$TARGET" > /dev/null 2>&1
                sleep 1
                if is_connected; then
                    echo -e "[$(date '+%H:%M:%S')] ${GREEN}🎉 Kết nối lại thành công!${NC}"
                    was_connected=true
                    start_scrcpy
                    print_remote_help
                fi
            fi
        fi
    fi

    # Read user keystrokes for TV Remote (2s timeout aligns with watchdog check)
    IFS= read -rsn1 -t 2 char
    read_status=$?

    if [ $read_status -eq 0 ]; then
        if [ "$was_connected" = false ]; then
            echo -ne "\r\033[K${YELLOW}⚠️  Chưa kết nối với TV ($TARGET), phím chưa được gửi...${NC}"
            continue
        fi

        case "$char" in
            $'\x1b')
                IFS= read -rsn2 -t 0.05 rest
                case "$rest" in
                    "[A") send_key 19 "⬆️  LÊN (Up)" ;;
                    "[B") send_key 20 "⬇️  XUỐNG (Down)" ;;
                    "[D") send_key 21 "⬅️  TRÁI (Left)" ;;
                    "[C") send_key 22 "➡️  PHẢI (Right)" ;;
                    *)    send_key 4  "🔙 QUAY LẠI (Back)" ;;
                esac
                ;;
            "")
                send_key 23 "🔘 CHỌN (OK/Enter)"
                ;;
            " ")
                send_key 23 "🔘 CHỌN (Space)"
                ;;
            $'\x7f'|$'\x08'|[bB])
                send_key 4  "🔙 QUAY LẠI (Back)"
                ;;
            [wW]) send_key 19 "⬆️  LÊN (W)" ;;
            [sS]) send_key 20 "⬇️  XUỐNG (S)" ;;
            [aA]) send_key 21 "⬅️  TRÁI (A)" ;;
            [dD]) send_key 22 "➡️  PHẢI (D)" ;;
            [hH]) send_key 3  "🏠 TRANG CHỦ (Home)" ;;
            [mM]) send_key 82 "📋 MENU" ;;
            [pP]) send_key 26 "⚡ NGUỒN (Power)" ;;
            "+"|"=") send_key 24 "🔊 TĂNG ÂM LƯỢNG (+)" ;;
            "-") send_key 25 "🔉 GIẢM ÂM LƯỢNG (-)" ;;
            [qQ])
                echo -e "\n${YELLOW}Đang thoát...${NC}"
                cleanup
                ;;
        esac
    fi
done
