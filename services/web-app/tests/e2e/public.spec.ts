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

  test("login page renders Clerk sign-in form", async ({ page }) => {
    await page.goto("/auth/login");
    await expect(page.locator("body")).not.toContainText("Application error");
    // Clerk <SignIn /> renders identifier (email) input on first step
    await expect(page.locator("input[name='identifier']")).toBeVisible({ timeout: 10_000 });
  });

  test("register page renders Clerk sign-up form", async ({ page }) => {
    await page.goto("/auth/register");
    await expect(page.locator("body")).not.toContainText("Application error");
    // Clerk <SignUp /> renders email address input on first step
    await expect(page.locator("input[name='emailAddress']")).toBeVisible({ timeout: 10_000 });
  });
});
