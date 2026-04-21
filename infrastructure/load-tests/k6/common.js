import http from "k6/http";

export const baseUrl = (__ENV.BASE_URL || "http://localhost:8082").replace(/\/$/, "");
export const users = Number(__ENV.USERS || 100);
export const duration = __ENV.DURATION || "1m";
export const password = __ENV.PASSWORD || "LoadTest123!";

export function uniqueEmail(prefix) {
  return `${prefix}+vu${__VU}@example.com`;
}

export function registerOrLogin(email, registerPayload) {
  const registerResponse = http.post(`${baseUrl}/api/v1/auth/register`, JSON.stringify(registerPayload), {
    headers: jsonHeaders(),
  });

  if (registerResponse.status !== 201 && registerResponse.status !== 409) {
    throw new Error(`Unexpected register status: ${registerResponse.status} ${registerResponse.body}`);
  }

  const loginResponse = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: jsonHeaders() },
  );

  if (loginResponse.status !== 200) {
    throw new Error(`Unexpected login status: ${loginResponse.status} ${loginResponse.body}`);
  }

  const payload = loginResponse.json();
  if (!payload || !payload.accessToken) {
    throw new Error("Login response did not include an access token");
  }

  return payload.accessToken;
}

export function jsonHeaders(token) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}
