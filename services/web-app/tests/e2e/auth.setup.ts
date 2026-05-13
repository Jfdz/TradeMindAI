import { test as setup } from "@playwright/test";
import { setupClerkTestingToken } from "@clerk/testing/playwright";
import path from "path";

const authFile = path.join(__dirname, "../../.auth/session.json");

const clerkEnabled = process.env.E2E_CLERK_TESTS_ENABLED === "true";

setup("authenticate", async ({ page }) => {
  setup.skip(!clerkEnabled, "Clerk e2e disabled — set E2E_CLERK_TESTS_ENABLED=true once Clerk app is configured for this environment");

  const email = process.env.E2E_EMAIL;
  const password = process.env.E2E_PASSWORD;

  if (!email || !password) {
    throw new Error("E2E_EMAIL and E2E_PASSWORD env vars must be set");
  }

  await setupClerkTestingToken({ page });

  await page.goto("/auth/login");
  // Clerk <SignIn /> multi-step: identifier → Continue → password → Continue
  await page.locator("input[name='identifier']").fill(email);
  await page.getByRole("button", { name: /continue/i }).click();
  await page.locator("input[name='password']").fill(password);
  await page.getByRole("button", { name: /continue/i }).click();

  await page.waitForURL("**/dashboard**", { timeout: 15_000 });
  await page.context().storageState({ path: authFile });
});
