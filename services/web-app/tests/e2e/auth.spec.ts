import { test, expect } from "@playwright/test";

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("auth", () => {
  test("unauthenticated /dashboard redirects to login", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/auth\/login/);
  });

  test("invalid credentials shows error", async ({ page }) => {
    await page.goto("/auth/login");
    await page.locator("#email").fill("invalid@example.com");
    await page.locator("#password").fill("wrongpassword123");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("Invalid email or password")).toBeVisible({ timeout: 10_000 });
  });

  test("valid credentials redirect to dashboard", async ({ page }) => {
    const email = process.env.E2E_EMAIL!;
    const password = process.env.E2E_PASSWORD!;
    await page.goto("/auth/login");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  });

  test("logout redirects to landing page", async ({ page }) => {
    const email = process.env.E2E_EMAIL!;
    const password = process.env.E2E_PASSWORD!;
    await page.goto("/auth/login");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 15_000 });
    await page.getByRole("button", { name: "Exit" }).click();
    await expect(page).toHaveURL(/^\/?$|\/$/);
  });
});
