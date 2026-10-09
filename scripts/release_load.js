import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Synthetic credentials are generated in the isolated JUnit fixture, never committed.
const fixture = JSON.parse(open('/tmp/fixture.json'));
const accepted = new Counter('accepted');
const empty = new Counter('stock_empty');
const duplicate = new Counter('duplicate_active');
const unexpected = new Counter('unexpected');
export const options = {
  scenarios: { burst: { executor: 'per-vu-iterations', vus: fixture.users.length,
    iterations: 1, maxDuration: '2m' } },
  thresholds: { checks: ['rate==1'], accepted: [`count==${fixture.expectedAccepted}`], unexpected: ['count==0'] },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'],
};
export default function () {
  const user = fixture.users[exec.vu.idInTest - 1];
  const response = http.post(`${fixture.base}/api/v1/user/appointments/reservations`,
    JSON.stringify({ slotId: fixture.slotId }), { headers: {
      'Content-Type': 'application/json', Authorization: `Bearer ${user.token}`,
      'Idempotency-Key': user.key,
    }, timeout: '90s' });
  let code = '';
  try { code = response.json('code'); } catch (_) { /* classified below */ }
  const ok = response.status === 202 && code === 'OK';
  const sold = response.status === 409 && code === 'APPT_409_STOCK_EMPTY';
  const dup = response.status === 409 && code === 'APPT_409_DUP_ACTIVE';
  accepted.add(ok ? 1 : 0); empty.add(sold ? 1 : 0); duplicate.add(dup ? 1 : 0);
  unexpected.add(ok || sold || dup ? 0 : 1);
  check(response, { 'expected business result': () => ok || sold || dup });
}
