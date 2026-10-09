#!/bin/bash
# 资源采样：每 2 秒一行 CSV，写入 $1（默认 ~/nac/bench/mon.csv），被 kill 时正常结束
OUT=${1:-$HOME/nac/bench/mon.csv}
echo $$ > "$OUT.pid"
RPWD="MAO5201314sm"
PG=$(getconf PAGESIZE)
read_cpu_stat() { awk '/^cpu / {for(i=2;i<=8;i++) s+=$i; print s; exit}' /proc/stat; }
read_cpu_idle() { awk '/^cpu / {print $5; exit}' /proc/stat; }
read_udp() { awk '/^Udp:/ {getline; print $2,$4,$7,$8,$9,$10}' /proc/net/snmp; }

echo "ts,cpu_pct,cpu_auth_pct,rss_auth_mb,redis_mb,udp_indg,udp_inerr,udp_rcvberr,udp_sndberr,load1,mem_avail_mb" > "$OUT"
prev_tot=$(read_cpu_stat); prev_idle=$(read_cpu_idle)
prev_pt=0; prev_ptt=$(read_cpu_stat)
while true; do
  sleep 2
  tot=$(read_cpu_stat); idle=$(read_cpu_idle)
  dt=$((tot-prev_tot)); di=$((idle-prev_idle))
  cpu=$(awk -v d=$dt -v i=$di 'BEGIN{if(d>0) printf "%.1f",100*(d-i)/d; else print 0}')
  apid=$(pgrep -f "app/nac-auth" | head -1)
  if [ -n "$apid" ] && [ -r /proc/$apid/stat ]; then
    pt=$(awk '{print $14+$15}' /proc/$apid/stat)
    rss=$(awk -v p=$PG '{printf "%d", $2*p/1024/1024}' /proc/$apid/statm)
    authcpu=$(awk -v p=$pt -v pp=$prev_pt -v d=$dt 'BEGIN{if(d>0) printf "%.1f",100*(p-pp)/d; else print 0}')
    prev_pt=$pt
  else rss=0; authcpu=0; fi
  rm=$(redis-cli -a "$RPWD" --no-auth-warning INFO memory 2>/dev/null | awk -F: '/^used_memory:/{printf "%d",$2/1024/1024}')
  read u_indg u_inerr u_outdg u_rcvb u_sndb u_icsm <<< "$(read_udp)"
  load1=$(awk '{print $1}' /proc/loadavg)
  avail=$(awk '/^MemAvailable:/{printf "%d",$2/1024}' /proc/meminfo)
  echo "$(date +%H:%M:%S),$cpu,$authcpu,$rss,$rm,$u_indg,$u_inerr,$u_rcvb,$u_sndb,$load1,$avail" >> "$OUT"
  prev_tot=$tot; prev_idle=$idle
done
