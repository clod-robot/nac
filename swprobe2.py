#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys, time, socket

HOST = '192.168.100.1'

def clean(b):
    out = bytearray(); i = 0
    while i < len(b):
        if b[i] == 255:
            if i + 1 >= len(b): break
            c = b[i+1]
            if c == 250:
                j = i + 2
                while j < len(b) - 1 and not (b[j] == 255 and b[j+1] == 240): j += 1
                i = j + 2
            elif 251 <= c <= 254: i += 3
            else: i += 2
        else:
            out.append(b[i]); i += 1
    return bytes(out)

class Tel:
    def __init__(self, s): self.s = s
    def read_for(self, ms=1500, max_ms=60000):
        self.s.settimeout(0.1)
        acc = bytearray()
        end = time.time() + ms/1000.0
        deadline = time.time() + max_ms/1000.0
        while time.time() < end and time.time() < deadline:
            try:
                d = self.s.recv(65536)
                if d:
                    acc += d
                    if b'more' in acc[-30:].lower():
                        self.s.sendall(b' ')
                    end = time.time() + ms/1000.0
            except socket.timeout: pass
            except Exception: break
        txt = clean(bytes(acc)).decode('gb18030', errors='replace')
        return '\n'.join(ln for ln in txt.splitlines() if '---MORE---' not in ln and '--More--' not in ln)
    def cmd(self, c, ms=4000, max_ms=60000):
        self.s.sendall(c.encode() + b'\r\n')
        return self.read_for(ms, max_ms)

def main():
    raw = socket.create_connection((HOST, 23), timeout=5)
    t = Tel(raw)
    t.read_for(2000)
    t.cmd('enable', 1000)
    raw.sendall(b'Maipu@1993\r\n')
    r = t.read_for(1800)
    if not r.rstrip().endswith('#'):
        print('ENABLE_FAILED', r); sys.exit(2)
    print('ENABLE_OK')
    cmds = [
        ('show dot1x auth-fail-user', 6000, 30000),
        ('show dot1x statistic', 5000, 20000),
        ('show dot1x auth-address 803f.5df2.6d81', 4000, 15000),
        ('show dot1x auth-address 803f5df26d81', 4000, 15000),
        ('show dot1x client', 4000, 15000),
        ('show dot1x config', 5000, 20000),
        ('show dot1x global', 4000, 15000),
        ('show aaa server', 4000, 15000),
        ('show aaa session', 4000, 15000),
        ('show aaa configuration', 4000, 15000),
    ]
    for c, w, mx in cmds:
        out = t.cmd(c, w, mx)
        print('\n===== %s =====' % c); print(out)
    raw.sendall(b'exit\r\n'); raw.close()
    print('DONE')

if __name__ == '__main__':
    main()
