#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NAC RADIUS 认证压测工具（纯标准库 asyncio）
支持 PAP 与 EAP-MD5（完整 Identity -> MD5-Challenge 两次交互）两种认证方式。
用法:
  python3 radius_bench.py smoke --server 192.168.80.36
  python3 radius_bench.py sweep --mode eap-md5 --concurrency 1,5,10,25,50,100,200,400 \
      --warmup 10 --duration 20 --out /home/mao/nac/bench/eap.json
"""
import argparse
import asyncio
import hashlib
import hmac
import json
import os
import secrets
import socket
import struct
import time

# ---------------- RADIUS 协议 ----------------
SECRET = b"testing123"
CODE_ACCESS_REQUEST, CODE_ACCEPT, CODE_REJECT, CODE_CHALLENGE = 1, 2, 3, 11
ATTR_USER_PASSWORD, ATTR_NAS_IP = 2, 4
ATTR_CALLING_STATION, ATTR_NAS_IDENT, ATTR_EAP_MSG, ATTR_MSG_AUTH = 31, 32, 79, 80
ATTR_USER_NAME = 1

USERS = [
    ("admin", "Admin@123"),
    ("liyang", "Ly@2026pwd"),
    ("wangfang", "Wf$secure99"),
    ("sunqi", "Sq!access08"),
    ("testuser", "Test@Test1"),
    ("guest01", "Guest01@pwd"),
]


def attr(t, v: bytes) -> bytes:
    return bytes([t, 2 + len(v)]) + v


def encode_radius(code, req_id, authenticator, attrs: bytes) -> bytes:
    total = 20 + len(attrs)
    return bytes([code, req_id]) + struct.pack(">H", total) + authenticator + attrs


def sign_packet(pkt: bytes) -> bytes:
    """RFC2869: 回填 Message-Authenticator(属性80) = HMAC-MD5(secret, 整包)。
    要求 pkt 已含 type=80,len=18,值为 16 字节 0 的占位属性。"""
    i = 20
    while i + 2 <= len(pkt):
        t, ln = pkt[i], pkt[i + 1]
        if t == ATTR_MSG_AUTH:
            hm = hmac.new(SECRET, pkt, hashlib.md5).digest()
            return pkt[:i + 2] + hm + pkt[i + 2 + 16:]
        if ln < 2:
            break
        i += ln
    return pkt


def parse_attrs(data: bytes):
    out = {}
    i = 0
    while i + 2 <= len(data):
        t, ln = data[i], data[i + 1]
        if ln < 2 or i + ln > len(data):
            break
        # RFC2865: 同类型多属性（如 EAP-Message 分片）需要拼接
        out.setdefault(t, b"")
        out[t] += data[i + 2:i + ln]
        i += ln
    return out


def pap_password(password: str, ra: bytes) -> bytes:
    pw = password.encode()
    pw = pw[:128].ljust(((len(password) + 15) // 16) * 16, b"\x00")
    res, prev = b"", ra
    for off in range(0, len(pw), 16):
        block = pw[off:off + 16]
        h = hashlib.md5(SECRET + prev).digest()
        enc = bytes(a ^ b for a, b in zip(block, h))
        res += enc
        prev = enc
    return res


def eap_response_identity(eap_id: int, username: str) -> bytes:
    u = username.encode()
    return bytes([2, eap_id]) + struct.pack(">H", 5 + len(u)) + bytes([1]) + u


def eap_response_md5(eap_id: int, password: str, challenge: bytes) -> bytes:
    h = hashlib.md5(bytes([eap_id]) + password.encode() + challenge).digest()
    data = bytes([16]) + h
    return bytes([2, eap_id]) + struct.pack(">H", 5 + len(data)) + bytes([4]) + data


def mac_for(i: int) -> str:
    return "02-00-%02X-%02X-%02X-%02X" % (
        (i >> 16) & 255, (i >> 8) & 255, i & 255, 0)


# ---------------- 异步 UDP 端点 ----------------
class RadiusEndpoint(asyncio.DatagramProtocol):
    def __init__(self, loop):
        self.loop = loop
        self.transport = None
        self.pending = {}
        self.next_id = 0

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, addr):
        if len(data) < 20:
            return
        rid = data[1]
        fut = self.pending.pop(rid, None)
        if fut and not fut.done():
            fut.set_result(data)

    def error_received(self, exc):
        pass

    def _alloc_id(self):
        for _ in range(256):
            rid = self.next_id
            self.next_id = (self.next_id + 1) & 255
            if rid not in self.pending:
                return rid
        raise RuntimeError("256 outstanding requests on one socket")

    async def exchange(self, packet: bytes, timeout=3.0):
        rid = packet[1]
        fut = self.loop.create_future()
        self.pending[rid] = fut
        try:
            self.transport.sendto(packet)
            return await asyncio.wait_for(fut, timeout)
        finally:
            self.pending.pop(rid, None)

    def build_packet(self, attrs_body: bytes):
        rid = self._alloc_id()
        return sign_packet(encode_radius(CODE_ACCESS_REQUEST, rid, secrets.token_bytes(16), attrs_body))


async def make_endpoints(loop, server, port, n=4):
    eps = []
    for _ in range(n):
        _, proto = await loop.create_datagram_endpoint(
            lambda: RadiusEndpoint(loop),
            remote_addr=(server, port), family=socket.AF_INET)
        eps.append(proto)
    return eps


def base_attrs(username, station):
    return (attr(ATTR_USER_NAME, username.encode()) +
            attr(ATTR_NAS_IDENT, b"nac-bench") +
            attr(ATTR_NAS_IP, socket.inet_aton("127.0.0.1")) +
            attr(ATTR_CALLING_STATION, station.encode()) +
            attr(ATTR_MSG_AUTH, b"\x00" * 16))


# ---------------- 认证流程 ----------------
async def auth_pap(ep: RadiusEndpoint, username, password, station):
    ra = secrets.token_bytes(16)
    # User-Password 需要用包的 Request Authenticator 加密：单独构造 authenticator
    rid = ep._alloc_id()
    body = (attr(ATTR_USER_NAME, username.encode()) +
            attr(ATTR_USER_PASSWORD, pap_password(password, ra)) +
            attr(ATTR_NAS_IDENT, b"nac-bench") +
            attr(ATTR_NAS_IP, socket.inet_aton("127.0.0.1")) +
            attr(ATTR_CALLING_STATION, station.encode()) +
            attr(ATTR_MSG_AUTH, b"\x00" * 16))
    pkt = sign_packet(encode_radius(CODE_ACCESS_REQUEST, rid, ra, body))
    resp = await ep.exchange(pkt)
    return resp[0]


async def auth_eap_md5(ep: RadiusEndpoint, username, password, station):
    # 1) EAP-Response/Identity
    eap1 = eap_response_identity(1, username)
    pkt1 = ep.build_packet(base_attrs(username, station) + attr(ATTR_EAP_MSG, eap1))
    resp1 = await ep.exchange(pkt1)
    if resp1[0] != CODE_CHALLENGE:
        return resp1[0]
    a = parse_attrs(resp1[20:])
    eap_data = a.get(ATTR_EAP_MSG)
    if not eap_data or len(eap_data) < 22 or eap_data[4] != 4:
        return -1
    eap_id = eap_data[1]
    challenge = eap_data[6:22]
    # 2) EAP-Response/MD5-Challenge
    eap2 = eap_response_md5(eap_id, password, challenge)
    pkt2 = ep.build_packet(base_attrs(username, station) + attr(ATTR_EAP_MSG, eap2))
    resp2 = await ep.exchange(pkt2)
    return resp2[0]


# ---------------- 压测执行 ----------------
async def run_stage(eps, mode, concurrency, warmup, duration):
    loop = asyncio.get_event_loop()
    lat, codes, errs = [], {}, {}
    deadline_warm = time.perf_counter() + warmup
    deadline = deadline_warm + duration
    measuring = {"v": False}
    stop = {"v": False}

    async def worker(idx):
        ep = eps[idx % len(eps)]
        station = mac_for(idx + 1)
        username, password = USERS[idx % len(USERS)]
        while not stop["v"]:
            t0 = time.perf_counter()
            try:
                if mode == "pap":
                    code = await auth_pap(ep, username, password, station)
                else:
                    code = await auth_eap_md5(ep, username, password, station)
                dt = time.perf_counter() - t0
                if measuring["v"]:
                    lat.append(dt)
                    codes[code] = codes.get(code, 0) + 1
            except asyncio.TimeoutError:
                if measuring["v"]:
                    errs["timeout"] = errs.get("timeout", 0) + 1
            except Exception as e:
                if measuring["v"]:
                    key = type(e).__name__
                    errs[key] = errs.get(key, 0) + 1
            # 让协程自然让出
            await asyncio.sleep(0)

    workers = [loop.create_task(worker(i)) for i in range(concurrency)]
    await asyncio.sleep(warmup)
    measuring["v"] = True
    t_start = time.perf_counter()
    await asyncio.sleep(duration)
    t_end = time.perf_counter()
    stop["v"] = True
    # 等待最后在途请求结束（最多 5s）
    await asyncio.gather(*workers, return_exceptions=True)

    total = sum(codes.values()) + sum(errs.values())
    elapsed = t_end - t_start
    lat.sort()

    def pct(p):
        if not lat:
            return None
        return lat[min(len(lat) - 1, int(len(lat) * p))] * 1000

    result = {
        "mode": mode, "concurrency": concurrency, "warmup_s": warmup, "duration_s": duration,
        "total": total, "throughput_auth_per_s": round(total / elapsed, 1),
        "accept": codes.get(CODE_ACCEPT, 0), "reject": codes.get(CODE_REJECT, 0),
        "challenge_only": codes.get(CODE_CHALLENGE, 0), "other_codes": {str(k): v for k, v in codes.items() if k not in (2, 3, 11)},
        "errors": errs,
        "latency_ms": {
            "avg": round(sum(lat) / len(lat) * 1000, 2) if lat else None,
            "p50": round(pct(0.50), 2) if lat else None,
            "p90": round(pct(0.90), 2) if lat else None,
            "p95": round(pct(0.95), 2) if lat else None,
            "p99": round(pct(0.99), 2) if lat else None,
            "max": round(lat[-1] * 1000, 2) if lat else None,
        },
        "success_rate_pct": round(codes.get(CODE_ACCEPT, 0) * 100.0 / total, 3) if total else 0.0,
    }
    return result


def print_result(r):
    print(("[{mode:7s}] C={concurrency:<4d} 吞吐={tps:>8.1f} auth/s  成功={ok:<7d} "
           "失败={rej:<5d} 错误={err:<5d} 成功率={sr:6.2f}%  "
           "avg={avg:>7.2f} p50={p50:>7.2f} p95={p95:>7.2f} p99={p99:>7.2f} max={mx:>8.2f} ms").format(
        mode=r["mode"], concurrency=r["concurrency"], tps=r["throughput_auth_per_s"],
        ok=r["accept"], rej=r["reject"], err=sum(r["errors"].values()), sr=r["success_rate_pct"],
        avg=r["latency_ms"]["avg"] or 0, p50=r["latency_ms"]["p50"] or 0,
        p95=r["latency_ms"]["p95"] or 0, p99=r["latency_ms"]["p99"] or 0,
        mx=r["latency_ms"]["max"] or 0))


async def main_smoke(args):
    loop = asyncio.get_event_loop()
    eps = await make_endpoints(loop, args.server, args.port, 2)
    st = mac_for(999)
    for mode, user, pwd in [("pap", "wangfang", "Wf$secure99"),
                            ("eap-md5", "wangfang", "Wf$secure99"),
                            ("pap-wrong", "wangfang", "wrong-pass")]:
        ep = eps[0]
        if mode == "pap":
            code = await auth_pap(ep, user, pwd, st)
        elif mode == "pap-wrong":
            code = await auth_pap(ep, user, pwd, mac_for(998))
        else:
            code = await auth_eap_md5(ep, user, pwd, st)
        label = {2: "Access-Accept", 3: "Access-Reject", 11: "Challenge"}.get(code, code)
        print("smoke %-9s -> %s" % (mode, label))
    for ep in eps:
        ep.transport.close()


async def main_sweep(args):
    loop = asyncio.get_event_loop()
    ports = [int(x) for x in args.ports.split(",")] if args.ports else [args.port]
    eps = []
    per = max(2, 12 // len(ports))
    for pt in ports:
        eps += await make_endpoints(loop, args.server, pt, per)
    levels = [int(x) for x in args.concurrency.split(",")]
    results = []
    for c in levels:
        r = await run_stage(eps, args.mode, c, args.warmup, args.duration)
        results.append(r)
        print_result(r)
    for ep in eps:
        ep.transport.close()
    if args.out:
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        print("saved -> %s" % args.out)


def main():
    global SECRET
    p = argparse.ArgumentParser()
    p.add_argument("--server", default="192.168.80.36")
    p.add_argument("--port", type=int, default=1812)
    p.add_argument("--secret", default="testing123")
    sub = p.add_subparsers(dest="cmd", required=True)
    ps = sub.add_parser("smoke")
    pw = sub.add_parser("sweep")
    pw.add_argument("--mode", choices=["pap", "eap-md5"], default="eap-md5")
    pw.add_argument("--concurrency", default="1,5,10,25,50,100,200,400")
    pw.add_argument("--warmup", type=int, default=10)
    pw.add_argument("--duration", type=int, default=20)
    pw.add_argument("--out", default="")
    pw.add_argument("--ports", default="", help="comma-separated ports for multi-instance round-robin")
    args = p.parse_args()
    SECRET = args.secret.encode()
    if args.cmd == "smoke":
        asyncio.run(main_smoke(args))
    else:
        asyncio.run(main_sweep(args))


if __name__ == "__main__":
    main()
