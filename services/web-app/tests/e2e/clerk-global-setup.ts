import { clerkSetup } from "@clerk/testing/playwright";

export default async function globalSetup() {
  if (process.env.E2E_CLERK_TESTS_ENABLED !== "true") {
    console.warn(
      "[e2e] E2E_CLERK_TESTS_ENABLED is not 'true' — skipping Clerk global setup. " +
        "Set E2E_CLERK_TESTS_ENABLED=true once the Clerk app is configured for this environment."
    );
    return;
  }
  await clerkSetup();
}
