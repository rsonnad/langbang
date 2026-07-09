import { expect, test, type Page } from "@playwright/test";
import { homedir } from "node:os";
import { join } from "node:path";

const SHOTS = join(homedir(), "Documents", "Screenshotz");

// A fake session unlocks the gate. Study content (bootstrap/instances/audio) is public —
// the token is only needed for user-specific sync, which the MVP study flow doesn't use.
const FAKE_SESSION = {
  user: { id: "test-user", email: "tester@langbang.test", emailVerified: true },
  token: "lb_playwright_smoke",
  expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
};

async function seedSession(page: Page) {
  await page.addInitScript((session) => {
    localStorage.setItem("langbang.session.v1", JSON.stringify(session));
  }, FAKE_SESSION);
}

test("login gate renders with LangBang branding and email sign-in", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator(".gate-card h1")).toContainText("LangBang");
  await expect(page.getByLabel("Email address")).toBeVisible();
  await expect(page.getByRole("button", { name: /Send sign-in code/i })).toBeVisible();
  await page.screenshot({ path: join(SHOTS, "langbang-web-login.png"), fullPage: true });
});

test("study app: shell, lessons, Now Voicing, and audio resolution", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });

  await seedSession(page);
  await page.goto("/");

  // Shell + tabs render (we're past the gate).
  await expect(page.getByRole("tab", { name: "Phrases" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Pronunciation" })).toBeVisible();

  // Bootstrap (or fallback) content loaded: a known first-group phrase is visible.
  await expect(page.locator(".content")).toContainText("Cześć", { timeout: 15_000 });

  // Now Voicing panel is present.
  await expect(page.locator(".nv .label")).toContainText("Now voicing");

  // Play a phrase → audio URL resolves (an .mp3 is requested) and Now Voicing updates.
  const mp3 = page.waitForRequest(/\.mp3(\?|$)/, { timeout: 20_000 });
  await page.locator('button[aria-label="Play phrase"]').first().click();
  const req = await mp3;
  expect(req.url()).toContain(".mp3");

  // The answer text shows in the Now Voicing panel and the transport appears.
  await expect(page.locator(".nv .answer")).not.toBeEmpty();
  await expect(page.locator(".transport")).toBeVisible();

  await page.screenshot({ path: join(SHOTS, "langbang-web-study.png"), fullPage: true });

  // A word lesson plays forms too.
  await page.getByRole("tab", { name: "Nouns" }).click();
  await expect(page.locator(".content")).toContainText("Play all forms");

  expect(consoleErrors, `console errors: ${consoleErrors.join(" | ")}`).toEqual([]);
});

test("tablet viewport: no horizontal overflow", async ({ page }) => {
  await seedSession(page);
  await page.setViewportSize({ width: 1024, height: 1366 });
  await page.goto("/");
  await expect(page.getByRole("tab", { name: "Phrases" })).toBeVisible();
  await expect(page.locator(".content")).toContainText("Cześć", { timeout: 15_000 });
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(2);
  await page.screenshot({ path: join(SHOTS, "langbang-web-tablet.png"), fullPage: true });
});
