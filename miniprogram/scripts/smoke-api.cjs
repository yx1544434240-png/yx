// Development-only, read-only API smoke checks. Never submits orders or changes bindings.
const assert = require('node:assert/strict');
const base = (process.env.MINIPROGRAM_API_BASE || 'http://127.0.0.1:8080/api').replace(/\/$/, '');
const checks = [];
async function api(path, token, method = 'GET') {
  const response = await fetch(base + path, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal: AbortSignal.timeout(15000)
  });
  assert.equal(response.status, 200, `${method} ${path}: HTTP ${response.status}`);
  const result = await response.json();
  assert.equal(result.code, 200, `${method} ${path}: API ${result.code}`);
  return result.data;
}
async function check(path, token, account = 'guest') {
  const data = await api(path, token);
  checks.push({ account, path, passed: true });
  return data;
}
async function main() {
  const catalog = await check('/service-items/list-available');
  assert.ok(Array.isArray(catalog), 'Catalog must be an array');
  for (const username of ['resident01', 'family01', 'staff01']) {
    // Existing demonstration accounts only; no registration or WeChat account creation.
    const login = await api(`/auth/mock-login?username=${username}`, undefined, 'POST');
    assert.ok(login?.token && login.userInfo?.role, 'Invalid login response');
    checks.push({ account: username, path: '/auth/mock-login', passed: true });
    const token = login.token;
    await check('/auth/current-user', token, username);
    const page = await check('/orders/page?pageNum=1&pageSize=20', token, username);
    assert.ok(Array.isArray(page?.records), 'Order page must contain records');
    if (username === 'staff01') {
      await check('/staff/my-profile', token, username);
    } else {
      await check('/elders/my-list', token, username);
      await check('/family-bindings/my-bindings', token, username);
    }
    for (const item of page.records) {
      const id = encodeURIComponent(item.order.id);
      const detail = await check(`/orders/${id}`, token, username);
      assert.equal(String(detail.order.id), String(item.order.id));
      const tracking = await check(`/orders/${id}/tracking`, token, username);
      assert.equal(String(tracking.orderId), String(item.order.id));
    }
  }
  console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
}
main().catch(error => {
  console.error('API smoke check failed:', error.message);
  process.exitCode = 1;
});
