#!/bin/bash
# 正式分级压测：单级调用 + 级间冷却 20s，消除连续压测热漂移
cd ~/nac/bench || exit 1
RPWD="MAO5201314sm"
flush_online() {
  redis-cli -a "$RPWD" --no-auth-warning --scan --pattern 'nac:online:*' 2>/dev/null \
    | xargs -r -n 200 redis-cli -a "$RPWD" --no-auth-warning UNLINK >/dev/null 2>&1
}
bash ~/nac/monitor.sh ~/nac/bench/mon-final.csv &
MON=$!
sleep 1

run_mode() {
  local mode=$1; shift
  local levels=("$@")
  for c in "${levels[@]}"; do
    flush_online
    taskset -c 3 python3 radius_bench.py --server 192.168.80.36 sweep \
      --mode "$mode" --concurrency "$c" --warmup 6 --duration 15 \
      --out "json/final-${mode}-${c}.json" 2>/dev/null | grep "C="
    sleep 20   # 冷却，让 CPU/队列回落到空闲
  done
}

echo "########## EAP-MD5 ##########"
run_mode eap-md5 25 50 100 150 200 300
echo "########## PAP ##########"
run_mode pap 25 50 100 200 400

kill $MON 2>/dev/null
echo "ALL_SWEEP_DONE"
