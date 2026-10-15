#!/bin/bash
pkill -f upstream_ok.py 2>/dev/null
sleep 1
nohup python3 /tmp/upstream_ok.py >/tmp/up_ok.log 2>&1 &
sleep 1
echo "=== one engine request ==="
curl -s -o /dev/null -w "engine=%{http_code}\n" -H "Host: bench.test" http://127.0.0.1:8099/
sleep 0.5
echo "=== upstream received ==="
cat /tmp/up_ok.log
