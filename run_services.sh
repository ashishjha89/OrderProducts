#!/bin/bash

# Get the directory of the script
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find all subdirectories within the script directory, excluding hidden ones and .git/.idea directories
services=$(find "$script_dir" -maxdepth 1 -mindepth 1 -type d ! -name ".*" ! -name ".git" ! -name ".idea")

# Loop through each service and run mvn spring-boot:run in the background
for service in $services; do
  echo "Starting $service"
  cd "$service" || { echo "Failed to change directory to $service"; exit 1; }
  # Run the service in the background
  mvn spring-boot:run &
  cd "$script_dir" || exit
done

echo "All services are starting in the background"
