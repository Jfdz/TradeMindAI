import { test, expect } from "@playwright/test";
import { mockPortfolioApi, mockSignalsApi } from "./helpers/mock-api";

test.describe("dashboard smoke", () => {
  test.beforeEach(async ({ page }) => {
    test.skip(
      process.env.E2E_CLERK_TESTS_ENABLED !== "true",
      "Clerk e2e disabled — authenticated dashboard specs require a real Clerk session",
    );
    await mockSignalsApi(page);
    await mockPortfolioApi(page);
  });

  test("dashboard home loads", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.locator("body")).not.toContainText("Application error");
    await expect(page.locator("body")).not.toContainText("500");
  });

  test("signals page renders table or empty state", async ({ page }) => {
    await page.goto("/dashboard/signals");
    await expect(page.locator("body")).not.toContainText("Application error");
    // table always renders when not loading/error; empty state row inside when no data
    await expect(page.locator("table")).toBeVisible({ timeout: 10_000 });
  });

  test("portfolio page - win rate is a number, not N/A", async ({ page }) => {
    await page.goto("/dashboard/portfolio");
    await expect(page.locator("body")).not.toContainText("Application error");
    // win rate badge should show a percentage, not hardcoded N/A
    const badge = page.locator("text=/\\d+(\\.\\d+)?%/");
    await expect(badge.first()).toBeVisible({ timeout: 10_000 });
  });

  test("backtests page loads form", async ({ page }) => {
    await page.goto("/dashboard/backtests");
    await expect(page.locator("body")).not.toContainText("Application error");
    await expect(page.locator("form, [role='form']")).toBeVisible({ timeout: 10_000 });
  });

  test("settings page shows user profile", async ({ page }) => {
    await page.goto("/dashboard/settings");
    await expect(page.locator("body")).not.toContainText("Application error");
    await expect(page.locator("body")).not.toContainText("500");
  });
});
