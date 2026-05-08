import { test, expect } from "@playwright/test";

test.describe("backtests page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/dashboard/backtests");
    await expect(page.locator("body")).not.toContainText("Application error");
    await expect(page.locator("form")).toBeVisible({ timeout: 10_000 });
  });

  test("form renders all key fields", async ({ page }) => {
    await expect(page.locator("select").first()).toBeVisible();
    await expect(page.locator("input[type='date'], input[name='from']").first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Run backtest/i })).toBeVisible();
  });

  test("results panel shows placeholder before any submission", async ({ page }) => {
    await expect(page.getByText("No results yet")).toBeVisible();
  });

  test("clearing capital and submitting shows validation error", async ({ page }) => {
    const capitalInput = page.locator("input[type='number']").first();
    await capitalInput.fill("");
    await page.getByRole("button", { name: /Run backtest/i }).click();
    await expect(
      page.getByText("Initial capital must be greater than zero")
    ).toBeVisible({ timeout: 5_000 });
  });

  test("date range fields are pre-filled with valid dates", async ({ page }) => {
    const fromValue = await page.locator("input[name='from']").inputValue();
    const toValue = await page.locator("input[name='to']").inputValue();
    expect(fromValue).not.toBe("");
    expect(toValue).not.toBe("");
    expect(new Date(toValue) >= new Date(fromValue)).toBe(true);
  });

  test("submit button is enabled and form is submittable", async ({ page }) => {
    const btn = page.getByRole("button", { name: /Run backtest/i });
    await expect(btn).toBeEnabled();
    await expect(btn).toHaveAttribute("type", "submit");
    // Clicking does not crash the app (API result or form stays intact)
    await btn.click();
    await expect(page.locator("body")).not.toContainText("Application error", { timeout: 5_000 });
  });
});
