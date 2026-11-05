// Procedural score + SFX, driven by the same timeline as the visuals.
// Tense pulse under the pain points, a riser + impact on the brand reveal,
// then an upbeat four-on-the-floor groove under the features.
import { writeFileSync } from "node:fs";

type TL = { total: number; lines: { scene: string; start: number; end: number }[] };

const SR = 44100;
const TAU = Math.PI * 2;
const midi = (n: number) => 440 * Math.pow(2, (n - 69) / 12);

export function buildMusic(tl: TL, path: string) {
  const n = Math.ceil(tl.total * SR);
  const out = new Float32Array(n);
  const sceneAt = (s: string) => {
    const i = tl.lines.findIndex((l) => l.scene === s);
    return i === 0 ? 0 : tl.lines[i].start - 0.12; // same scene boundary rule as index.html
  };
  const turn = sceneAt("logo");
  const groove = sceneAt("cleanup");
  const outro = sceneAt("end");

  let noiseSeed = 1;
  const noise = () => ((noiseSeed = (noiseSeed * 1103515245 + 12345) & 0x7fffffff) / 0x3fffffff - 1);
  const add = (t0: number, dur: number, f: (t: number) => number, gain = 1) => {
    const a = Math.max(0, Math.floor(t0 * SR));
    const b = Math.min(n, Math.floor((t0 + dur) * SR));
    for (let i = a; i < b; i++) out[i] += gain * f(i / SR - t0);
  };

  // ---- instruments ----
  const kick = (t0: number, g = 0.9) => {
    let ph = 0;
    add(t0, 0.35, (t) => { ph += (TAU * (45 + 110 * Math.exp(-t * 30))) / SR; return Math.sin(ph) * Math.exp(-t * 9); }, g);
  };
  const sub = (t0: number, g = 0.5) => add(t0, 0.45, (t) => (Math.sin(TAU * 55 * t) + 0.3 * Math.sin(TAU * 110 * t)) * Math.exp(-t * 6) * Math.min(1, t * 200), g);
  const hat = (t0: number, g = 0.12) => { let lp = 0; add(t0, 0.06, (t) => { const x = noise(); lp += 0.5 * (x - lp); return (x - lp) * Math.exp(-t * 70); }, g); };
  const clap = (t0: number, g = 0.22) => { let lp = 0, hp = 0; add(t0, 0.2, (t) => { const x = noise(); lp += 0.35 * (x - lp); hp += 0.05 * (lp - hp); return (lp - hp) * Math.exp(-t * 22); }, g); };
  const tick = (t0: number, g = 0.05) => add(t0, 0.02, (t) => Math.sin(TAU * 2900 * t) * Math.exp(-t * 300), g);
  const pad = (t0: number, dur: number, notes: number[], g = 0.06) => add(t0, dur + 0.6, (t) => {
    const env = Math.min(1, t / 0.35) * (t > dur ? Math.exp(-(t - dur) * 6) : 1);
    let s = 0;
    for (const m of notes) { const f = midi(m); s += Math.sin(TAU * f * t) + 0.6 * Math.sin(TAU * f * 1.003 * t) + 0.15 * Math.sin(TAU * f * 2 * t); }
    return (s / notes.length) * env;
  }, g);
  const pluck = (t0: number, m: number, g = 0.07) => add(t0, 0.4, (t) => { const f = midi(m); return (Math.sin(TAU * f * t) + 0.35 * Math.sin(TAU * 2 * f * t)) * Math.exp(-t * 11) * Math.min(1, t * 400); }, g);
  const bell = (t0: number, g = 0.12) => add(t0, 2.5, (t) => [72, 76, 79, 84].reduce((s, m, i) => s + Math.sin(TAU * midi(m) * t) * Math.exp(-t * (2 + i)), 0) / 4, g);
  const whoosh = (t0: number, d = 0.35, g = 0.18) => {
    let lp = 0, hp = 0;
    add(t0 - d * 0.6, d, (t) => {
      const x = t / d, fc = 0.02 + 0.25 * Math.sin(Math.PI * x);
      const v = noise(); lp += fc * (v - lp); hp += 0.02 * (lp - hp);
      return (lp - hp) * Math.sin(Math.PI * x) ** 2;
    }, g);
  };
  const riser = (t0: number, d: number) => {
    let lp = 0, ph = 0;
    add(t0, d, (t) => {
      const x = t / d;
      lp += (0.01 + 0.4 * x * x) * (noise() - lp);
      ph += (TAU * (180 + 900 * x * x)) / SR;
      return (lp * 0.7 + 0.25 * Math.sin(ph)) * x * x;
    }, 0.35);
  };
  const impact = (t0: number) => {
    let ph = 0;
    add(t0, 1.8, (t) => { ph += (TAU * (32 + 60 * Math.exp(-t * 6))) / SR; return Math.sin(ph) * Math.exp(-t * 2.2); }, 0.9);
    let lp = 0;
    add(t0, 0.6, (t) => { lp += 0.3 * (noise() - lp); return lp * Math.exp(-t * 8); }, 0.5);
  };

  // ---- pain: dark pulse in A minor ----
  for (let t = 0; t < turn - 0.05; t += 0.5) sub(t, 0.45);
  for (let t = 0.25; t < turn - 1.2; t += 0.5) tick(t);
  pad(0, turn - 0.2, [45, 48, 52], 0.07); // Am
  riser(turn - 1.3, 1.3);
  for (const s of ["junk", "search", "cloud"]) whoosh(sceneAt(s), 0.3, 0.12);

  // ---- reveal ----
  impact(turn);
  const prog = [[60, 64, 67, 71], [55, 59, 62, 67], [57, 60, 64, 67], [53, 57, 60, 64]]; // Cmaj7 G Am7 F
  pad(turn, groove - turn, prog[0], 0.07);

  // ---- features: groove at 120 bpm ----
  for (let i = 0, t = groove; t < outro - 0.05; i++, t += 0.5) {
    kick(t);
    hat(t + 0.25);
    if (i % 2 === 1) clap(t);
    const chord = prog[Math.floor(i / 4) % 4];
    if (i % 4 === 0) pad(t, 2, chord, 0.06);
    pluck(t, chord[(i * 2) % 4] + 12);
    pluck(t + 0.25, chord[(i * 2 + 1) % 4] + 12, 0.05);
  }
  for (const s of ["overview", "post", "offline"]) whoosh(sceneAt(s), 0.28, 0.1);

  // ---- outro ----
  whoosh(outro, 0.35, 0.14);
  kick(outro, 1);
  pad(outro, tl.total - outro - 0.6, [48, 55, 60, 64, 67], 0.08);
  bell(outro + 0.3);

  // soft clip + fade out
  for (let i = 0; i < n; i++) {
    const fade = Math.min(1, (n - i) / (SR * 0.8));
    out[i] = Math.tanh(out[i] * 1.2) * 0.8 * fade;
  }

  const pcm = Buffer.alloc(44 + n * 2);
  pcm.write("RIFF", 0);
  pcm.writeUInt32LE(36 + n * 2, 4);
  pcm.write("WAVEfmt ", 8);
  pcm.writeUInt32LE(16, 16);
  pcm.writeUInt16LE(1, 20);
  pcm.writeUInt16LE(1, 22);
  pcm.writeUInt32LE(SR, 24);
  pcm.writeUInt32LE(SR * 2, 28);
  pcm.writeUInt16LE(2, 32);
  pcm.writeUInt16LE(16, 34);
  pcm.write("data", 36);
  pcm.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) pcm.writeInt16LE(Math.round(Math.max(-1, Math.min(1, out[i])) * 32767), 44 + i * 2);
  writeFileSync(path, pcm);
}
