import { test, expect } from "@playwright/test";

test.describe("portfolio page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/dashboard/portfolio");
    await expect(page.locator("body")).not.toContainText("Application error");
  });

  test("four summary cards are visible", async ({ page }) => {
    // summary cards use p-5; other articles on the page (donut, holdings table) use p-6
    const cards = page.locator("article.p-5");
    await expect(cards).toHaveCount(4, { timeout: 10_000 });
  });

  test("win rate shows percentage or dash, never N/A", async ({ page }) => {
    await page.waitForTimeout(2_000);
    const body = await page.locator("body").textContent();
    expect(body).not.toContain("N/A");
    const hasWinRate = /\d+%|—/.test(body ?? "");
    expect(hasWinRate).toBe(true);
  });

  test("Add Position button navigates to add page", async ({ page }) => {
    await page.getByRole("link", { name: /Add Position/i }).click();
    await expect(page).toHaveURL(/\/dashboard\/portfolio\/add/);
  });

  test("holdings table or empty state is visible", async ({ page }) => {
    const tableOrEmpty = page.locator("table").or(page.getByText("No open positions."));
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 15_000 });
  });

  test("close position modal opens and cancels", async ({ page }) => {
    await expect(page.locator("table").first()).toBeVisible({ timeout: 15_000 });
    const closeBtn = page.getByRole("button", { name: "Close" }).first();
    const hasClosed = await closeBtn.isVisible().catch(() => false);
    if (!hasClosed) {
      test.skip();
      return;
    }
    await closeBtn.click();
    await expect(page.getByText("Exit Price (USD)")).toBeVisible();
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(page.getByText("Exit Price (USD)")).not.toBeVisible();
  });
});

test.describe("add position page", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/dashboard/portfolio/add");
    await expect(page.getByRole("heading", { name: "Add Position" })).toBeVisible();
  });

  test("submitting empty form shows ticker error", async ({ page }) => {
    await page.getByRole("button", { name: "Add Position" }).click();
    await expect(page.getByText("Ticker is required")).toBeVisible();
  });

  test("zero quantity shows validation error", async ({ page }) => {
    // set ticker first (validation order: ticker → qty → price)
    const tickerSelect = page.locator("select").first();
    if (await tickerSelect.isVisible()) {
      await tickerSelect.selectOption({ index: 1 });
    } else {
      await page.locator("input[placeholder='AAPL']").fill("AAPL");
    }
    await page.locator("input[placeholder='10']").fill("0");
    await page.locator("input[placeholder='170.00']").fill("100");
    await page.getByRole("button", { name: "Add Position" }).click();
    await expect(page.getByText("Quantity must be a positive number")).toBeVisible();
  });

  test("cancel returns to portfolio", async ({ page }) => {
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(page).toHaveURL(/\/dashboard\/portfolio$/);
  });
});
