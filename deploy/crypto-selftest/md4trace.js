const crypto = require('crypto');

function rol(v, s) { return ((v << s) | (v >>> (32 - s))) >>> 0; }
function add() {
  let r = 0;
  for (const a of arguments) r = (r + a) >>> 0;
  return r;
}

function md4(msg) {
  // pad
  const len = msg.length;
  const bitLen = BigInt(len) * 8n;
  let paddedLen = Math.floor((len + 8) / 64 + 1) * 64;
  const m = Buffer.alloc(paddedLen);
  msg.copy(m);
  m[len] = 0x80;
  m.writeBigUInt64LE(bitLen, paddedLen - 8);

  let a = 0x67452301, b = 0xefcdab89, c = 0x98badcfe, d = 0x10325476;
  const trace = [];
  const log = (tag, A, B, C, D) => trace.push(`${tag} A=${A.toString(16).padStart(8,'0')} B=${B.toString(16).padStart(8,'0')} C=${C.toString(16).padStart(8,'0')} D=${D.toString(16).padStart(8,'0')}`);

  for (let pos = 0; pos < paddedLen; pos += 64) {
    const x = [];
    for (let i = 0; i < 16; i++) x.push(m.readUInt32LE(pos + i * 4));
    let A = a >>> 0, B = b >>> 0, C = c >>> 0, D = d >>> 0;

    const F = (x, y, z) => ((x & y) | (~x & z)) >>> 0;
    const G = (x, y, z) => ((x & y) | (x & z) | (y & z)) >>> 0;
    const H = (x, y, z) => (x ^ y ^ z) >>> 0;

    const ff = (which, cur, p, q, r, xk, s) => {
      let t = add(cur, F(p, q, r), xk);
      t = add(rol(t, s), p);
      return t >>> 0;
    };

    // Round 1
    const r1 = [
      ['A','B','C','D',0,3],['D','A','B','C',1,7],['C','D','A','B',2,11],['B','C','D','A',3,19],
      ['A','B','C','D',4,3],['D','A','B','C',5,7],['C','D','A','B',6,11],['B','C','D','A',7,19],
      ['A','B','C','D',8,3],['D','A','B','C',9,7],['C','D','A','B',10,11],['B','C','D','A',11,19],
      ['A','B','C','D',12,3],['D','A','B','C',13,7],['C','D','A','B',14,11],['B','C','D','A',15,19]
    ];
    const regs = { A, B, C, D };
    for (const [dst, p, q, r, k, s] of r1) {
      regs[dst] = add(rol(add(regs[dst], F(regs[p], regs[q], regs[r]), x[k]), s), regs[p]) >>> 0;
      trace.push(`1 ${dst}${k} A=${regs.A.toString(16).padStart(8,'0')} B=${regs.B.toString(16).padStart(8,'0')} C=${regs.C.toString(16).padStart(8,'0')} D=${regs.D.toString(16).padStart(8,'0')}`);
    }
    A = regs.A; B = regs.B; C = regs.C; D = regs.D;
    const r2 = [
      ['A','B','C','D',0,3],['D','A','B','C',4,5],['C','D','A','B',8,9],['B','C','D','A',12,13],
      ['A','B','C','D',1,3],['D','A','B','C',5,5],['C','D','A','B',9,9],['B','C','D','A',13,13],
      ['A','B','C','D',2,3],['D','A','B','C',6,5],['C','D','A','B',10,9],['B','C','D','A',14,13],
      ['A','B','C','D',3,3],['D','A','B','C',7,5],['C','D','A','B',11,9],['B','C','D','A',15,13]
    ];
    for (const [dst, p, q, r, k, s] of r2) {
      regs[dst] = add(rol(add(regs[dst], G(regs[p], regs[q], regs[r]), x[k], 0x5A827999), s), regs[p]) >>> 0;
      trace.push(`2 ${dst}${k} A=${regs.A.toString(16).padStart(8,'0')} B=${regs.B.toString(16).padStart(8,'0')} C=${regs.C.toString(16).padStart(8,'0')} D=${regs.D.toString(16).padStart(8,'0')}`);
    }
    A = regs.A; B = regs.B; C = regs.C; D = regs.D;
    const r3 = [
      ['A','B','C','D',0,3],['D','A','B','C',8,9],['C','D','A','B',4,11],['B','C','D','A',12,15],
      ['A','B','C','D',2,3],['D','A','B','C',10,9],['C','D','A','B',6,11],['B','C','D','A',14,15],
      ['A','B','C','D',1,3],['D','A','B','C',9,9],['C','D','A','B',5,11],['B','C','D','A',13,15],
      ['A','B','C','D',3,3],['D','A','B','C',11,9],['C','D','A','B',7,11],['B','C','D','A',15,15]
    ];
    for (const [dst, p, q, r, k, s] of r3) {
      regs[dst] = add(rol(add(regs[dst], H(regs[p], regs[q], regs[r]), x[k], 0x6ED9EBA1), s), regs[p]) >>> 0;
      trace.push(`3 ${dst}${k} A=${regs.A.toString(16).padStart(8,'0')} B=${regs.B.toString(16).padStart(8,'0')} C=${regs.C.toString(16).padStart(8,'0')} D=${regs.D.toString(16).padStart(8,'0')}`);
    }
    a = add(a, regs.A); b = add(b, regs.B); c = add(c, regs.C); d = add(d, regs.D);
  }
  const out = Buffer.alloc(16);
  out.writeUInt32LE(a >>> 0, 0); out.writeUInt32LE(b >>> 0, 4);
  out.writeUInt32LE(c >>> 0, 8); out.writeUInt32LE(d >>> 0, 12);
  return { hex: out.toString('hex'), trace };
}

const r = md4(Buffer.alloc(0));
const ref = crypto.createHash('md4').update(Buffer.alloc(0)).digest('hex');
console.log('mine =', r.hex, ' ref =', ref, r.hex === ref ? 'OK' : 'MISMATCH');
console.log(r.trace.slice(0, 6).join('\n'));
require('fs').writeFileSync(__dirname + '/md4trace_js.txt', r.trace.join('\n'));
