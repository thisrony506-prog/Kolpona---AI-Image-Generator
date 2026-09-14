const { randomUUID } = require("crypto");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");

setGlobalOptions({ region: "us-central1", maxInstances: 8 });

if (admin.apps.length === 0) {
  admin.initializeApp({
    storageBucket: "kolpona-ai.firebasestorage.app",
  });
}

const huggingfaceToken = defineSecret("HUGGINGFACE_TOKEN");

const ROUTER = "https://router.huggingface.co";
const USER_AGENT = "Kolpona-Functions/1.14.4";
const HUB_MODELS = [
  "Wan-AI/Wan2.2-TI2V-5B",
  "tencent/HunyuanVideo",
  "Wan-AI/Wan2.1-T2V-1.3B",
];
const FALLBACK_FAL = [
  `${ROUTER}/fal-ai/fal-ai/wan/v2.2-5b/text-to-video?_subdomain=queue`,
  `${ROUTER}/fal-ai/fal-ai/hunyuan-video?_subdomain=queue`,
  `${ROUTER}/fal-ai/fal-ai/wan/v2.1/1.3b/text-to-video?_subdomain=queue`,
];
const NEGATIVE =
  "blurry, distorted, flicker, jitter, morphing, extra limbs, watermark, text, low quality, unnatural motion";

exports.generateVideo = onRequest(
  {
    cors: true,
    timeoutSeconds: 540,
    memory: "1GiB",
    secrets: [huggingfaceToken],
    invoker: "public",
  },
  async (req, res) => {
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST") {
      jsonError(res, 405, "SERVER", "Use POST.");
      return;
    }

    try {
      const uid = await requireUser(req);
      const body = req.body && typeof req.body === "object" ? req.body : {};
      const prompt = enhancePrompt(body.prompt);
      if (!prompt) {
        jsonError(res, 400, "INVALID_PROMPT", "Describe what you want to create.");
        return;
      }
      const aspect = normalizeAspect(body.aspectRatio, body.width, body.height);
      const token = huggingfaceToken.value();
      if (!token) {
        console.error("video_missing_secret");
        jsonError(res, 500, "SERVER", "Video studio is not configured.");
        return;
      }

      const endpoints = await resolveEndpoints();
      let last = null;
      for (const url of endpoints) {
        try {
          const result = await generateAt(url, token, prompt, aspect);
          if (!looksLikeVideo(result.bytes) || result.bytes.length < 4000) {
            last = { code: "VIDEO_UNAVAILABLE", status: 502 };
            continue;
          }
          let videoUrl = result.sourceUrl || "";
          try {
            videoUrl = await storeVideo(uid, result.bytes);
          } catch (storeErr) {
            console.error("video_store_fail", safeError(storeErr));
            if (!String(videoUrl).startsWith("http")) {
              throw Object.assign(new Error("Couldn't save the video."), {
                code: "SERVER",
                status: 500,
              });
            }
          }
          res.status(200).json({
            ok: true,
            videoUrl,
            durationSeconds: 5,
            aspectRatio: aspect,
            model: modelName(url),
            provider: providerName(url),
          });
          return;
        } catch (err) {
          last = err;
          console.error("video_provider_fail", safeError(err));
          if (err && err.code === "INVALID_TOKEN") {
            break;
          }
        }
      }

      const fail = last || { code: "VIDEO_UNAVAILABLE", status: 502 };
      jsonError(
        res,
        fail.status || 502,
        fail.code || "VIDEO_UNAVAILABLE",
        fail.message || "Couldn't create the video. Try again."
      );
    } catch (err) {
      console.error("video_unhandled", safeError(err));
      const code = err && err.code ? err.code : "SERVER";
      jsonError(res, err && err.status ? err.status : 500, code, err && err.message);
    }
  }
);

async function requireUser(req) {
  const header = String(req.get("authorization") || req.get("Authorization") || "");
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    throw Object.assign(new Error("Sign in to create a video."), {
      code: "PERMISSION_DENIED",
      status: 401,
    });
  }
  try {
    const decoded = await admin.auth().verifyIdToken(match[1]);
    if (!decoded || !decoded.uid) {
      throw new Error("no uid");
    }
    return decoded.uid;
  } catch (err) {
    if (err && err.code === "PERMISSION_DENIED") throw err;
    throw Object.assign(new Error("Sign in to create a video."), {
      code: "PERMISSION_DENIED",
      status: 401,
    });
  }
}

