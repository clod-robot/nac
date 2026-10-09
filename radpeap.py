import struct, sys, time

# usage: python3 radpeap.py /tmp/rad6.pcap
fn = sys.argv[1]
data = open(fn, 'rb').read()

# pcap global header 24B; assume ethernet linktype 1
off = 24
n = 0
def radius_attrs(payload):
    # payload: RADIUS packet
    if len(payload) < 20: return None, None, []
    code = payload[0]; rid = payload[1]
    l = struct.unpack('>H', payload[2:4])[0]
    attrs = []
    i = 20
    eap = b''
    while i + 2 <= min(l, len(payload)):
        t = payload[i]; ln = payload[i+1]
        if ln < 2 or i + ln > len(payload): break
        v = payload[i+2:i+ln]
        if t == 79:
            eap += v
        attrs.append((t, v))
        i += ln
    return code, rid, eap

def eap_info(eap):
    if len(eap) < 4: return 'EAP(empty %d)' % len(eap)
    code = eap[0]; rid = eap[1]; l = struct.unpack('>H', eap[2:4])[0]
    names = {1:'Request',2:'Response',3:'Success',4:'Failure'}
    s = 'EAP %s id=%d len=%d' % (names.get(code, code), rid, l)
    if len(eap) > 4:
        t = eap[4]
        if t == 25 and len(eap) > 5:
            fl = eap[5]
            bits = ''.join([c for c,b in zip('LMS',[0x80,0x40,0x20]) if fl & b])
            pos = 6
            tot = ''
            if fl & 0x80 and len(eap) >= 10:
                tot = str(struct.unpack('>I', eap[6:10])[0]); pos = 10
            s += ' PEAP flags[%s] total=%s tlsdata=%d tls0=%s' % (
                bits or '-', tot or '-', len(eap)-pos,
                eap[pos:pos+8].hex())
        else:
            s += ' type=%d' % t
    return s

while off + 16 <= len(data):
    ts, tus, incl, orig = struct.unpack('<IIII', data[off:off+16])
    off += 16
    pkt = data[off:off+incl]
    off += incl
    n += 1
    if len(pkt) < 34: continue
    eth_type = struct.unpack('>H', pkt[12:14])[0]
    if eth_type != 0x0800: continue
    ihl = (pkt[14] & 0x0F) * 4
    proto = pkt[23]
    if proto != 17: continue
    src = '.'.join(str(b) for b in pkt[26:30])
    dst = '.'.join(str(b) for b in pkt[30:34])
    udp_off = 14 + ihl
    sport, dport = struct.unpack('>HH', pkt[udp_off:udp_off+4])
    if 1812 not in (sport, dport): continue
    pl = pkt[udp_off+8:]
    t = ts + tus/1e6
    code, rid, eap = radius_attrs(pl)
    if code is None: continue
    codes = {1:'AccessReq',2:'AccessAcc',3:'AccessChal',4:'AccessRej',11:'AccessChal?'}
    print('%8.3f %15s:%-5d -> %-15s %-10s radId=%-3d %s' % (
        t, src, sport, dst, codes.get(code, code), rid, eap_info(eap)))
