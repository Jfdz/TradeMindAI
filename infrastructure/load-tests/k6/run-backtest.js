import http from "k6/http";
import { check, sleep } from "k6";
import { baseUrl, duration, jsonHeaders, password, registerOrLogin, uniqueEmail, users } from "./common.js";

export const options = {
  vus: users,
  duration,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<2000"],
  },
};

export default function () {
  const email = uniqueEmail("backtest");
  const token = registerOrLogin(email, {
    email,
    password,
    firstName: "Back",
    lastName: "Tester",
  });

  const response = http.post(
    `${baseUrl}/api/v1/backtests`,
    JSON.stringify({
      symbol: "AAPL",
      from: "2026-04-01",
      to: "2026-04-17",
      quantity: 10,
    }),
    { headers: jsonHeaders(token) },
  );

  check(response, {
    "backtest submitted": (r) => r.status === 202,
  });

  sleep(1);
}
