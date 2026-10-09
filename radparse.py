#!/usr/bin/env python3
# Parse /tmp/rad.pcap: find Access-Request/Access-Accept pair on UDP 1812,
# verify Response-Authenticator and Message-Authenticator.
import struct, hmac, hashlib, sys, glob

SECRET = b'testing123'

def parse_pcap(path):
    pkts = []
    with open(path,'rb') as f:
        gh = f.read(24)
        magic = gh[:4]
        endian = '<' if magic in (b'\xd4\xc3\xb2\xa1', b'M<\xb1\xa1') else '>'
        # linktype offset 20
        lt = struct.unpack(endian+'I', gh[20:24])[0]
        while True:
            h = f.read(16)
            if len(h) < 16: break
            ts_s, ts_us, incl, orig = struct.unpack(endian+'IIII', h)
            data = f.read(incl)
            pkts.append((ts_s, ts_us, data, lt))
    return pkts

def extract_udp(payload, lt):
    # Linux cooked v2 (276) or EN10MB (1)
    try:
        if lt == 1:
            if len(payload) < 14: return None
            et = struct.unpack('>H', payload[12:14])[0]
            off = 14
            if et == 0x8100:
                et = struct.unpack('>H', payload[16:18])[0]; off = 18
            if et != 0x0800: return None
            ip = payload[off:]
        elif lt == 276:
            # LINUX_SLL2: 20-byte header
            if len(payload) < 20: return None
            et = struct.unpack('>H', payload[0:2])[0]
            if et != 0x0800: return None
            ip = payload[20:]
        elif lt == 113:
            if len(payload) < 16: return None
            et = struct.unpack('>H', payload[14:16])[0]
            if et != 0x0800: return None
            ip = payload[16:]
        else:
            return None
        ihl = (ip[0] & 0x0f) * 4
        proto = ip[9]
        if proto != 17: return None
        src = '.'.join(str(b) for b in ip[12:16]); dst = '.'.join(str(b) for b in ip[16:20])
        udp = ip[ihl:]
        sport, dport, ulen, csum = struct.unpack('>HHHH', udp[:8])
        return src, sport, dst, dport, udp[8:8+ulen-8]
    except Exception as e:
        return None

def attrs(b):
    i = 0
    while i < len(b):
        t, l = b[i], b[i+1]
        yield t, b[i+2:i+l]
        i += l

def main():
    path = sys.argv[1] if len(sys.argv) > 1 else '/tmp/rad.pcap'
    pkts = parse_pcap(path)
    reqs = {}
    for ts_s, ts_us, data, lt in pkts:
        u = extract_udp(data, lt)
        if not u: continue
        src, sport, dst, dport, rad = u
        if dport != 1812 and sport != 1812: continue
        if len(rad) < 20: continue
        code, rid, length = rad[0], rad[1], struct.unpack('>H', rad[2:4])[0]
        vec = rad[4:20]
        body = rad[20:length]
        tname = {1:'Request',2:'Accept',3:'Reject',11:'Challenge'}.get(code, str(code))
        print('pkt %s.%06d %s:%d -> %s:%d code=%s id=%d len=%d' % (ts_s, ts_us, src, sport, dst, dport, tname, rid, length))
        if code == 1:
            # remember last request authenticator per id
            reqs[rid] = vec
        if code in (2, 3, 11):
            reqauth = reqs.get(rid)
            print('  respAuth =', vec.hex())
            if reqauth:
                calc = hashlib.md5(rad[0:4] + reqauth + body + SECRET).digest()
                print('  respAuth calc =', calc.hex(), 'MATCH' if calc == vec else '*** MISMATCH ***')
            msgauth = None
            for t, v in attrs(body):
                if t == 80: msgauth = v
                if t == 79:
                    print('  EAP-Message:', v.hex())
            if msgauth and reqauth:
                idx = body.index(bytes([80, 18]))
                zeroed = rad[:20+idx+2] + b'\x00'*16 + rad[20+idx+18:]
                vA = hmac.new(SECRET, zeroed, hashlib.md5).digest()
                vB = hmac.new(SECRET, zeroed[:4]+reqauth+zeroed[20:], hashlib.md5).digest()
                print('  msgAuth got  =', msgauth.hex())
                print('  HMAC A respAuth-slot =', vA.hex(), 'MATCH' if vA==msgauth else '')
                print('  HMAC B reqAuth-slot  =', vB.hex(), 'MATCH' if vB==msgauth else '')

if __name__ == '__main__':
    main()
