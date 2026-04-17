import http from "k6/http";
import { check, sleep } from "k6";
import { baseUrl, duration, jsonHeaders, password, registerOrLogin, uniqueEmail, users } from "./common.js";

export const options = {
  vus: users,
  duration,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const email = uniqueEmail("signals");
  const token = registerOrLogin(email, {
    email,
    password,
    firstName: "Signal",
    lastName: "Reader",
  });

  const latestResponse = http.get(`${baseUrl}/api/v1/signals/latest`, {
    headers: jsonHeaders(token),
  });
  check(latestResponse, {
    "latest signal available": (r) => r.status === 200,
  });

  const listResponse = http.get(`${baseUrl}/api/v1/signals?size=20&page=0`, {
    headers: jsonHeaders(token),
  });
  check(listResponse, {
    "signal list accessible": (r) => r.status === 200,
  });

  sleep(1);
}
