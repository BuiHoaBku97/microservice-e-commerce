import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const checkoutConflicts = new Counter('checkout_conflicts');
const invariantFailures = new Rate('invariant_failures');
export const options = {
  scenarios: { mixedTraffic: { executor: 'constant-vus', vus: 100, duration: '2m' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    invariant_failures: ['rate==0'],
  },
};
const base = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
export default function () {
  const search = http.get(`${base}/products/search?q=ao&page=0&size=20`, { tags: { flow: 'search' } });
  invariantFailures.add(!check(search, { 'search succeeds': r => r.status === 200 }));
  if (__ENV.HOT_SKU_ID) {
    const availability = http.get(`${base}/inventory/availability?skuIds=${__ENV.HOT_SKU_ID}`,
      { tags: { flow: 'availability' } });
    invariantFailures.add(!check(availability, {
      'availability remains non-negative': r => r.status === 200 && r.json()[0].available >= 0,
    }));
  }
  if (__ENV.CUSTOMER_TOKEN && __ENV.CHECKOUT_BODY) {
    const key = `k6-${__VU}-${__ITER}`;
    const checkout = http.post(`${base}/orders/checkout`, __ENV.CHECKOUT_BODY,
      { headers: { Authorization: `Bearer ${__ENV.CUSTOMER_TOKEN}`,
        'Content-Type': 'application/json', 'Idempotency-Key': key }, tags: { flow: 'checkout' } });
    if (checkout.status === 409) checkoutConflicts.add(1);
    invariantFailures.add(!check(checkout, {
      'checkout returns a controlled result': r => [200, 201, 409].includes(r.status),
    }));
  }
  if (__ENV.ADMIN_TOKEN && __ITER % 10 === 0) {
    const report = http.get(`${base}/admin/reports/payments`, {
      headers: { Authorization: `Bearer ${__ENV.ADMIN_TOKEN}` }, tags: { flow: 'admin-report' },
    });
    invariantFailures.add(!check(report, { 'admin report succeeds': r => r.status === 200 }));
  }
  sleep(0.2);
}
