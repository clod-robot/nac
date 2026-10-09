#!/bin/bash
exec > /tmp/jstack-prof.txt 2>&1
cd ~/nac/bench
taskset -c 3 python3 radius_bench.py --server 192.168.80.36 sweep \
  --mode eap-md5 --concurrency 100 --warmup 6 --duration 16 \
  --out json/prof-100.json > /tmp/jstack-bench.log 2>&1 &
BPID=$!
sleep 11
APID=$(pgrep -f "app/nac-auth" | head -1)
echo "auth=$APID"
echo "===== busy threads (TID -> name) ====="
top -b -n 1 -H -p "$APID" | awk 'NR>=8 && $9>5 {print $1, $9}' > /tmp/busy-tids.txt
cat /tmp/busy-tids.txt
~/software/jdk1.8.0_202/bin/jstack "$APID" > /tmp/auth-jstack.txt 2>&1
while read tid cpu; do
  nid=$(printf '0x%x' "$tid")
  echo "--- TID=$tid CPU=$cpu nid=$nid"
  grep -A1 "nid=$nid " /tmp/auth-jstack.txt | head -2
done < /tmp/busy-tids.txt
echo "===== context switches (whole machine) ====="
vmstat 1 3
wait $BPID
tail -2 /tmp/jstack-bench.log
