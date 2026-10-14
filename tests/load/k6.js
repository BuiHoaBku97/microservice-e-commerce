import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = { vus: 100, duration: '60s', thresholds: { http_req_failed: ['rate<0.01'] } };
const base = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
export default function () {
  const search = http.get(`${base}/products/search?q=ao&page=0&size=20`);
  check(search, { 'search succeeds': r => r.status === 200 });
  if (__ENV.CUSTOMER_TOKEN && __ENV.CHECKOUT_BODY) {
    const key = `k6-${__VU}-${__ITER}`;
    const checkout = http.post(`${base}/orders/checkout`, __ENV.CHECKOUT_BODY,
      { headers: { Authorization: `Bearer ${__ENV.CUSTOMER_TOKEN}`,
        'Content-Type': 'application/json', 'Idempotency-Key': key } });
    check(checkout, { 'checkout is safe': r => [200, 201, 409].includes(r.status) });
  }
  sleep(0.2);
}
