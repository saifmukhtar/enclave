#!/bin/bash

APK_PATH="enclave-ui/app/build/outputs/apk/release/app-release.apk"
PACKAGE_NAME="com.enclave.app"
LOG_FILE="release_test_log.txt"

echo "================================================="
echo "🚀 Enclave Release Tester & Bug Catcher"
echo "================================================="

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: Release APK not found at $APK_PATH"
    echo "Please run './gradlew assembleRelease' first."
    exit 1
fi

echo "📱 Installing Release APK to connected device..."
adb install -r -d "$APK_PATH"

if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to install APK. Check ADB connection."
    exit 1
fi

echo "🧹 Clearing old logcat buffers..."
adb logcat -c

echo "▶️ Launching Enclave..."
adb shell am start -n "$PACKAGE_NAME/.MainActivity"

echo "================================================="
echo "🐛 Capturing LIVE logs to $LOG_FILE"
echo "Press Ctrl+C to stop logging."
echo "================================================="

# Start streaming logcat. 
# We filter for com.enclave.app, AndroidRuntime (for crashes), and strict FATAL tags.
adb logcat -v time | awk '
    /com\.enclave\.app/ || /AndroidRuntime/ || /FATAL EXCEPTION/ {
        print $0;
        fflush();
    }
' | tee "$LOG_FILE"
