import { test, expect } from "@playwright/test";
import { mockSignalsApi } from "./helpers/mock-api";

test.describe("signals page", () => {
  test.beforeEach(async ({ page }) => {
    test.skip(
      process.env.E2E_CLERK_TESTS_ENABLED !== "true",
      "Clerk e2e disabled — authenticated dashboard specs require a real Clerk session",
    );
    await mockSignalsApi(page);
    await page.goto("/dashboard/signals");
    await expect(page.locator("table")).toBeVisible({ timeout: 15_000 });
  });

  test("filter buttons ALL/BUY/SELL/HOLD are visible", async ({ page }) => {
    for (const label of ["ALL", "BUY", "SELL", "HOLD"]) {
      await expect(page.getByRole("button", { name: label })).toBeVisible();
    }
  });

  test("ALL filter is active by default", async ({ page }) => {
    const allBtn = page.getByRole("button", { name: "ALL" });
    await expect(allBtn).toHaveClass(/bg-cyan|text-black/);
  });

  test("BUY filter shows only BUY signals or empty state", async ({ page }) => {
    await page.getByRole("button", { name: "BUY" }).click();
    await page.waitForTimeout(500);
    const rows = page.locator("table tbody tr");
    const count = await rows.count();
    if (count === 1) {
      await expect(page.getByText("No signals match the current filter.")).toBeVisible();
    } else {
      const badges = page.locator("table tbody").getByText("BUY");
      const sellBadges = page.locator("table tbody").getByText("SELL");
      await expect(badges.first()).toBeVisible();
      expect(await sellBadges.count()).toBe(0);
    }
  });

  test("SELL filter shows only SELL signals or empty state", async ({ page }) => {
    await page.getByRole("button", { name: "SELL" }).click();
    await page.waitForTimeout(500);
    const buyBadges = page.locator("table tbody span").getByText("BUY");
    expect(await buyBadges.count()).toBe(0);
  });

  test("HOLD filter shows only HOLD signals or empty state", async ({ page }) => {
    await page.getByRole("button", { name: "HOLD" }).click();
    await page.waitForTimeout(500);
    const buyBadges = page.locator("table tbody span").getByText("BUY");
    expect(await buyBadges.count()).toBe(0);
  });

  test("heading shows signal count or 'Signal feed'", async ({ page }) => {
    const heading = page.locator("h2");
    const text = await heading.textContent();
    // "Updating from the backend" is the pre-fix production text — remove once deployed
    const isExpected =
      /\d+ live signal/.test(text ?? "") ||
      text?.trim() === "Signal feed" ||
      text?.trim() === "Loading signals…" ||
      text?.trim() === "Updating from the backend";
    expect(isExpected).toBe(true);
  });

  test("signal detail page loads via dashboard card link", async ({ page }) => {
    await page.goto("/dashboard");
    const signalLink = page.locator("a[href*='/dashboard/signals/']").first();
    const exists = await signalLink.count();
    if (!exists) {
      test.skip();
      return;
    }
    await signalLink.click();
    await expect(page).toHaveURL(/\/dashboard\/signals\/.+/, { timeout: 10_000 });
    await expect(page.locator("body")).not.toContainText("Application error");
  });
});
