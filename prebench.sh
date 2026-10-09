#!/bin/bash
echo "===== current udp buffer limits ====="
sysctl net.core.rmem_max net.core.wmem_max
echo "===== set 4MB (needed by nac-auth 4MB socket buf) ====="
echo 'MAO5201314sm' | sudo -S sysctl -w net.core.rmem_max=4194304 net.core.wmem_max=4194304
echo "===== verify effective ====="
sysctl net.core.rmem_max net.core.wmem_max
echo "===== bench dir ====="
ls ~/nac/bench/ 2>/dev/null
echo "===== radius smoke ====="
cd ~/nac/bench
taskset -c 3 python3 radius_bench.py --server 127.0.0.1 smoke 2>&1 | tail -8
