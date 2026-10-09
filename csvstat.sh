#!/bin/bash
python3 - <<'PY'
import csv
rows=list(csv.DictReader(open('/home/mao/nac/bench/mon-eap-md5.csv')))
print("samples:",len(rows))
def col(k):
    out=[]
    for r in rows:
        try: out.append(float(r[k]))
        except: pass
    return out
for k in ['cpu_pct','cpu_auth_pct','rss_auth_mb','redis_mb','load1','mem_avail_mb']:
    v=col(k)
    if v:
        print("%-16s avg=%7.1f  max=%7.1f  last=%7.1f" % (k, sum(v)/len(v), max(v), v[-1]))
# UDP counters: first->last delta
for k in ['udp_indg','udp_inerr','udp_rcvberr','udp_sndberr']:
    v=col(k)
    if len(v)>=2:
        print("%-16s delta=%d (first %.0f last %.0f)" % (k, v[-1]-v[0], v[0], v[-1]))
PY
echo "===== current raw UDP counters ====="
awk '/^Udp:/{getline; print "InDgm="$2" NoPort="$3" InErr="$4" RcvbufErr="$7}' /proc/net/snmp
