import type { Page } from "@playwright/test";
import signalsMock from "../fixtures/signals.json";
import portfolioMock from "../fixtures/portfolio.json";

export async function mockSignalsApi(page: Page) {
  await page.route("**/api/v1/signals", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(signalsMock),
    })
  );
}

export async function mockPortfolioApi(page: Page) {
  await page.route("**/api/v1/portfolio", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(portfolioMock),
    })
  );
}
