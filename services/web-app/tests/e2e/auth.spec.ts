import { test, expect } from "@playwright/test";
import { setupClerkTestingToken } from "@clerk/testing/playwright";

// All tests in this file run without a pre-existing session (public/auth project)
test.use({ storageState: { cookies: [], origins: [] } });

const clerkReady =
  /^pk_(test|live)_/.test(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY ?? "") &&
  /^sk_(test|live)_/.test(process.env.CLERK_SECRET_KEY ?? "");

test.describe("auth", () => {
  test("unauthenticated /dashboard redirects to login", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/auth\/login/);
  });

  test("invalid credentials shows error", async ({ page }) => {
    test.skip(
      !clerkReady,
      "Clerk keys missing or invalid — set NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY and CLERK_SECRET_KEY"
    );
    await setupClerkTestingToken({ page });
    await page.goto("/auth/login");
    // Clerk <SignIn /> multi-step form: identifier then password
    await page.locator("input[name='identifier']").fill("invalid@example.com");
    await page.getByRole("button", { name: /continue/i }).click();
    await page.locator("input[name='password']").fill("wrongpassword123");
    await page.getByRole("button", { name: /continue/i }).click();
    // Clerk renders form errors inside its component (cl-formFieldErrorText or cl-alert)
    await expect(
      page.locator(".cl-formFieldErrorText, .cl-alert, [data-clerk-field-error]").first()
    ).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/auth\/login/);
  });

  test("valid credentials redirect to dashboard", async ({ page }) => {
    test.skip(
      !clerkReady,
      "Clerk keys missing or invalid — set NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY and CLERK_SECRET_KEY"
    );
    const email = process.env.E2E_EMAIL!;
    const password = process.env.E2E_PASSWORD!;
    await setupClerkTestingToken({ page });
    await page.goto("/auth/login");
    await page.locator("input[name='identifier']").fill(email);
    await page.getByRole("button", { name: /continue/i }).click();
    await page.locator("input[name='password']").fill(password);
    await page.getByRole("button", { name: /continue/i }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  });

  test("logout redirects to landing page", async ({ page }) => {
    test.skip(
      !clerkReady,
      "Clerk keys missing or invalid — set NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY and CLERK_SECRET_KEY"
    );
    const email = process.env.E2E_EMAIL!;
    const password = process.env.E2E_PASSWORD!;
    await setupClerkTestingToken({ page });
    await page.goto("/auth/login");
    await page.locator("input[name='identifier']").fill(email);
    await page.getByRole("button", { name: /continue/i }).click();
    await page.locator("input[name='password']").fill(password);
    await page.getByRole("button", { name: /continue/i }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 15_000 });
    await page.getByRole("button", { name: "Exit" }).click();
    await expect(page).toHaveURL(/^\/?$|\/$/);
  });
});
