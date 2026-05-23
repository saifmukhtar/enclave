#!/bin/bash

# Temporary PID files
PID_FILE_A="/tmp/enclave_pid_A.txt"
PID_FILE_B="/tmp/enclave_pid_B.txt"

# Clean exit handler
cleanup() {
    echo -e "\n\033[1;31mStopping log collection...\033[0m"
    # Kill background monitor loops and adb processes
    kill $(jobs -p) 2>/dev/null
    rm -f "$PID_FILE_A" "$PID_FILE_B"
    exit 0
}
trap cleanup SIGINT SIGTERM

# Check if adb is installed
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb is not installed or not in PATH."
    exit 1
fi

# Detect devices
echo "🔍 Detecting connected Android devices..."
DEVICES=($(adb devices | grep -w "device" | awk '{print $1}'))
NUM_DEVICES=${#DEVICES[@]}

if [ "$NUM_DEVICES" -lt 2 ]; then
    echo "❌ Error: Found $NUM_DEVICES device(s). You need at least 2 connected devices."
    echo "Currently connected:"
    adb devices
    exit 1
fi

SERIAL_A="${DEVICES[0]}"
SERIAL_B="${DEVICES[1]}"

# Get device names/models for better logging
MODEL_A=$(adb -s "$SERIAL_A" shell getprop ro.product.model | tr -d '\r')
SDK_A=$(adb -s "$SERIAL_A" shell getprop ro.build.version.sdk | tr -d '\r')
MODEL_B=$(adb -s "$SERIAL_B" shell getprop ro.product.model | tr -d '\r')
SDK_B=$(adb -s "$SERIAL_B" shell getprop ro.build.version.sdk | tr -d '\r')

echo -e "📱 \033[1;35mPhone A:\033[0m $MODEL_A (API $SDK_A, Serial: $SERIAL_A)"
echo -e "📱 \033[1;36mPhone B:\033[0m $MODEL_B (API $SDK_B, Serial: $SERIAL_B)"

# Initialize PID files
echo "" > "$PID_FILE_A"
echo "" > "$PID_FILE_B"

# Start background PID monitors
monitor_pid() {
    local serial="$1"
    local pid_file="$2"
    local last_pid=""
    while true; do
        local pid=$(adb -s "$serial" shell pidof com.enclave.app 2>/dev/null | tr -d '\r' | awk '{print $1}')
        if [ "$pid" != "$last_pid" ]; then
            echo "$pid" > "$pid_file"
            last_pid="$pid"
        fi
        sleep 0.5
    done
}

monitor_pid "$SERIAL_A" "$PID_FILE_A" &
monitor_pid "$SERIAL_B" "$PID_FILE_B" &

read -p "Clear logcat buffers on both devices first? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🧹 Clearing logcat buffers..."
    adb -s "$SERIAL_A" logcat -c
    adb -s "$SERIAL_B" logcat -c
fi

LOG_FILE="./logcat.txt"
echo "📝 Unified logs will be written to: $LOG_FILE"
echo "🚀 Starting log capture. Press Ctrl+C to stop..."
echo "=================================================="

# Run logging subshell and pipe stdout to logcat.txt, while redirecting colors to stderr
(
    # Phone A
    adb -s "$SERIAL_A" logcat -v threadtime | awk -v prefix="Phone-A" -v pid_file="$PID_FILE_A" '
        BEGIN {
            last_pid = ""
        }
        {
            gsub(/\r/, "");
            if ((getline pid < pid_file) > 0) {
                close(pid_file)
            } else {
                pid = last_pid
            }
            
            # Print if PID matches or if package name is mentioned
            if (pid != "" && ($3 == pid || $4 == pid)) {
                printf "\033[1;35m[%s]\033[0m %s\n", prefix, $0 > "/dev/stderr";
                printf "[%s] %s\n", prefix, $0;
                fflush();
                last_pid = pid
            } else if ($0 ~ "com.enclave.app") {
                printf "\033[1;35m[%s]\033[0m %s\n", prefix, $0 > "/dev/stderr";
                printf "[%s] %s\n", prefix, $0;
                fflush();
            }
        }
    ' &

    # Phone B
    adb -s "$SERIAL_B" logcat -v threadtime | awk -v prefix="Phone-B" -v pid_file="$PID_FILE_B" '
        BEGIN {
            last_pid = ""
        }
        {
            gsub(/\r/, "");
            if ((getline pid < pid_file) > 0) {
                close(pid_file)
            } else {
                pid = last_pid
            }
            
            # Print if PID matches or if package name is mentioned
            if (pid != "" && ($3 == pid || $4 == pid)) {
                printf "\033[1;36m[%s]\033[0m %s\n", prefix, $0 > "/dev/stderr";
                printf "[%s] %s\n", prefix, $0;
                fflush();
                last_pid = pid
            } else if ($0 ~ "com.enclave.app") {
                printf "\033[1;36m[%s]\033[0m %s\n", prefix, $0 > "/dev/stderr";
                printf "[%s] %s\n", prefix, $0;
                fflush();
            }
        }
    ' &

    wait
) > "$LOG_FILE"
