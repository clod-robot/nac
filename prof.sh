#!/bin/bash
exec > /tmp/prof-out.txt 2>&1
# C=200 单级压测 + 线程级 CPU 采样
cd ~/nac/bench
redis-cli -a MAO5201314sm --no-auth-warning --scan --pattern 'nac:online:*' 2>/dev/null \
  | xargs -r -n 200 redis-cli -a MAO5201314sm --no-auth-warning UNLINK >/dev/null 2>&1

taskset -c 3 python3 radius_bench.py --server 192.168.80.36 sweep \
  --mode eap-md5 --concurrency 200 --warmup 8 --duration 18 \
  --out json/prof-200.json > /tmp/prof-bench.log 2>&1 &
BPID=$!
sleep 10
APID=$(pgrep -f "app/nac-auth" | head -1)
echo "auth pid=$APID, bench pid=$BPID"
for i in 1 2 3; do
  echo "========== top -H sample $i =========="
  top -b -n 1 -H -p "$APID" | head -25
  sleep 3
done
echo "========== whole machine top (python client / jvms) =========="
top -b -n 1 | head -20
wait $BPID
echo "========== bench result =========="
grep 'C=200' /tmp/prof-bench.log
