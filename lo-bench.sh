#!/bin/bash
# Redis 走 lo 后的真实水位长测：60s 窗口 + 25s 冷却
cd ~/nac/bench || exit 1
RPWD="MAO5201314sm"
flush_online() {
  redis-cli -a "$RPWD" --no-auth-warning --scan --pattern 'nac:online:*' 2>/dev/null \
    | xargs -r -n 200 redis-cli -a "$RPWD" --no-auth-warning UNLINK >/dev/null 2>&1
}
run_long() {
  local mode=$1 c=$2
  flush_online
  taskset -c 3 python3 radius_bench.py --server 192.168.80.36 sweep \
    --mode "$mode" --concurrency "$c" --warmup 8 --duration 60 \
    --out "json/lo-${mode}-${c}.json" 2>/dev/null | grep "C="
  sleep 25
}
echo "==== LO EAP ===="
run_long eap-md5 50
run_long eap-md5 100
run_long eap-md5 200
run_long eap-md5 400
echo "==== LO PAP ===="
run_long pap 100
run_long pap 400
echo "LO_BENCH_DONE"
