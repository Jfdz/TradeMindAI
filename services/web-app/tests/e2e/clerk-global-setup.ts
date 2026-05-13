import { clerkSetup } from "@clerk/testing/playwright";

export default async function globalSetup() {
  if (
    !process.env.CLERK_SECRET_KEY ||
    !process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
  ) {
    console.warn(
      "[e2e] CLERK_SECRET_KEY or NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY not set — " +
        "skipping Clerk global setup. Auth tests will fail until Phase 0 is complete."
    );
    return;
  }
  await clerkSetup();
}
