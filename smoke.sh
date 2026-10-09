#!/bin/bash
echo "===== GATEWAY LOGIN (wangfang) ====="
curl -s -X POST http://127.0.0.1:9000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"wangfang","password":"Wf$secure99"}'
echo ""
echo "===== GATEWAY LOGIN (wrong password) ====="
curl -s -X POST http://127.0.0.1:9000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"wangfang","password":"wrong"}'
echo ""
echo "===== ONLINE COUNT ====="
curl -s http://127.0.0.1:9000/api/auth/online/count
echo ""
echo "===== RADIUS SMOKE ====="
taskset -c 3 python3 ~/nac/bench/radius_bench.py --server 192.168.80.36 smoke
