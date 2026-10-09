#!/bin/bash
# 在线会话容量测试：灌 20 万会话，监控 Redis 内存/计数曲线，灌满后测保持成本与管理接口延迟
cd ~/nac/bench || exit 1
RPWD="MAO5201314sm"
R="redis-cli -a $RPWD --no-auth-warning"
OUT=~/nac/bench/online-test.log
exec > "$OUT" 2>&1

# 防多实例残留
pkill -f 'python3 online_fill.py' 2>/dev/null
[ -f ~/nac/bench/online-mon.pid ] && kill "$(cat ~/nac/bench/online-mon.pid)" 2>/dev/null
sleep 1

echo "===== flush online ====="
$R --scan --pattern 'nac:online:*' | xargs -r -n 200 $R UNLINK >/dev/null
sleep 2
$R DBSIZE
$R INFO memory | grep -E 'used_memory:|used_memory_rss:'

# 内存/计数采样
(
  echo $$ > ~/nac/bench/online-mon.pid
  while true; do
    sc=$($R SCARD nac:online:ids)
    um=$($R INFO memory | awk -F: '/^used_memory:/{print $2}' | tr -d '\r')
    rss=$($R INFO memory | awk -F: '/^used_memory_rss:/{print $2}' | tr -d '\r')
    la=$(awk '{print $1}' /proc/loadavg)
    echo "$(date +%H:%M:%S),$sc,$um,$rss,$la"
    sleep 5
  done
) > ~/nac/bench/online-mon.csv &
MON=$!
sleep 1

echo "===== fill 200000 sessions (C=200 PAP) ====="
taskset -c 3 python3 online_fill.py --server 192.168.80.36 \
  --concurrency 200 --target 200000 --max-seconds 900

kill $MON 2>/dev/null
sleep 5
echo "===== after fill ====="
echo "SCARD ids:        $($R SCARD nac:online:ids)"
echo "dbsize:           $($R DBSIZE)"
$R INFO memory | grep -E 'used_memory:|used_memory_rss:|mem_fragmentation'
echo "---- per-key samples ----"
SID=$($R SRANDMEMBER nac:online:ids)
echo "sample id: $SID"
echo "session strlen:   $($R STRLEN nac:online:session:$SID)"
echo "session MEMORY USAGE: $($R MEMORY USAGE nac:online:session:$SID)"
echo "ids SET MEMORY USAGE: $($R MEMORY USAGE nac:online:ids)"
$R --scan --pattern 'nac:online:user:*' | while read k; do
  echo "user-set $k scard=$($R SCARD "$k") usage=$($R MEMORY USAGE "$k")"
done

echo "===== steady-state cost (idle 15s, sample load x3) ====="
for i in 1 2 3; do uptime; sleep 5; done
top -b -n 1 | head -12

echo "===== management API latency ====="
echo "-- /online/count (internally does 2x full scan) --"
curl -s -m 60 -o /tmp/cnt.json -w 'http=%{http_code} total=%{time_total}s size=%{size_download}\n' \
  http://localhost:9001/api/auth/online/count
head -c 300 /tmp/cnt.json; echo
echo "-- redis SCARD baseline --"
$R --latency-history -i 1 2>/dev/null &
LP=$!; sleep 3; kill $LP 2>/dev/null

echo "===== redis persistence config ====="
$R CONFIG GET save
$R CONFIG GET appendonly
echo "ONLINE_TEST_DONE"
