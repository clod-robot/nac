#!/bin/bash
echo "===== kafka server.log tail ====="
tail -50 ~/software/kafka/logs/server.log 2>/dev/null | grep -iE 'error|exception|fatal|started|shut' | tail -12
echo "===== kafka process ====="
pgrep -af kafka.Kafka | head -2
echo "===== 9092 ====="
ss -tln | grep 9092 || echo NO_9092
