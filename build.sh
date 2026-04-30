#!/bin/bash

# Frontend
cd frontend
./build.sh --no-docker clean init build
cd ..

# Create directory structure and copy frontend dist
./copyFrontFiles.sh

# Backend
cd backend
./build.sh --no-docker clean build
cd ..

# Clean up - remove frontend/dist and backend/src/main/resources
rm -rf frontend/dist