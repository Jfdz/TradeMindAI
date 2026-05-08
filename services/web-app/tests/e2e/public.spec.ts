import { test, expect } from "@playwright/test";

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("public pages", () => {
  test("landing page hero and CTAs", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("h1")).toContainText("Trading intelligence");
    await expect(page.getByRole("link", { name: /Start for Free/i })).toHaveAttribute("href", "/auth/register");
    await expect(page.getByRole("link", { name: /Compare Plans/i })).toHaveAttribute("href", "/pricing");
  });

  test("landing page stats are present", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("Signals generated")).toBeVisible();
    await expect(page.getByText("Model accuracy")).toBeVisible();
  });

  test("pricing page shows 3 plan cards", async ({ page }) => {
    await page.goto("/pricing");
    await expect(page.locator("body")).not.toContainText("Application error");
    const planCards = page.locator("article, [class*='plan'], [class*='card']").filter({ hasText: /FREE|BASIC|PREMIUM/ });
    await expect(planCards).toHaveCount(3, { timeout: 8_000 });
  });

  test("login page renders form", async ({ page }) => {
    await page.goto("/auth/login");
    await expect(page.locator("#email")).toBeVisible();
    await expect(page.locator("#password")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });

  test("register page renders all fields", async ({ page }) => {
    await page.goto("/auth/register");
    await expect(page.locator("#firstName")).toBeVisible();
    await expect(page.locator("#lastName")).toBeVisible();
    await expect(page.locator("#email")).toBeVisible();
    await expect(page.locator("#password")).toBeVisible();
    await expect(page.locator("#confirmPassword")).toBeVisible();
    await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();
  });

  test("register form validates password mismatch", async ({ page }) => {
    await page.goto("/auth/register");
    await page.locator("#firstName").fill("Test");
    await page.locator("#lastName").fill("User");
    await page.locator("#email").fill("test@example.com");
    await page.locator("#password").fill("password123");
    await page.locator("#confirmPassword").fill("different999");
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page.getByText("Passwords do not match")).toBeVisible();
  });
});
