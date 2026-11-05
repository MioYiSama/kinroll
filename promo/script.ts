// Narration and on-screen copy. Order = playback order.
// `gapBefore` is silence (seconds) inserted before the line starts.
export type Line = {
  id: string;
  scene: string;
  voice: string; // spoken text
  caption: string; // subtitle text
  gapBefore: number;
};

export const LINES: Line[] = [
  { id: "p1", scene: "full", gapBefore: 0.35, voice: "手机相册，又又又满了！", caption: "手机相册，又又又满了！" },
  { id: "p2", scene: "junk", gapBefore: 0.15, voice: "截图、重复、拍糊的废片，删都删不完。", caption: "截图、重复、拍糊的废片，删都删不完" },
  { id: "p3", scene: "search", gapBefore: 0.15, voice: "想找张发票，翻到手指发酸。", caption: "想找张发票，翻到手指发酸" },
  { id: "p4", scene: "cloud", gapBefore: 0.15, voice: "交给云端 AI？隐私谁来管？", caption: "交给云端 AI？隐私谁来管？" },
  { id: "f0", scene: "logo", gapBefore: 0.55, voice: "相知，完全离线的 AI 相册。", caption: "相知，完全离线的 AI 相册" },
  { id: "f1", scene: "cleanup", gapBefore: 0.25, voice: "废片、截图、重复照片，一键清理。", caption: "废片、截图、重复照片，一键清理" },
  { id: "f2", scene: "overview", gapBefore: 0.2, voice: "一句话搜图，猫、发票，秒找到。", caption: "一句话搜图，猫、发票，秒找到" },
  { id: "f3", scene: "post", gapBefore: 0.2, voice: "选几张照片，帖文自动写好。", caption: "选几张照片，帖文自动写好" },
  { id: "f4", scene: "offline", gapBefore: 0.2, voice: "模型全在手机上跑，照片不出手机。", caption: "模型全在手机上跑，照片不出手机" },
  { id: "end", scene: "end", gapBefore: 0.45, voice: "相知，懂你的每一张照片。", caption: "" },
];

export const TTS = {
  model: "google/gemini-3.8-flash-tts",
  voice: "Puck",
  instructions: "中文广告旁白：自信、明亮、有冲击力，语速偏快，干脆利落，句尾不拖音",
  sampleRate: 24000,
};

export const TAIL = 1.6; // seconds held after the last line
