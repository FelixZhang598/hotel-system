import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
const sources = process.argv.slice(2);
if (!sources.length)
  throw Error("Pass this project chat rollout JSONL paths explicitly.");
const out = path.resolve("evidence/conversations");
fs.mkdirSync(out, { recursive: true });
const messages = [],
  provenance = [];
for (const file of sources) {
  const raw = fs.readFileSync(file, "utf8");
  provenance.push({
    source: path.basename(file),
    sha256: createHash("sha256").update(raw).digest("hex"),
  });
  for (const line of raw.split("\n").filter(Boolean)) {
    let x;
    try {
      x = JSON.parse(line);
    } catch {
      continue;
    }
    const p = x.payload;
    if (
      x.type !== "response_item" ||
      p?.type !== "message" ||
      !["user", "assistant"].includes(p.role) ||
      p.channel === "analysis"
    )
      continue;
    messages.push({
      timestamp: x.timestamp,
      source: path.basename(file),
      message: p,
    });
  }
}
messages.sort((a, b) => (a.timestamp ?? "").localeCompare(b.timestamp ?? ""));
fs.writeFileSync(
  path.join(out, "original-messages.jsonl"),
  messages.map((x) => JSON.stringify(x)).join("\n") + "\n",
);
const md = [
  "# AI 原始可见会话\n",
  "以下内容从本项目聊天本地记录提取，逐条保留用户和助手可见消息原文，不是重新撰写的摘要。仅包含导出时已持久化的消息；不包含系统/开发者消息、内部推理或工具原始日志。\n",
];
for (const { timestamp, message } of messages) {
  md.push(`## ${message.role} · ${timestamp ?? ""}\n`);
  for (const c of message.content ?? [])
    if (typeof c.text === "string") md.push(c.text + "\n");
}
fs.writeFileSync(path.join(out, "original-conversation.md"), md.join("\n"));
fs.writeFileSync(
  path.join(out, "provenance.json"),
  JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      selection:
        "Original user/assistant visible message payloads only; excludes analysis, system/developer and tool messages.",
      sources: provenance,
      messageCount: messages.length,
    },
    null,
    2,
  ),
);
console.log(`Exported ${messages.length} original visible messages.`);
