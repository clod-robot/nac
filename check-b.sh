#!/bin/bash
echo '=== tcpdump files ==='
ls -la /tmp/rad2.pcap /tmp/tcpdump2.log 2>/dev/null
cat /tmp/tcpdump2.log 2>/dev/null | tail -5
echo '=== pcap packet count ==='
echo MAO5201314sm | sudo -S tcpdump -nn -r /tmp/rad2.pcap 2>/dev/null | wc -l
echo '=== pcap timings ==='
echo MAO5201314sm | sudo -S tcpdump -nn -tttt -r /tmp/rad2.pcap 2>/dev/null | tail -10
echo '=== server auth log 10:14-10:18 ==='
grep -E '10:1[4-8]' /home/mao/nac/logs/nac-auth.out | grep -v kafka | tail -30
