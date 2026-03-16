#!/bin/bash

find backend/src/main/resources/public/ -maxdepth 1 -type f -exec rm -f {} +
cp -R frontend/dist/* backend/src/main/resources/

# Copy Files
VIEW_DIR=backend/src/main/resources/view

# If 'view' exists and is a regular file, remove it so we can create a directory
if [ -f "$VIEW_DIR" ]; then
  rm -f "$VIEW_DIR"
fi

# Ensure directory exists
mkdir -p "$VIEW_DIR"
mv backend/src/main/resources/*.html "$VIEW_DIR"/
