import http from "k6/http";
import { check, sleep } from "k6";
import { duration, jsonHeaders, password, registerOrLogin, uniqueEmail, users } from "./common.js";

export const options = {
  vus: users,
  duration,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const email = uniqueEmail("login");
  const token = registerOrLogin(email, {
    email,
    password,
    firstName: "Load",
    lastName: "Tester",
  });

  const response = http.get(`${__ENV.BASE_URL || "http://localhost:8082"}/api/v1/subscriptions/plans`, {
    headers: jsonHeaders(token),
  });

  check(response, {
    "plans accessible": (r) => r.status === 200,
  });

  sleep(1);
}
