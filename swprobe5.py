#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 读取迈普交换机已开启的 debug aaa all 实时输出（debug 此前已开），抓一个认证周期
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
    def read_for(self, ms=1500, max_ms=90000, more=True):
        self.s.settimeout(0.2)
        acc = bytearray()
        end = time.time() + ms/1000.0
        deadline = time.time() + max_ms/1000.0
        while time.time() < end and time.time() < deadline:
            try:
                d = self.s.recv(65536)
                if d:
                    acc += d
                    if more and b'more' in acc[-30:].lower():
                        self.s.sendall(b' ')
                    end = time.time() + ms/1000.0
            except socket.timeout: pass
            except Exception: break
        return clean(bytes(acc)).decode('gb18030', errors='replace')
    def cmd(self, c, ms=2000, max_ms=90000):
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
    print('ENABLE_OK', flush=True)
    print(t.cmd('terminal monitor', 1200), flush=True)
    print('--- listening debug 78s ---', flush=True)
    sys.stdout.flush()
    out = t.read_for(78000, 82000)
    print(out, flush=True)
    raw.sendall(b'exit\r\n'); raw.close()
    print('DONE')

if __name__ == '__main__':
    main()
