import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.K6_BASE_URL || "http://localhost:8080";
const SESSION_COOKIE = __ENV.K6_SESSION_COOKIE || "";

const errorRate = new Rate("errors");
const apiDuration = new Trend("api_duration", true);

export const options = {
  stages: [
    { duration: "30s", target: 50 },
    { duration: "2m", target: 50 },
    { duration: "15s", target: 100 },
    { duration: "30s", target: 100 },
    { duration: "15s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<500", "p(99)<1000"],
    errors: ["rate<0.01"],
  },
};

function authHeaders() {
  if (!SESSION_COOKIE) return {};
  return { Cookie: `JSESSIONID=${SESSION_COOKIE}` };
}

export default function () {
  group("health check", () => {
    const res = http.get(`${BASE_URL}/actuator/health`);
    check(res, { "health 200": (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
    apiDuration.add(res.timings.duration);
  });

  if (SESSION_COOKIE) {
    group("auth user", () => {
      const res = http.get(`${BASE_URL}/api/auth/user`, {
        headers: authHeaders(),
      });
      check(res, { "auth 200": (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiDuration.add(res.timings.duration);
    });

    group("dashboard metrics", () => {
      const res = http.get(`${BASE_URL}/api/dashboard/metrics`, {
        headers: authHeaders(),
      });
      check(res, { "dashboard 200": (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiDuration.add(res.timings.duration);
    });

    group("personal profile", () => {
      const res = http.get(`${BASE_URL}/api/personal/profile`, {
        headers: authHeaders(),
      });
      check(res, { "profile 200": (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiDuration.add(res.timings.duration);
    });

    group("daily suggestions", () => {
      const res = http.get(`${BASE_URL}/api/personal/suggestions/today`, {
        headers: authHeaders(),
      });
      check(res, { "suggestions 200": (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
      apiDuration.add(res.timings.duration);
    });
  }

  sleep(0.5);
}
