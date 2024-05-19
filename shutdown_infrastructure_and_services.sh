#!/bin/bash

# Closing Docker-Desktop app
echo "Closing Docker Desktop app..."
pkill -SIGHUP -f /Applications/Docker.app 'docker serve'

echo "Stopping MySQL..."
mysql.server stop

echo "Stopping MondoDB..."
brew services stop mongodb-community

# Find all Java processes (assuming only your Spring Boot apps are running Java)
pids=$(pgrep -f 'spring-boot')

# Loop through each PID and kill it
for pid in $pids; do
  echo "Stopping process $pid"
  kill $pid || { echo "Failed to kill process $pid"; exit 1; }
done

echo "All services stopped."
