#!/bin/bash
echo MAO5201314sm | sudo -S pkill -f 'tcpdump.*1812' 2>/dev/null
sleep 1
nohup bash -c "echo MAO5201314sm | sudo -S timeout 80 tcpdump -ni enp3s0 -w /tmp/rad2.pcap udp port 1812" >/tmp/tcpdump2.log 2>&1 &
echo "tcpdump started pid $!"
