#!/bin/bash
echo MAO5201314sm | sudo -S timeout 80 tcpdump -ni enp3s0 -w /tmp/rad4.pcap 'udp port 1812 or udp port 1813' 2>/tmp/rad4.err
echo "exit=$?"
ls -la /tmp/rad4.pcap
