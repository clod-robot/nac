#!/bin/bash
echo "===== uptime ====="
uptime
echo "===== listen ports ====="
ss -tln | awk 'NR>1{print $4}' | grep -E ':(3306|6379|9092|2181|8848|9000|9001|9002|9003|9004|9005|9006|1812)$' | sort -u
echo "===== java procs ====="
pgrep -af java | grep -v pgrep | awk '{print $1, $NF}' | head -20
echo "===== app dir ====="
ls -la ~/nac/app/ 2>/dev/null
echo "===== nacos script ====="
ls ~/nac/start-nacos.sh 2>/dev/null && echo EXISTS
