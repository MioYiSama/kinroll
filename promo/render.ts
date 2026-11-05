// Renders index.html frame by frame in headless Chrome and encodes the promo.
//   bun render.ts                 -> ../dist/kinroll-promo.mp4
//   bun render.ts --still 3.2 8   -> out/still-3.2.png, out/still-8.png (preview frames)
// Both modes also write ../dist/kinroll-promo-cover.jpg from cover.html.
import puppeteer from "puppeteer-core";
import { readFileSync, mkdirSync } from "node:fs";
import { buildMusic } from "./music";

const DIR = import.meta.dir;
const OUT = `${DIR}/out`;
const DIST = `${DIR}/../dist`;
const FPS = 30;
const CHROME = process.env.CHROME_PATH ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

// Globals defined by index.html; the page is ours, so the cast documents that contract.
type PromoWindow = Window & { ready: Promise<unknown>; setTimeline(tl: unknown): void; seek(t: number): void };

const tl = JSON.parse(readFileSync(`${OUT}/timeline.json`, "utf8"));
const args = process.argv.slice(2);
const stills = args[0] === "--still" ? args.slice(1).map(Number) : null;

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ["--allow-file-access-from-files", "--force-color-profile=srgb", "--hide-scrollbars"] });
const page = await browser.newPage();

// Cover images for the video platforms; cover.html picks its layout from the viewport.
mkdirSync(DIST, { recursive: true });
for (const [name, width, height] of [["9x16", 1080, 1920], ["16x9", 1920, 1080], ["4x3", 1440, 1080]] as const) {
  await page.setViewport({ width, height, deviceScaleFactor: 1 });
  await page.goto(`file://${DIR}/cover.html`, { waitUntil: "load" });
  await page.evaluate(() => Promise.all([document.fonts.ready, ...[...document.images].map((i) => i.decode())]));
  const path = `${DIST}/kinroll-promo-cover-${name}.jpg`;
  await page.screenshot({ path, type: "jpeg", quality: 95 });
  console.log(path);
}
await page.setViewport({ width: 1080, height: 1920, deviceScaleFactor: 1 });

await page.goto(`file://${DIR}/index.html`, { waitUntil: "load" });
await page.evaluate(async (tl) => {
  await (window as unknown as PromoWindow).ready;
  (window as unknown as PromoWindow).setTimeline(tl);
}, tl);
const cdp = await page.createCDPSession();

const frame = async (t: number, format: "jpeg" | "png") => {
  await page.evaluate((t) => (window as unknown as PromoWindow).seek(t), t);
  const { data } = await cdp.send("Page.captureScreenshot", { format, quality: format === "jpeg" ? 95 : undefined, optimizeForSpeed: true });
  return Buffer.from(data, "base64");
};

if (stills) {
  for (const t of stills) await Bun.write(`${OUT}/still-${t}.png`, await frame(t, "png"));
  console.log(stills.map((t) => `${OUT}/still-${t}.png`).join("\n"));
} else {
  buildMusic(tl, `${OUT}/music.wav`);
  const target = `${DIST}/kinroll-promo.mp4`;
  const ff = Bun.spawn(
    [
      "ffmpeg", "-loglevel", "error", "-y",
      "-f", "image2pipe", "-c:v", "mjpeg", "-framerate", String(FPS), "-i", "-",
      "-i", `${OUT}/voice.wav`, "-i", `${OUT}/music.wav`,
      "-filter_complex",
      "[1:a]aresample=48000,asplit=2[vo][sc];" +
        "[2:a]aresample=48000,volume=0.5[mu];" +
        "[mu][sc]sidechaincompress=threshold=0.04:ratio=5:attack=15:release=350[duck];" +
        "[vo][duck]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[a]",
      "-map", "0:v", "-map", "[a]",
      "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-profile:v", "high",
      "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
      "-movflags", "+faststart", "-t", String(tl.total), target,
    ],
    { stdin: "pipe", stdout: "inherit", stderr: "inherit" },
  );
  const frames = Math.round(tl.total * FPS);
  for (let i = 0; i < frames; i++) {
    ff.stdin.write(await frame(i / FPS, "jpeg"));
    if (i % 60 === 0) process.stdout.write(`\rframe ${i}/${frames}`);
  }
  ff.stdin.end();
  if ((await ff.exited) !== 0) throw new Error("ffmpeg failed");
  console.log(`\n${target}`);
}
await browser.close();