function enhancePrompt(raw) {
  const cleaned = String(raw || "")
    .trim()
    .replace(/\s+/g, " ");
  if (cleaned.length < 3) return "";
  if (cleaned.length > 2000) {
    throw Object.assign(new Error("Keep the prompt under 2000 characters."), {
      code: "INVALID_PROMPT",
      status: 400,
    });
  }
  const rich =
    cleaned.length > 240 ||
    /cinematic|photoreal|24fps|camera movement|dolly|tracking/i.test(cleaned);
  if (rich) return cleaned.slice(0, 1400);
  return [
    cleaned,
    "cinematic 5 second video, 24fps, smooth natural camera movement",
    "realistic lighting, sharp focus, natural motion, high detail, no flicker",
  ]
    .join(", ")
    .slice(0, 1400);
}

function normalizeAspect(aspectRatio, width, height) {
  const raw = String(aspectRatio || "").trim();
  if (raw === "9:16" || raw === "16:9" || raw === "1:1") return raw;
  const w = Number(width) || 0;
  const h = Number(height) || 0;
  if (w > 0 && h > 0) {
    if (w === h) return "1:1";
    return w > h ? "16:9" : "9:16";
  }
  return "16:9";
}

async function resolveEndpoints() {
  const urls = [];
  for (const model of HUB_MODELS) {
    try {
      const response = await fetch(
        `https://huggingface.co/api/models/${model}?expand[]=inferenceProviderMapping`,
        { headers: { "User-Agent": USER_AGENT } }
      );
      if (!response.ok) continue;
      const data = await response.json();
      const mapping = data && data.inferenceProviderMapping;
      if (!mapping || typeof mapping !== "object") continue;
      const fal = mapping["fal-ai"];
      if (
        fal &&
        fal.status === "live" &&
        fal.task === "text-to-video" &&
        typeof fal.providerId === "string" &&
        fal.providerId
      ) {
        urls.push(`${ROUTER}/fal-ai/${fal.providerId}?_subdomain=queue`);
      }
      const replicate = mapping.replicate;
      if (
        replicate &&
        replicate.status === "live" &&
        replicate.task === "text-to-video" &&
        typeof replicate.providerId === "string" &&
        replicate.providerId
      ) {
        urls.push(`${ROUTER}/replicate/v1/models/${replicate.providerId}/predictions`);
      }
      const wavespeed = mapping.wavespeed;
      if (
        wavespeed &&
        wavespeed.status === "live" &&
        wavespeed.task === "text-to-video" &&
        typeof wavespeed.providerId === "string" &&
        wavespeed.providerId
      ) {
        urls.push(`${ROUTER}/wavespeed/api/v3/${wavespeed.providerId}`);
      }
    } catch (err) {
      console.error("video_mapping_fail", safeError(err));
    }
  }
  return unique(urls.concat(FALLBACK_FAL));
}

async function generateAt(url, token, prompt, aspect) {
  if (url.includes("/replicate/")) {
    return generateReplicate(url, token, prompt);
  }
  if (url.includes("/wavespeed/")) {
    return generateWavespeed(url, token, prompt);
  }
  return generateFal(url, token, prompt, aspect);
}

async function generateFal(url, token, prompt, aspect) {
  const bodies = [
    {
      prompt,
      aspect_ratio: aspect,
      resolution: "720p",
      enable_prompt_expansion: false,
      num_frames: 121,
      frames_per_second: 24,
      negative_prompt: NEGATIVE,
    },
    {
      prompt,
      aspect_ratio: aspect,
      resolution: "720p",
      enable_prompt_expansion: false,
    },
    { prompt },
  ];
  let last = null;
  for (const body of bodies) {
    try {
      return await submitFal(url, token, body);
    } catch (err) {
      last = err;
      if (err && err.code !== "INVALID_PROMPT") throw err;
    }
  }
  throw last || Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
}

