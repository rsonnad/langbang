#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const DEFAULT_R2_BASE = "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev";

const args = parseArgs(process.argv.slice(2));
const r2Base = args.r2Base || process.env.LANGBANGML_R2_BASE || DEFAULT_R2_BASE;
const r2Version = Number(args.r2Version || 4);
const outDir = path.resolve(args.out || `/private/tmp/langbangml-r2-sentence-v${r2Version}-repair`);
const publicPrefix = `${r2Base.replace(/\/+$/, "")}/langbang/sentences/v${r2Version}`;

main().catch((error) => {
  console.error(error?.stack || error);
  process.exit(1);
});

async function main() {
  const manifestUrl = `${publicPrefix}/manifest.json`;
  const manifest = await fetchJson(manifestUrl);
  const entries = Object.entries(manifest.entries || {});
  let changedBundles = 0;
  let changedSentences = 0;
  let changedTokens = 0;
  let overriddenSentences = 0;

  for (const [key, entry] of entries) {
    const url = args.trustManifestUrls === "true" && entry.url
      ? entry.url
      : `${publicPrefix}/${key}`;
    const originalText = await fetchText(url);
    const sentences = JSON.parse(originalText);
    const repaired = repairSentences(sentences);
    const overridden = applyCommonPhraseOverrides(key, repaired.sentences);
    const nextText = `${JSON.stringify(repaired.sentences, null, 2)}\n`;

    manifest.entries[key] = {
      ...entry,
      url: `${publicPrefix}/${key}`,
    };

    if (repaired.changedSentences > 0 || overridden.changedSentences > 0) {
      changedBundles += 1;
      changedSentences += repaired.changedSentences;
      changedTokens += repaired.changedTokens;
      overriddenSentences += overridden.changedSentences;
      const outPath = path.join(outDir, "langbang", "sentences", `v${r2Version}`, key);
      fs.mkdirSync(path.dirname(outPath), { recursive: true });
      fs.writeFileSync(outPath, nextText);
      manifest.entries[key].sha256 = sha256(nextText);
      manifest.entries[key].bytes = Buffer.byteLength(nextText);
      manifest.entries[key].count = repaired.sentences.length;
    }
  }

  manifest.generatedAt = new Date().toISOString();
  const manifestText = `${JSON.stringify(manifest, null, 2)}\n`;
  const manifestOut = path.join(outDir, "langbang", "sentences", `v${r2Version}`, "manifest.json");
  fs.mkdirSync(path.dirname(manifestOut), { recursive: true });
  fs.writeFileSync(manifestOut, manifestText);

  const summary = {
    outDir,
    r2Version,
    totalBundles: entries.length,
    changedBundles,
    changedSentences,
    changedTokens,
    overriddenSentences,
    manifestPath: manifestOut,
    uploadPrefix: `langbang/sentences/v${r2Version}`,
  };
  fs.writeFileSync(path.join(outDir, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
  console.log(JSON.stringify(summary, null, 2));
}

function applyCommonPhraseOverrides(key, sentences) {
  const overrides = COMMON_PHRASE_OVERRIDES[key];
  if (!overrides) return { changedSentences: 0 };
  let changedSentences = 0;
  for (const [indexText, override] of Object.entries(overrides)) {
    const index = Number(indexText);
    if (!sentences[index]) {
      throw new Error(`Override missing sentence ${key}#${index}`);
    }
    if (override.expectedEn && sentences[index].en !== override.expectedEn) {
      throw new Error(`Override mismatch ${key}#${index}: ${sentences[index].en}`);
    }
    sentences[index] = override.sentence;
    changedSentences += 1;
  }
  return { changedSentences };
}

const COMMON_PHRASE_OVERRIDES = {
  "adjectives/atrakcyjny.json": {
    38: override("Y'all are listening about an attractive city.", sentence(
      "To jest atrakcyjne miasto.",
      "This is an attractive city.",
      "This is attractive city.",
      [["To", "This"], ["jest", "is"], ["atrakcyjne", "attractive"], ["miasto", "city"]]
    )),
  },
  "adjectives/brzydki.json": {
    38: override("I am listening about an ugly cat.", sentence(
      "Widzę brzydki sweter.",
      "I see an ugly sweater.",
      "I-see ugly sweater.",
      [["Widzę", "I-see"], ["brzydki", "ugly"], ["sweter", "sweater"]]
    )),
  },
  "adjectives/chory.json": {
    25: override("I love a sick dog.", sentence(
      "Mam chorego psa.",
      "I have a sick dog.",
      "I-have sick dog.",
      [["Mam", "I-have"], ["chorego", "sick"], ["psa", "dog"]]
    )),
    26: override("Do you love a sick child?", sentence(
      "Czy dziecko jest chore?",
      "Is the child sick?",
      "Whether child is sick?",
      [["Czy", "whether"], ["dziecko", "child"], ["jest", "is"], ["chore", "sick"]]
    )),
    34: override("I am listening about a sick cat.", sentence(
      "Kot jest chory.",
      "The cat is sick.",
      "Cat is sick.",
      [["Kot", "cat"], ["jest", "is"], ["chory", "sick"]]
    )),
  },
  "adjectives/ciekawy.json": {
    13: override("I am listening to an interesting story.", sentence(
      "Słucham ciekawej historii.",
      "I am listening to an interesting story.",
      "I-listen interesting story.",
      [["Słucham", "I-listen"], ["ciekawej", "interesting"], ["historii", "story"]]
    )),
  },
  "adjectives/ciężki.json": {
    13: override("I am listening about a heavy day.", sentence(
      "To był ciężki dzień.",
      "It was a hard day.",
      "This was hard day.",
      [["To", "this"], ["był", "was"], ["ciężki", "hard"], ["dzień", "day"]]
    )),
  },
  "adjectives/delikatny.json": {
    29: override("He listens about a gentle dog.", sentence(
      "Ona ma delikatną sukienkę.",
      "She has a delicate dress.",
      "She has delicate dress.",
      [["Ona", "she"], ["ma", "has"], ["delikatną", "delicate"], ["sukienkę", "dress"]]
    )),
  },
  "adjectives/długi.json": {
    24: override("I am listening about a long day.", sentence(
      "To był długi dzień.",
      "It was a long day.",
      "This was long day.",
      [["To", "this"], ["był", "was"], ["długi", "long"], ["dzień", "day"]]
    )),
    38: override("Y'all are listening about a long book.", sentence(
      "Czytacie długą książkę.",
      "Y'all are reading a long book.",
      "Y'all-read long book.",
      [["Czytacie", "Y'all-read"], ["długą", "long"], ["książkę", "book"]]
    )),
  },
  "adjectives/mocny.json": {
    24: override("We listen about a strong dog.", sentence(
      "Mamy mocną kawę.",
      "We have strong coffee.",
      "We-have strong coffee.",
      [["Mamy", "we-have"], ["mocną", "strong"], ["kawę", "coffee"]]
    )),
  },
  "adjectives/pewny.json": {
    14: override("I am-listening about a confident friend.", sentence(
      "Jestem pewny.",
      "I am sure.",
      "I-am sure.",
      [["Jestem", "I-am"], ["pewny", "sure"]]
    )),
  },
  "adjectives/słaby.json": {
    32: override("They listen about a weak child.", sentence(
      "Czuję się słaby.",
      "I feel weak.",
      "I-feel myself weak.",
      [["Czuję", "I-feel"], ["się", "myself"], ["słaby", "weak"]]
    )),
  },
  "adjectives/słodki.json": {
    35: override("You listen about a sweet dog.", sentence(
      "Masz słodkiego psa.",
      "You have a sweet dog.",
      "You-have sweet dog.",
      [["Masz", "you-have"], ["słodkiego", "sweet"], ["psa", "dog"]]
    )),
  },
  "adjectives/trudny.json": {
    38: override("I am listening about a difficult school.", sentence(
      "Mam trudny dzień.",
      "I am having a hard day.",
      "I-have difficult day.",
      [["Mam", "I-have"], ["trudny", "difficult"], ["dzień", "day"]]
    )),
  },
  "adjectives/wielki.json": {
    15: override("I am listening about a big dog.", sentence(
      "Widzę wielkiego psa.",
      "I see a big dog.",
      "I-see big dog.",
      [["Widzę", "I-see"], ["wielkiego", "big"], ["psa", "dog"]]
    )),
  },
  "adverbs/chętnie.json": {
    34: override("I'll gladly listen about you.", sentence(
      "Chętnie cię posłucham.",
      "I'll gladly listen to you.",
      "Gladly you I-will-listen.",
      [["Chętnie", "gladly"], ["cię", "you"], ["posłucham", "I-will-listen"]]
    )),
  },
};

function override(expectedEn, replacement) {
  return { expectedEn, sentence: replacement };
}

function sentence(pl, en, literal, words) {
  return {
    pl,
    en,
    literal,
    words: words.map(([wordPl, wordEn]) => ({ pl: wordPl, en: wordEn })),
  };
}

function repairSentences(sentences) {
  let changedSentences = 0;
  let changedTokens = 0;
  const repaired = sentences.map((sentence) => {
    let changed = false;
    const next = { ...sentence };
    if (typeof next.pl === "string") {
      const fixed = scrubPolishYall(next.pl);
      if (fixed !== next.pl) {
        next.pl = fixed;
        changed = true;
      }
    }
    if (Array.isArray(next.words)) {
      next.words = next.words.map((token) => {
        if (!token || typeof token !== "object" || typeof token.pl !== "string") return token;
        const fixed = scrubPolishYall(token.pl);
        if (fixed === token.pl) return token;
        changed = true;
        changedTokens += 1;
        return { ...token, pl: fixed };
      });
    }
    if (changed) changedSentences += 1;
    return next;
  });
  return { sentences: repaired, changedSentences, changedTokens };
}

function scrubPolishYall(text) {
  return text.replace(/\by['’]all\b/gi, (_match, offset) => offset === 0 ? "Wy" : "wy");
}

async function fetchJson(url) {
  return JSON.parse(await fetchText(url));
}

async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET ${url} -> HTTP ${response.status}`);
  return response.text();
}

function sha256(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

function parseArgs(raw) {
  const out = {};
  for (let i = 0; i < raw.length; i += 1) {
    const arg = raw[i];
    if (!arg.startsWith("--")) continue;
    const body = arg.slice(2);
    const eq = body.indexOf("=");
    if (eq >= 0) {
      out[body.slice(0, eq)] = body.slice(eq + 1);
    } else {
      const next = raw[i + 1];
      if (next && !next.startsWith("--")) {
        out[body] = next;
        i += 1;
      } else {
        out[body] = "true";
      }
    }
  }
  return out;
}
