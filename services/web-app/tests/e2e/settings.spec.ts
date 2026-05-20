import { test, expect } from "@playwright/test";

test.describe("settings page", () => {
  test.beforeEach(async ({ page }) => {
    test.skip(
      process.env.E2E_CLERK_TESTS_ENABLED !== "true",
      "Clerk e2e disabled — authenticated dashboard specs require a real Clerk session",
    );
    await page.goto("/dashboard/settings");
    await expect(page.getByRole("heading", { name: "Configure your workspace" })).toBeVisible();
  });

  test("profile tab is active by default", async ({ page }) => {
    await expect(page.getByText("Update your workspace details")).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Display name" })).toBeVisible({ timeout: 8_000 });
  });

  test("email field is disabled on profile tab", async ({ page }) => {
    const emailInput = page.locator("input[disabled]");
    await expect(emailInput).toBeVisible({ timeout: 8_000 });
  });

  test("plan tab shows plan cards", async ({ page }) => {
    await page.getByRole("button", { name: /plan/i }).click();
    await expect(page.getByText("Current plan", { exact: true })).toBeVisible({ timeout: 8_000 });
    await expect(page.getByRole("link", { name: "Upgrade to Basic" })).toBeVisible();
  });

  test("notifications tab shows 5 toggles", async ({ page }) => {
    await page.getByRole("button", { name: /notifications/i }).click();
    await expect(page.getByText("Signal digest")).toBeVisible();
    await expect(page.getByText("Live alerts")).toBeVisible();
    await expect(page.getByText("Risk warnings")).toBeVisible();
    await expect(page.getByText("Strategy changes")).toBeVisible();
    await expect(page.getByText("Weekly recap")).toBeVisible();
  });

  test("toggling a notification changes its visual state", async ({ page }) => {
    await page.getByRole("button", { name: /notifications/i }).click();
    const toggles = page.locator("button[type='button']").filter({ has: page.locator("span.rounded-full") });
    const firstToggle = toggles.first();
    const classBefore = await firstToggle.getAttribute("class") ?? "";
    await firstToggle.click();
    const classAfter = await firstToggle.getAttribute("class") ?? "";
    expect(classBefore).not.toEqual(classAfter);
  });

  test("switching tabs back to profile shows form again", async ({ page }) => {
    await page.getByRole("button", { name: /plan/i }).click();
    await page.getByRole("button", { name: /profile/i }).click();
    await expect(page.getByText("Update your workspace details")).toBeVisible();
  });
});
