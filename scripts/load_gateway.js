import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const fixture = JSON.parse(open(__ENV.FIXTURE));
const accepted = new Counter('accepted');
const exhausted = new Counter('stock_empty');
const limited = new Counter('rate_limited');
const unexpected = new Counter('unexpected');
export const options = {
  scenarios: { reservations: { executor: 'shared-iterations', vus: 50,
    iterations: fixture.users.length, maxDuration: '2m' } },
  thresholds: { checks: ['rate==1'], unexpected: ['count==0'], accepted: [`count==${fixture.quota}`] },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const user = fixture.users[exec.scenario.iterationInTest];
  const response = http.post(`${__ENV.BASE_URL}/api/v1/user/appointments/reservations`,
    JSON.stringify({slotId: fixture.slotId}), { headers: {
      'Content-Type': 'application/json', Authorization: `Bearer ${user.token}`,
      'Idempotency-Key': user.key,
    }, timeout: '30s' });
  let code = '';
  try { code = response.json('code'); } catch (_) { /* Count protocol failures below. */ }
  const ok = response.status === 202 && code === 'OK';
  const empty = response.status === 409 && code === 'APPT_409_STOCK_EMPTY';
  const throttled = response.status === 429;
  accepted.add(ok ? 1 : 0); exhausted.add(empty ? 1 : 0);
  limited.add(throttled ? 1 : 0); unexpected.add(ok || empty || throttled ? 0 : 1);
  check(response, {'recognized business outcome': () => ok || empty || throttled});
}
