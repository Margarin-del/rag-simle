#!/bin/bash

echo "=== Running CI/CD locally ==="

# Test
echo "Running tests..."
./gradlew test

if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi

# Build
echo "Building..."
./gradlew clean build -x test

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Docker build
echo "Building Docker image..."
docker build -t rag-app:local .

if [ $? -ne 0 ]; then
    echo "Docker build failed!"
    exit 1
fi

echo "=== CI/CD completed successfully! ==="