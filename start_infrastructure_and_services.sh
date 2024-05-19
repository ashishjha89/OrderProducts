#!/bin/bash

# Start Docker-Desktop app
echo "Launching Docker Desktop app..."
open -a Docker

# Function to check if MySQL is running
check_mysql() {
  # Check if MySQL is running by querying its process
  pgrep mysqld >/dev/null
}

# Function to start MySQL
start_mysql() {
  echo "Starting MySQL..."
  # Start MySQL using the appropriate command for your setup
  # For example, if you're using Homebrew, you might use:
  # brew services start mysql
  # Or if you're using the MySQL installer from the official website, you might use:
  # sudo /usr/local/mysql/support-files/mysql.server start
  mysql.server start
}

# Function to check if MongoDB is running
check_mongodb() {
  # Check if MongoDB is running by querying its process
  pgrep mongod >/dev/null
}

# Function to start MongoDB
start_mongodb() {
  echo "Starting MongoDB..."
  brew services start mongodb-community
}

# Check if MySQL is running
check_mysql
if [ $? -ne 0 ]; then
  # MySQL is not running, so start it
  start_mysql
fi

# Check if MongoDB is running
check_mongodb
if [ $? -ne 0 ]; then
  # MongoDB is not running, so start it
  start_mongodb
fi

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
