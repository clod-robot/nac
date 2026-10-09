#!/bin/bash
echo "===== errors detail ====="
for f in ~/nac/bench/json/eap-md5-*.json; do
  python3 -c "import json,sys;d=json.load(open('$f'));print('C=%d tps=%.1f err=%s' % (d['concurrency'],d['throughput_auth_per_s'],d['errors']))"
done
echo "===== monitor CSV (eap/info) ====="
cat ~/nac/bench/mon-eap-md5.csv
echo "===== UDP snmp now ====="
awk '/^Udp:/ {getline; print "InDgm="$2,"NoPorts="$3,"InErr="$4,"OutDgm="$5,"RcvBufErr="$6,"SndBufErr="$7}' /proc/net/snmp
echo "===== top java ====="
ps -eo pid,%cpu,rss,cmd --sort=-%cpu | grep -E 'app/nac|nacos' | grep -v grep | cut -c1-110
