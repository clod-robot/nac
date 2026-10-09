#!/bin/bash
# 分级压测执行器: bash bench.sh <mode: pap|eap-md5> <"1 5 10 ..."> [warmup] [duration]
MODE=$1
LEVELS=${2//,/ }
WARMUP=${3:-10}
DUR=${4:-20}
RPWD="MAO5201314sm"
BENCH=$HOME/nac/bench
mkdir -p "$BENCH/json"
LOG="$BENCH/run-$MODE.log"
: > "$LOG"

flush_online() {
  redis-cli -a "$RPWD" --no-auth-warning --scan --pattern 'nac:online:*' 2>/dev/null \
    | xargs -r -n 200 redis-cli -a "$RPWD" --no-auth-warning UNLINK >/dev/null 2>&1
}

flush_online
MON="$BENCH/mon-$MODE.csv"
nohup bash "$BENCH/monitor.sh" "$MON" >/dev/null 2>&1 &
MONPID=$!
sleep 2

for C in $LEVELS; do
  flush_online
  echo "===== $(date +%H:%M:%S) mode=$MODE concurrency=$C warmup=${WARMUP}s measure=${DUR}s =====" | tee -a "$LOG"
  taskset -c 3 python3 "$BENCH/radius_bench.py" --server 192.168.80.36 sweep \
    --mode "$MODE" --concurrency "$C" --warmup "$WARMUP" --duration "$DUR" \
    --out "$BENCH/json/${MODE}-${C}.json" 2>&1 | tee -a "$LOG"
  # 每级结束记录资源快照
  redis-cli -a "$RPWD" --no-auth-warning INFO memory 2>/dev/null | grep -E "^used_memory:" | tee -a "$LOG"
  sleep 3
done
kill "$MONPID" 2>/dev/null
echo "DONE $MODE"
