#!/bin/bash
# ==============================================================================
# Enclave - Local F-Droid libsignal Source-Build Simulation
# ==============================================================================
# This script simulates how F-Droid's build server compiles libsignal from
# source using the srclibs config and populates the local apps/android/app/libs
# folder to verify the fdroid=true build pipeline.
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TEMP_SRC_DIR="$PROJECT_ROOT/libsignal-src"
TARGET_LIBS_DIR="$PROJECT_ROOT/apps/android/app/libs"

echo "================================================================================"
echo "   ENCLAVE — F-DROID LIBSIGNAL COMPILATION SIMULATOR"
echo "================================================================================"

# 1. Prerequisite Checks
echo "Checking prerequisites..."
if ! command -v git &>/dev/null; then
    echo "❌ Error: git is not installed." >&2
    exit 1
fi

if ! command -v cargo &>/dev/null; then
    echo "❌ Error: Rust/Cargo was not found. libsignal compilation requires Rust." >&2
    echo "   Please install Rust: https://www.rust-lang.org/tools/install" >&2
    exit 1
fi


# Locate ANDROID_HOME
if [[ -z "$ANDROID_HOME" ]]; then
    # Try to extract it from apps/android/local.properties
    PROP_FILE="$PROJECT_ROOT/apps/android/local.properties"
    if [[ -f "$PROP_FILE" ]]; then
        ANDROID_HOME=$(grep '^sdk.dir=' "$PROP_FILE" | cut -d'=' -f2- | sed 's/\\//g')
    fi
fi

if [[ -z "$ANDROID_HOME" ]]; then
    echo "❌ Error: ANDROID_HOME is not set and could not be read from local.properties." >&2
    echo "   Please set ANDROID_HOME or configure apps/android/local.properties." >&2
    exit 1
fi
echo "✓ ANDROID_HOME found: $ANDROID_HOME"

# 2. Clone Repository
if [[ -d "$TEMP_SRC_DIR" ]]; then
    echo "✓ Existing libsignal source directory found at $TEMP_SRC_DIR. Updating..."
    cd "$TEMP_SRC_DIR"
    git reset --hard
    git clean -fd
    if command -v cargo &>/dev/null; then
        cargo clean || true
    fi
    git fetch --tags
    git checkout --force v0.39.2
else
    echo "Cloning signalapp/libsignal at tag v0.39.2..."
    git clone --branch v0.39.2 --depth 1 https://github.com/signalapp/libsignal.git "$TEMP_SRC_DIR"
fi

# 2b. Add Android target platforms for the specific rust-toolchain version
if command -v rustup &>/dev/null; then
    # Read toolchain channel version from project files
    TOOLCHAIN=""
    if [[ -f "$TEMP_SRC_DIR/rust-toolchain" ]]; then
        TOOLCHAIN=$(cat "$TEMP_SRC_DIR/rust-toolchain" | tr -d '\r' | xargs)
    elif [[ -f "$TEMP_SRC_DIR/rust-toolchain.toml" ]]; then
        TOOLCHAIN=$(grep 'channel' "$TEMP_SRC_DIR/rust-toolchain.toml" | cut -d'"' -f2)
    fi
    
    TOOLCHAIN_ARG=""
    if [[ -n "$TOOLCHAIN" ]]; then
        echo "✓ Detected target Rust toolchain: $TOOLCHAIN"
        TOOLCHAIN_ARG="--toolchain $TOOLCHAIN"
    fi
    
    INSTALLED_TARGETS=$(rustup target list $TOOLCHAIN_ARG --installed)
    MISSING_TARGETS=""
    for target in aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android; do
        if ! echo "$INSTALLED_TARGETS" | grep -q "$target"; then
            MISSING_TARGETS="$MISSING_TARGETS $target"
        fi
    done
    
    if [[ -n "$MISSING_TARGETS" ]]; then
        echo "⚠️  Missing Android compilation targets for toolchain ${TOOLCHAIN:-default}:$MISSING_TARGETS"
        echo "   Running: rustup target add $TOOLCHAIN_ARG $MISSING_TARGETS"
        rustup target add $TOOLCHAIN_ARG $MISSING_TARGETS
    fi
fi

# 3. Build libsignal
echo "Building libsignal Java & Android targets from source..."
cd "$TEMP_SRC_DIR/java"

# Ensure local.properties exists in the clone's java folder pointing to the Android SDK and NDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

