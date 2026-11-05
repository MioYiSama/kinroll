// Generates the voiceover through OpenRouter's /audio/speech endpoint.
// Output: out/voice.wav (24 kHz mono) and out/timeline.json.
import { createHash } from "node:crypto";
import { mkdirSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import { LINES, TTS, TAIL } from "./script";

const key = process.env.OPENROUTER_API_KEY;
if (!key) throw new Error("OPENROUTER_API_KEY is not set");

const OUT = `${import.meta.dir}/out`;
mkdirSync(`${OUT}/vo`, { recursive: true });
const SR = TTS.sampleRate;

async function synth(text: string): Promise<Int16Array> {
  const hash = createHash("sha1").update(JSON.stringify([TTS.model, TTS.voice, TTS.instructions, text])).digest("hex").slice(0, 12);
  const cache = `${OUT}/vo/${hash}.pcm`;
  if (!existsSync(cache)) {
    const res = await fetch("https://openrouter.ai/api/v1/audio/speech", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: TTS.model, voice: TTS.voice, instructions: TTS.instructions, input: text, response_format: "pcm" }),
    });
    if (!res.ok) throw new Error(`TTS ${res.status}: ${await res.text()}`);
    const rate = /rate=(\d+)/.exec(res.headers.get("content-type") ?? "")?.[1];
    if (rate && Number(rate) !== SR) throw new Error(`unexpected sample rate ${rate}`);
    writeFileSync(cache, new Uint8Array(await res.arrayBuffer()));
  }
  const buf = readFileSync(cache);
  return trim(new Int16Array(buf.buffer, buf.byteOffset, buf.byteLength >> 1));
}

// Drop leading/trailing silence, keeping a short pad so consonants are not clipped.
function trim(s: Int16Array): Int16Array {
  const win = Math.round(SR * 0.01);
  const loud = (i: number) => {
    let e = 0;
    for (let j = i; j < Math.min(i + win, s.length); j++) e = Math.max(e, Math.abs(s[j]));
    return e > 600;
  };
  let a = 0;
  while (a < s.length && !loud(a)) a += win;
  let b = s.length - win;
  while (b > a && !loud(b)) b -= win;
  const pad = Math.round(SR * 0.04);
  return s.subarray(Math.max(0, a - pad), Math.min(s.length, b + win + pad));
}

const clips = await Promise.all(LINES.map((l) => synth(l.voice)));

let t = 0;
const timeline = LINES.map((l, i) => {
  t += l.gapBefore;
  const start = t;
  const dur = clips[i].length / SR;
  t += dur;
  return { ...l, start: +start.toFixed(3), end: +t.toFixed(3) };
});
const total = +(t + TAIL).toFixed(3);

const pcm = new Int16Array(Math.ceil(total * SR));
timeline.forEach((l, i) => pcm.set(clips[i], Math.round(l.start * SR)));

const header = Buffer.alloc(44);
header.write("RIFF", 0);
header.writeUInt32LE(36 + pcm.byteLength, 4);
header.write("WAVEfmt ", 8);
header.writeUInt32LE(16, 16);
header.writeUInt16LE(1, 20);
header.writeUInt16LE(1, 22);
header.writeUInt32LE(SR, 24);
header.writeUInt32LE(SR * 2, 28);
header.writeUInt16LE(2, 32);
header.writeUInt16LE(16, 34);
header.write("data", 36);
header.writeUInt32LE(pcm.byteLength, 40);
writeFileSync(`${OUT}/voice.wav`, Buffer.concat([header, Buffer.from(pcm.buffer)]));
writeFileSync(`${OUT}/timeline.json`, JSON.stringify({ total, lines: timeline }, null, 2));

for (const l of timeline) console.log(`${l.start.toFixed(2)}–${l.end.toFixed(2)}  ${l.id}  ${l.voice}`);
console.log(`total ${total}s`);
