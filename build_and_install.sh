#!/usr/bin/env bash
set -euo pipefail

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "=================================================="
echo "  SereneType — Сборка и установка APK (macOS)"
echo "=================================================="

# 1. Проверка Homebrew
if ! command -v brew >/dev/null 2>&1; then
  echo "❌ Homebrew не найден. Установите его:"
  echo '/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
  exit 1
fi

# 2. Установка JDK 17
BREW_JDK17="$(brew --prefix openjdk@17 2>/dev/null)/libexec/openjdk.jdk/Contents/Home"
if [ -d "$BREW_JDK17" ]; then
  export JAVA_HOME="$BREW_JDK17"
elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
else
  echo "📦 Устанавливаю OpenJDK 17..."
  brew install openjdk@17
  export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "✅ JAVA_HOME: $JAVA_HOME"

# 3. Установка ADB (platform-tools)
if ! command -v adb >/dev/null 2>&1; then
  echo "📦 Устанавливаю ADB (platform-tools)..."
  brew install --cask android-platform-tools
fi

# 4. Настройка Android SDK
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
  echo "📦 Настраиваю Android SDK (API 35)..."
  mkdir -p "$ANDROID_HOME"

  if ! command -v sdkmanager >/dev/null 2>&1; then
    brew install --cask android-commandlinetools
  fi

  SDKMANAGER_BIN="$(command -v sdkmanager || find /opt/homebrew /usr/local "$ANDROID_HOME" -name sdkmanager 2>/dev/null | head -n 1)"
  if [ -z "${SDKMANAGER_BIN:-}" ]; then
    echo "❌ Не удалось найти sdkmanager."
    exit 1
  fi

  yes | "$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
  "$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
fi
echo "✅ ANDROID_HOME: $ANDROID_HOME"

# 5. Генерация Gradle Wrapper при необходимости
GRADLE_CMD="./gradlew"
if [ ! -f "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "📦 Инициализирую Gradle Wrapper..."
  if ! command -v gradle >/dev/null 2>&1; then
    brew install gradle
  fi
  if ! gradle wrapper --gradle-version 8.11.1 --no-validate-url; then
    echo "⚠️  Wrapper не удалось проверить по сети, собираю напрямую через системный gradle..."
    GRADLE_CMD="gradle"
  fi
fi
[ -f "$PROJECT_DIR/gradlew" ] && chmod +x "$PROJECT_DIR/gradlew"

# 6. Сборка APK
echo "🔨 Собираю SereneType APK..."
"$GRADLE_CMD" assembleRelease --no-daemon

APK_SRC="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SRC" ]; then
  echo "❌ Ошибка: APK не найден по пути $APK_SRC"
  exit 1
fi

# 7. Сохранение APK в папку dist
DIST_DIR="$PROJECT_DIR/dist"
mkdir -p "$DIST_DIR"
cp "$APK_SRC" "$DIST_DIR/SereneType.apk"

echo "✅ APK сохранён: $DIST_DIR/SereneType.apk"

# 8. Установка по ADB (если устройство подключено)
CONNECTED_DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
if [ -n "$CONNECTED_DEVICES" ]; then
  echo "📱 Найдено устройство ADB. Устанавливаю SereneType.apk..."
  adb install -r "$DIST_DIR/SereneType.apk"
  echo "✅ Приложение установлено! Откройте SereneType на телефоне для включения клавиатуры."
else
  echo "⚠️  Android-устройство по ADB не подключено (включите отладку по USB и подключите телефон)."
  echo "    Готовый APK лежит в папке: $DIST_DIR"
fi

# 9. Открытие папки с APK в Finder
open "$DIST_DIR"
echo "🎉 Готово!"