async function submitFal(url, token, body) {
  const response = await hfFetch(url, {
    method: "POST",
    token,
    headers: { Accept: "application/json,video/mp4" },
    body: JSON.stringify(body),
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  if (looksLikeVideo(bytes)) return { bytes, sourceUrl: "" };
  const text = bytes.toString("utf8");
  const json = parseJson(text);
  if (!json || !json.request_id || !String(json.response_url || "").startsWith("http")) {
    throw mapHttp(response.status, text);
  }
  return pollFal(url, json, token);
}

async function pollFal(submitUrl, queue, token) {
  const submit = new URL(submitUrl);
  const path = new URL(queue.response_url).pathname;
  const query = submit.search || "";
  const base =
    submit.host === "router.huggingface.co"
      ? `${submit.protocol}//${submit.host}/fal-ai`
      : `${submit.protocol}//${submit.host}`;
  const resultUrl = `${base}${path}${query}`;
  const statusUrl = `${resultUrl.replace(/\?.*$/, "").replace(/\/$/, "")}/status${query}`;
  for (let i = 0; i < 180; i += 1) {
    if (i > 0) await sleep(2000);
    const statusRes = await hfFetch(statusUrl, { method: "GET", token, headers: { Accept: "application/json" } });
    if (statusRes.status === 401 || statusRes.status === 403) {
      throw mapHttp(statusRes.status, await statusRes.text());
    }
    if (!statusRes.ok) continue;
    const statusJson = parseJson(await statusRes.text()) || {};
    const status = String(statusJson.status || "");
    if (/failed|error|cancelled/i.test(status)) {
      throw Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
    }
    if (!/completed|complete|ok/i.test(status)) continue;
    const resultRes = await hfFetch(resultUrl, { method: "GET", token, headers: { Accept: "application/json" } });
    const resultText = await resultRes.text();
    const mediaUrl = extractVideoUrl(resultText);
    if (!mediaUrl) {
      throw Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
    }
    const bytes = await downloadVideo(mediaUrl);
    return { bytes, sourceUrl: mediaUrl };
  }
  throw Object.assign(new Error("The video took too long. Try again."), { code: "TIMEOUT", status: 504 });
}

async function generateReplicate(url, token, prompt) {
  const response = await hfFetch(url, {
    method: "POST",
    token,
    headers: { Accept: "application/json", Prefer: "wait" },
    body: JSON.stringify({ input: { prompt } }),
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  if (looksLikeVideo(bytes)) return { bytes, sourceUrl: "" };
  const text = bytes.toString("utf8");
  const json = parseJson(text);
  const status = String((json && json.status) || "").toLowerCase();
  const getUrl = json && json.urls && typeof json.urls.get === "string" ? json.urls.get : "";
  if (getUrl && (status === "starting" || status === "processing" || status === "queued")) {
    return pollReplicate(getUrl, token);
  }
  const output = json && json.output;
  const mediaUrl =
    typeof output === "string" && output.startsWith("http")
      ? output
      : Array.isArray(output) && typeof output[0] === "string"
        ? output[0]
        : extractVideoUrl(text);
  if (!mediaUrl) throw mapHttp(response.status, text);
  const video = await downloadVideo(mediaUrl);
  return { bytes: video, sourceUrl: mediaUrl };
}

async function pollReplicate(getUrl, token) {
  for (let i = 0; i < 180; i += 1) {
    if (i > 0) await sleep(2000);
    const response = await hfFetch(getUrl, { method: "GET", token, headers: { Accept: "application/json" } });
    const json = parseJson(await response.text()) || {};
    const status = String(json.status || "").toLowerCase();
    if (status === "failed" || status === "canceled" || status === "cancelled") {
      throw Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
    }
    if (status !== "succeeded" && status !== "complete" && status !== "completed") continue;
    const output = json.output;
    const mediaUrl =
      typeof output === "string" && output.startsWith("http")
        ? output
        : Array.isArray(output) && typeof output[0] === "string"
          ? output[0]
          : extractVideoUrl(JSON.stringify(json));
    if (!mediaUrl) {
      throw Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
    }
    const bytes = await downloadVideo(mediaUrl);
    return { bytes, sourceUrl: mediaUrl };
  }
  throw Object.assign(new Error("The video took too long. Try again."), { code: "TIMEOUT", status: 504 });
}

async function generateWavespeed(url, token, prompt) {
  const response = await hfFetch(url, {
    method: "POST",
    token,
    headers: { Accept: "application/json" },
    body: JSON.stringify({ prompt }),
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  if (looksLikeVideo(bytes)) return { bytes, sourceUrl: "" };
  const text = bytes.toString("utf8");
  const json = parseJson(text) || {};
  const data = json.data && typeof json.data === "object" ? json.data : json;
  const outputs = data.outputs;
  const ready =
    Array.isArray(outputs) && typeof outputs[0] === "string" && outputs[0].startsWith("http")
      ? outputs[0]
      : extractVideoUrl(text);
  if (ready) {
    const video = await downloadVideo(ready);
    return { bytes: video, sourceUrl: ready };
  }
  const getUrl = data.urls && typeof data.urls.get === "string" ? data.urls.get : "";
  if (!getUrl) throw mapHttp(response.status, text);
  return pollWavespeed(getUrl, token);
}

async function pollWavespeed(getUrl, token) {
  for (let i = 0; i < 180; i += 1) {
    if (i > 0) await sleep(2000);
    const response = await hfFetch(getUrl, { method: "GET", token, headers: { Accept: "application/json" } });
    const json = parseJson(await response.text()) || {};
    const data = json.data && typeof json.data === "object" ? json.data : json;
    const status = String(data.status || json.status || "").toLowerCase();
    if (status === "failed" || status === "error") {
      throw Object.assign(new Error("Couldn't create the video."), { code: "VIDEO_UNAVAILABLE", status: 502 });
    }
    const outputs = data.outputs;
    const mediaUrl =
      Array.isArray(outputs) && typeof outputs[0] === "string" && outputs[0].startsWith("http")
        ? outputs[0]
        : extractVideoUrl(JSON.stringify(json));
    if (!mediaUrl) continue;
    const bytes = await downloadVideo(mediaUrl);
    return { bytes, sourceUrl: mediaUrl };
  }
  throw Object.assign(new Error("The video took too long. Try again."), { code: "TIMEOUT", status: 504 });
}

async function hfFetch(url, { method, token, headers, body }) {
  let response;
  try {
    response = await fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "User-Agent": USER_AGENT,
        ...(headers || {}),
      },
      body,
    });
  } catch (err) {
    const timedOut = /timeout|abort/i.test(String(err && err.message));
    throw Object.assign(new Error(timedOut ? "The video took too long. Try again." : "Check your connection and try again."), {
      code: timedOut ? "TIMEOUT" : "NETWORK",
      status: timedOut ? 504 : 503,
    });
  }
  if (response.ok) return response;
  const text = await response.text();
  throw mapHttp(response.status, text);
}

function mapHttp(status, text) {
  const lower = String(text || "").toLowerCase();
  let code = "SERVER";
  let message = "Couldn't create the video. Try again.";
  if (status === 401 || /invalid.*token|unauthorized/i.test(lower)) {
    code = "INVALID_TOKEN";
    message = "Video studio authentication failed. Try again later.";
  } else if (status === 403 || /permission|forbidden/i.test(lower)) {
    code = "PERMISSION_DENIED";
    message = "This account cannot use video generation right now.";
  } else if (status === 404 || /not found|no such model/i.test(lower)) {
    code = "MODEL_UNAVAILABLE";
    message = "That video model is unavailable. Try again shortly.";
  } else if (status === 429) {
    code = "RATE_LIMIT";
    message = "Too many video requests. Wait a moment and try again.";
  } else if (status === 400 || status === 422) {
    code = "INVALID_PROMPT";
    message = "Try a clearer description of the scene you want.";
  } else if (status === 503 || status === 529 || /overloaded|unavailable/i.test(lower)) {
    code = "PROVIDER_UNAVAILABLE";
    message = "The video studio is busy. Try again in a moment.";
  } else if (status >= 500) {
    code = "SERVER";
    message = "The video studio had a problem. Try again.";
  }
  console.error("video_http", { status, code });
  return Object.assign(new Error(message), { code, status: status || 502 });
}

async function downloadVideo(url) {
  const response = await fetch(url, {
    headers: { "User-Agent": USER_AGENT, Accept: "video/mp4,*/*" },
  });
  if (!response.ok) {
    throw Object.assign(new Error("Couldn't download the video."), {
      code: "VIDEO_UNAVAILABLE",
      status: 502,
    });
  }
  const bytes = Buffer.from(await response.arrayBuffer());
  if (!looksLikeVideo(bytes)) {
    throw Object.assign(new Error("Couldn't create the video."), {
      code: "VIDEO_UNAVAILABLE",
      status: 502,
    });
  }
  return bytes;
}

async function storeVideo(uid, bytes) {
  const { randomUUID } = require("crypto");
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const path = `users/${uid}/videos/${id}.mp4`;
  const file = admin.storage().bucket().file(path);
  const token = randomUUID();
  await file.save(bytes, {
    contentType: "video/mp4",
    resumable: false,
    metadata: {
      cacheControl: "private, max-age=3600",
      metadata: { firebaseStorageDownloadTokens: token },
    },
  });
  const bucket = file.bucket.name;
  return `https://firebasestorage.googleapis.com/v0/b/${bucket}/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
}

function extractVideoUrl(text) {
  const json = parseJson(text);
  if (json) {
    const nested = pickUrl(json);
    if (nested) return nested;
  }
  const match = String(text || "").match(/https?:\/\/[^\s"'<>\\]+/g) || [];
  return match.map((item) => item.replace(/[),.;]+$/, "")).find(isMediaUrl) || "";
}

function pickUrl(value) {
  if (!value || typeof value !== "object") return "";
  if (typeof value.url === "string" && value.url.startsWith("http") && isMediaUrl(value.url)) {
    return value.url;
  }
  if (typeof value.video === "string" && value.video.startsWith("http") && isMediaUrl(value.video)) {
    return value.video;
  }
  if (value.video && typeof value.video === "object") {
    const child = pickUrl(value.video);
    if (child) return child;
  }
  if (value.result && typeof value.result === "object") {
    const child = pickUrl(value.result);
    if (child) return child;
  }
  if (value.data && typeof value.data === "object") {
    const child = pickUrl(value.data);
    if (child) return child;
  }
  if (Array.isArray(value.output) && typeof value.output[0] === "string" && isMediaUrl(value.output[0])) {
    return value.output[0];
  }
  if (typeof value.output === "string" && isMediaUrl(value.output)) return value.output;
  return "";
}

function isMediaUrl(url) {
  const lower = String(url || "").toLowerCase();
  if (!lower.startsWith("http")) return false;
  if (lower.includes("/status") || lower.includes("/cancel")) return false;
  if (lower.includes("queue.fal.run") && !lower.includes(".mp4")) return false;
  return (
    lower.includes(".mp4") ||
    lower.includes(".webm") ||
    lower.includes("fal.media") ||
    lower.includes("cdn.fal.ai") ||
    lower.includes("falserverless") ||
    lower.includes("replicate.delivery") ||
    lower.includes("googleapis.com")
  );
}

function looksLikeVideo(bytes) {
  if (!bytes || bytes.length < 12) return false;
  const limit = Math.min(512, bytes.length - 4);
  for (let i = 0; i < limit; i += 1) {
    if (bytes[i] === 0x66 && bytes[i + 1] === 0x74 && bytes[i + 2] === 0x79 && bytes[i + 3] === 0x70) {
      return true;
    }
  }
  return bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf;
}

function parseJson(text) {
  try {
    return JSON.parse(String(text || "").trim());
  } catch (_) {
    return null;
  }
}

function jsonError(res, status, code, message) {
  res.status(status).json({
    ok: false,
    error: code,
    message: message || "Couldn't create the video. Try again.",
  });
}

function safeError(err) {
  if (!err) return { message: "unknown" };
  return {
    code: err.code || "",
    status: err.status || 0,
    message: String(err.message || "").slice(0, 180),
  };
}

function unique(items) {
  return [...new Set(items.filter(Boolean))];
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
