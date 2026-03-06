#!/bin/bash
set -e

echo "=== Clubs Project Setup ==="

# 1. Download gradle wrapper jar
WRAPPER_JAR="backend/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar..."
    curl -sL "https://github.com/gradle/gradle/raw/v8.12.1/gradle/wrapper/gradle-wrapper.jar" \
        -o "$WRAPPER_JAR"
    echo "Done: $WRAPPER_JAR"
else
    echo "gradle-wrapper.jar already exists"
fi

# 2. Make gradlew executable
chmod +x backend/gradlew
echo "gradlew is executable"

# 3. Init git if needed
if [ ! -d ".git" ]; then
    git init
    echo "Git repository initialized"
else
    echo "Git repository already exists"
fi

echo ""
echo "=== Setup complete. Next steps: ==="
echo "1. docker-compose up -d"
echo "2. cd backend && ./gradlew bootRun"
echo "3. curl http://localhost:8080/actuator/health"
