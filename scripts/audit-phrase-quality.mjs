#!/usr/bin/env node
import fs from "node:fs";
import crypto from "node:crypto";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const args = process.argv.slice(2);
const r2Dir = valueAfter("--r2-dir") || "/tmp/langbang_sentences_v4";
const cleanR2Dir = valueAfter("--write-clean-r2-dir");
const cleanVersion = Number(valueAfter("--clean-version") || "5");
const publicBase = (valueAfter("--public-base") ||
  "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev").replace(/\/+$/, "");
const jsonOut = args.includes("--json");

const sources = [];
loadBundledPhrases();
if (fs.existsSync(r2Dir)) loadR2Bundles(r2Dir);

const findings = [];
for (const source of sources) {
  for (const finding of qualityFindings(source.sentence)) {
    findings.push({ ...source, rule: finding.rule, detail: finding.detail });
  }
}

if (jsonOut) {
  console.log(JSON.stringify({ checked: sources.length, findings }, null, 2));
} else {
  console.log(`Checked ${sources.length} phrase/sentence examples.`);
  if (findings.length === 0) {
    console.log("No phrase quality findings.");
  } else {
    console.log(`${findings.length} phrase quality finding(s):`);
    for (const finding of findings) {
      console.log(
        `- [${finding.rule}] ${finding.source} #${finding.index + 1}: ` +
          `${finding.sentence.en} :: ${finding.sentence.pl}`
      );
      if (finding.detail) console.log(`  ${finding.detail}`);
    }
  }
}

process.exitCode = findings.length > 0 && !cleanR2Dir ? 1 : 0;

if (cleanR2Dir) {
  const cleanSummary = writeCleanR2Tree(r2Dir, cleanR2Dir, cleanVersion, publicBase);
  const message = `Wrote clean R2 v${cleanVersion} tree to ${cleanR2Dir}: ` +
    `${cleanSummary.files} files, ${cleanSummary.removed} sentence(s) removed.`;
  if (jsonOut) {
    console.error(message);
  } else {
    console.log(message);
  }
}

function valueAfter(flag) {
  const index = args.indexOf(flag);
  if (index < 0) return null;
  return args[index + 1] || null;
}

function loadBundledPhrases() {
  const lesson5 = readJsonIfExists("app/src/main/assets/lesson-05.json");
  for (const group of lesson5?.groups || []) {
    (group.sentences || []).forEach((sentence, index) => {
      sources.push({
        source: `lesson-05:${group.id}`,
        index,
        sentence,
      });
    });
  }

  const past = readJsonIfExists("app/src/main/assets/verb-past-sentences-pregen.json");
  if (Array.isArray(past)) {
    past.forEach((sentence, index) => {
      sources.push({ source: "verb-past-sentences-pregen", index, sentence });
    });
  } else if (past && typeof past === "object") {
    for (const [lemma, entries] of Object.entries(past)) {
      if (!Array.isArray(entries)) continue;
      entries.forEach((sentence, index) => {
        sources.push({ source: `verb-past-sentences-pregen:${lemma}`, index, sentence });
      });
    }
  }
}

function loadR2Bundles(root) {
  for (const file of walkJson(root)) {
    const data = readJsonIfExists(file);
    if (!Array.isArray(data)) continue;
    const rel = path.relative(root, file);
    data.forEach((sentence, index) => {
      sources.push({ source: `r2:${rel}`, index, sentence });
    });
  }
}

function writeCleanR2Tree(inputRoot, outputRoot, version, baseUrl) {
  if (!fs.existsSync(inputRoot)) {
    throw new Error(`R2 input dir does not exist: ${inputRoot}`);
  }
  fs.rmSync(outputRoot, { recursive: true, force: true });
  fs.mkdirSync(outputRoot, { recursive: true });
  const entries = {};
  let files = 0;
  let removed = 0;
  for (const file of walkJson(inputRoot)) {
    const rel = path.relative(inputRoot, file);
    const data = readJsonIfExists(file);
    if (!Array.isArray(data)) continue;
    const cleaned = data.filter((sentence) => qualityFindings(sentence).length === 0);
    removed += data.length - cleaned.length;
    const body = `${JSON.stringify(cleaned, null, 2)}\n`;
    const outFile = path.join(outputRoot, rel);
    fs.mkdirSync(path.dirname(outFile), { recursive: true });
    fs.writeFileSync(outFile, body);
    const key = rel.split(path.sep).join("/");
    entries[key] = {
      sha256: crypto.createHash("sha256").update(body).digest("hex"),
      count: cleaned.length,
      bytes: Buffer.byteLength(body),
      url: `${baseUrl}/langbang/sentences/v${version}/${key}`,
    };
    files += 1;
  }
  const manifestBody = `${JSON.stringify({
    promptVersion: version,
    generatedAt: new Date().toISOString(),
    entries,
  }, null, 2)}\n`;
  fs.writeFileSync(path.join(outputRoot, "manifest.json"), manifestBody);
  return { files, removed };
}

function qualityFindings(sentence) {
  const en = normalize(sentence?.en || "");
  const pl = normalize(sentence?.pl || "");
  const findings = [];
  const checks = [
    {
      rule: "awkward-want-adverb",
      regex: /\b(well|quickly|fast|gladly|willingly) (want|wants|wanted)\b/,
      detail: "Manner adverbs with 'want' read like translation artifacts.",
    },
    {
      rule: "contrived-want-animal",
      regex: /\b(want|wants|wanted) (a|the) (good|bad|big|small|new) (cat|dog)\b/,
      detail: "Reject low-frequency textbook combinations like 'wanted a good cat'.",
    },
    {
      rule: "bad-english-modal",
      regex: /\b(can|could|must|should|may|might) to [a-z]+\b/,
      detail: "English modal verbs take a bare infinitive.",
    },
    {
      rule: "bad-feel-like",
      regex: /\bfeel(s|ing|t)? like to [a-z]+\b/,
      detail: "English 'feel like' should take a gerund or noun phrase.",
    },
    {
      rule: "literal-in-home",
      regex: /\b(in home|to home)\b/,
      detail: "Use idiomatic English for the translation, e.g. 'at home' or 'home'.",
    },
  ];
  for (const check of checks) {
    if (check.regex.test(en)) {
      findings.push(check);
    }
  }
  if (/\b(chcę|chce|chcesz|chciałem|chciał|chcieliśmy|chcieli)\b/.test(pl) &&
      /\b(dobrze|szybko|chętnie)\b/.test(pl)) {
    findings.push({
      rule: "awkward-polish-want-adverb",
      detail: "Avoid dobrze/szybko/chętnie as direct adverbs of chcieć.",
    });
  }
  if (/\b(chcę|chciałem)\b/.test(pl) &&
      /\b(kota|psa)\b/.test(pl) &&
      /\b(dobrego|złego|dużego|małego|nowego)\b/.test(pl)) {
    findings.push({
      rule: "contrived-polish-want-animal",
      detail: "Reject chcieć + adjective + common pet as a default generated phrase.",
    });
  }
  return findings;
}

function normalize(text) {
  return String(text)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function readJsonIfExists(file) {
  const absolute = path.isAbsolute(file) ? file : path.join(repoRoot, file);
  if (!fs.existsSync(absolute)) return null;
  return JSON.parse(fs.readFileSync(absolute, "utf8"));
}

function walkJson(root) {
  const out = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) out.push(...walkJson(full));
    else if (entry.isFile() && entry.name.endsWith(".json")) out.push(full);
  }
  return out;
}
