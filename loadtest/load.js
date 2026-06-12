// Load test for the rate limiter.
// Install k6: brew install k6  (or see https://k6.io/docs/getting-started/installation/)
// Run: k6 run loadtest/load.js

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,
  duration: '20s',
};

const USERS = ['alice', 'bob', 'charlie', 'diana', 'eve'];

export default function () {
  const userId = USERS[Math.floor(Math.random() * USERS.length)];

  const res = http.post('http://localhost:8080/api/demo/order', null, {
    headers: { 'X-User-Id': userId },
  });

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });
}

export function handleSummary(data) {
  const total = data.metrics.http_reqs.values.count;
  const passed = data.metrics['http_req_failed'].values.passes ?? 0;
  console.log(`\nTotal requests: ${total}`);
  console.log(`Each user has limit=5 per minute, so most requests should get 429.`);
  return {};
}