NDK_DIR=""
if [[ -d "$ANDROID_HOME/ndk/25.2.9519653" ]]; then
    NDK_DIR="$ANDROID_HOME/ndk/25.2.9519653"
elif [[ -d "$ANDROID_HOME/ndk/27.1.12297006" ]]; then
    NDK_DIR="$ANDROID_HOME/ndk/27.1.12297006"
elif [[ -d "$ANDROID_HOME/ndk/29.0.13113456" ]]; then
    NDK_DIR="$ANDROID_HOME/ndk/29.0.13113456"
else
    ANY_NDK=$(ls -d "$ANDROID_HOME"/ndk/*/ 2>/dev/null | head -n 1)
    if [[ -n "$ANY_NDK" ]]; then
        NDK_DIR="${ANY_NDK%/}"
    fi
fi

if [[ -n "$NDK_DIR" ]]; then
    echo "✓ Found NDK at $NDK_DIR. Configuring ndk.dir."
    echo "ndk.dir=$NDK_DIR" >> local.properties
    
    # Dynamically patch target NDK version in build.gradle if using a fallback version
    NDK_VER=$(basename "$NDK_DIR")
    if [[ "$NDK_VER" != "25.2.9519653" ]]; then
        echo "✓ Patching libsignal ndkVersion to '$NDK_VER'..."
        sed -i "s/ndkVersion .*/ndkVersion '$NDK_VER'/g" "$TEMP_SRC_DIR/java/android/build.gradle"
    else
        echo "✓ Matches preferred NDK version 25.2.9519653. No patching needed."
    fi
else
    echo "⚠️  Warning: No NDK version detected under $ANDROID_HOME/ndk/"
fi

# Locate JDK 17 to build libsignal (since its Gradle 7.4 build is incompatible with Java 21)
JAVA_17_HOME=""
for candidate in \
    "/usr/lib/jvm/java-17-temurin-jdk" \
    "/usr/lib/jvm/java-17-openjdk-amd64" \
    "/usr/lib/jvm/java-17-openjdk" \
    "/usr/lib/jvm/java-17-temurin" \
    "/usr/lib/jvm/temurin-17-jdk"; do
    if [[ -d "$candidate" ]]; then
        JAVA_17_HOME="$candidate"
        break
    fi
done

GRADLE_ARGS="build -x test"
if [[ -n "$JAVA_17_HOME" ]]; then
    echo "✓ Found JDK 17 at $JAVA_17_HOME. Forcing Gradle to use it."
    GRADLE_ARGS="$GRADLE_ARGS -Dorg.gradle.java.home=$JAVA_17_HOME"
else
    echo "⚠️  Warning: JDK 17 path not found in standard directories. Using system default java."
fi

# Configure bindgen to use NDK's prebuilt LLVM/Clang to avoid host Clang 21 parser issues
if [[ -n "$NDK_DIR" ]]; then
    NDK_LLVM_DIR="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
    if [[ -d "$NDK_LLVM_DIR" ]]; then
        echo "✓ Configuring bindgen to use NDK prebuilt LLVM/Clang..."
        export CLANG_PATH="$NDK_LLVM_DIR/bin/clang"
        export LIBCLANG_PATH="$NDK_LLVM_DIR/lib64"
        export BINDGEN_EXTRA_CLANG_ARGS="-I$NDK_LLVM_DIR/sysroot/usr/include"
    fi
fi

# Run the build
./gradlew --stop || true
./gradlew $GRADLE_ARGS

# 4. Copy Artifacts to apps/android/app/libs/
echo "Creating target libs directory: $TARGET_LIBS_DIR"
mkdir -p "$TARGET_LIBS_DIR"

echo "Copying compiled AAR and JAR artifacts..."
# Copy Android JNI wrapper AAR
cp "$TEMP_SRC_DIR"/java/android/build/outputs/aar/*.aar "$TARGET_LIBS_DIR"/
# Copy Java Client JAR
cp "$TEMP_SRC_DIR"/java/client/build/libs/*.jar "$TARGET_LIBS_DIR"/

echo "================================================================================"
echo "   ✅ LIBSIGNAL COMPILATION COMPLETE"
echo "   Artifacts written to: apps/android/app/libs/"
echo "   Now you can build the app locally in F-Droid mode using:"
echo "   cd apps/android && ./gradlew assembleRelease -Pfdroid=true"
echo "================================================================================"
