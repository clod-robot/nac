#!/bin/bash
sudo rm -f /tmp/peap5.pcap
echo MAO5201314sm | sudo -S timeout 60 tcpdump -ni enp3s0 -w /tmp/peap5.pcap 'udp port 1812 or udp port 1813' 2>/tmp/peap5.err
echo "exit=$?"
ls -la /tmp/peap5.pcap
