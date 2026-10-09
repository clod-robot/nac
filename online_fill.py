#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在线会话灌量：PAP 成功认证即产生一条 nac:online 会话(UUID)，
用固定 6 用户轮转（命中 L1 缓存，零 DB），灌入 --target 条会话或达 --max-seconds。"""
import argparse
import asyncio
import hashlib
import secrets
import socket
import struct
import time

SECRET = b"testing123"
USERS = [
    ("admin", "Admin@123"),
    ("liyang", "Ly@2026pwd"),
    ("wangfang", "Wf$secure99"),
    ("sunqi", "Sq!access08"),
    ("testuser", "Test@Test1"),
    ("guest01", "Guest01@pwd"),
]
CODE_ACCEPT = 2


def attr(t, v):
    return bytes([t, 2 + len(v)]) + v


def parse_ok(data):
    return len(data) >= 2 and data[0] == CODE_ACCEPT


class EP(asyncio.DatagramProtocol):
    def __init__(self, loop):
        self.loop = loop
        self.tr = None
        self.pending = {}
        self.next_id = 0

    def connection_made(self, tr):
        self.tr = tr

    def datagram_received(self, data, addr):
        fut = self.pending.pop(data[1], None)
        if fut and not fut.done():
            fut.set_result(data)

    def alloc(self):
        for _ in range(256):
            rid = self.next_id
            self.next_id = (self.next_id + 1) & 255
            if rid not in self.pending:
                return rid
        raise RuntimeError("256 outstanding")

    async def exchange(self, pkt, timeout=3.0):
        rid = pkt[1]
        fut = self.loop.create_future()
        self.pending[rid] = fut
        try:
            self.tr.sendto(pkt)
            return await asyncio.wait_for(fut, timeout)
        finally:
            self.pending.pop(rid, None)


def pap_pkt(ep, username, password, station_idx):
    ra = secrets.token_bytes(16)
    pw = password.encode().ljust(16, b"\x00")
    h = hashlib.md5(SECRET + ra).digest()
    enc = bytes(a ^ b for a, b in zip(pw, h))
    station = "02-00-%02X-%02X-%02X-00" % (
        (station_idx >> 16) & 255, (station_idx >> 8) & 255, station_idx & 255)
    body = (attr(1, username.encode()) + attr(2, enc) +
            attr(32, b"nac-bench") + attr(4, socket.inet_aton("127.0.0.1")) +
            attr(31, station.encode()))
    rid = ep.alloc()
    return struct.pack(">BBH", 1, rid, 20 + len(body)) + ra + body


async def main(args):
    loop = asyncio.get_event_loop()
    eps = []
    for _ in range(4):
        _, p = await loop.create_datagram_endpoint(
            lambda: EP(loop), remote_addr=(args.server, args.port), family=socket.AF_INET)
        eps.append(p)

    done = {"n": 0}
    stop = False
    lock = asyncio.Lock()
    t0 = time.perf_counter()

    async def worker(idx):
        nonlocal stop
        ep = eps[idx % 4]
        u, pw = USERS[idx % len(USERS)]
        while not stop:
            try:
                pkt = pap_pkt(ep, u, pw, idx + 1)
                resp = await ep.exchange(pkt)
                if parse_ok(resp):
                    async with lock:
                        done["n"] += 1
                        if done["n"] >= args.target:
                            stop = True
            except Exception:
                pass
            await asyncio.sleep(0)

    workers = [loop.create_task(worker(i)) for i in range(args.concurrency)]
    last = 0
    while not stop and time.perf_counter() - t0 < args.max_seconds:
        await asyncio.sleep(5)
        cur = done["n"]
        el = time.perf_counter() - t0
        print("t=%6.1fs sessions=%d (+%d in 5s, %.0f/s avg)" %
              (el, cur, cur - last, cur / el), flush=True)
        last = cur
    stop = True
    await asyncio.gather(*workers, return_exceptions=True)
    el = time.perf_counter() - t0
    print("FILL_DONE sessions=%d elapsed=%.1fs avg=%.0f/s" % (done["n"], el, done["n"] / el))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--server", default="192.168.80.36")
    p.add_argument("--port", type=int, default=1812)
    p.add_argument("--secret", default="testing123")
    p.add_argument("--concurrency", type=int, default=200)
    p.add_argument("--target", type=int, default=100000)
    p.add_argument("--max-seconds", type=int, default=600)
    a = p.parse_args()
    SECRET = a.secret.encode()
    asyncio.run(main(a))
