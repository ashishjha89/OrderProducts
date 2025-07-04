#!/bin/bash

# Get the directory of the script
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find all subdirectories within the script directory, excluding hidden ones and .git/.idea directories
services=$(find "$script_dir" -maxdepth 1 -mindepth 1 -type d ! -name ".*" ! -name ".git" ! -name ".idea" ! -name ".vscode" ! -name "infrastructure")

# Loop through each service and run mvn clean install
for service in $services; do
  echo "Building $service"
  cd "$service" || { echo "Failed to change directory to $service"; exit 1; }
  mvn clean install || { echo "Failed to build $service"; exit 1; }
done

echo "All services built successfully"
