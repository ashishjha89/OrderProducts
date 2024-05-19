#!/bin/bash

# Find all Java processes (assuming only your Spring Boot apps are running Java)
pids=$(pgrep -f 'spring-boot')

# Loop through each PID and kill it
for pid in $pids; do
  echo "Stopping process $pid"
  kill $pid || { echo "Failed to kill process $pid"; exit 1; }
done

echo "All services stopped."
