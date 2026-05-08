import { defineConfig, devices } from "@playwright/test";
import { config } from "dotenv";

config({ path: ".env.local" });

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  retries: 0,
  reporter: [["html", { open: "on-failure" }], ["list"]],
  use: {
    baseURL: process.env.BASE_URL ?? "https://trademind.es",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: "chromium",
      testMatch: /(?<!auth|public)\.spec\.ts$/,
      use: {
        ...devices["Desktop Chrome"],
        storageState: ".auth/session.json",
      },
      dependencies: ["setup"],
    },
    {
      name: "chromium-public",
      testMatch: /(auth|public)\.spec\.ts$/,
      use: {
        ...devices["Desktop Chrome"],
      },
    },
  ],
});
