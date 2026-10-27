const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const { renderLocalConfig, pickLanAddress, buildLanBaseUrl, patchLanBaseUrl } = require('../scripts/sync-lan-ip.cjs');

const sampleIfaces = {
  jumengcloud: [{ address: '198.18.0.1', family: 'IPv4', internal: false }],
  'vEthernet (Default Switch)': [{ address: '172.23.96.1', family: 'IPv4', internal: false }],
  'vEthernet (WSL (Hyper-V firewall))': [{ address: '172.20.144.1', family: 'IPv4', internal: false }],
  WLAN: [{ address: '10.146.147.237', family: 'IPv4', internal: false }],
  'Loopback Pseudo-Interface 1': [{ address: '127.0.0.1', family: 'IPv4', internal: true }]
};

test('LAN picker prefers the Wi-Fi address and skips VPN, Hyper-V and loopback adapters', () => {
  assert.equal(pickLanAddress(sampleIfaces), '10.146.147.237');
});

test('LAN picker understands numeric IPv4 family values from newer Node releases', () => {
  assert.equal(pickLanAddress({
    'Wi-Fi': [{ address: '192.168.1.20', family: 4, internal: false }]
  }), '192.168.1.20');
});

test('LAN picker returns null when the machine has no usable LAN IPv4 address', () => {
  assert.equal(pickLanAddress({
    jumengcloud: [{ address: '198.18.0.1', family: 'IPv4', internal: false }]
  }), null);
});

test('LAN base URL uses the current host, port and servlet context path', () => {
  assert.equal(buildLanBaseUrl('10.146.147.237', 8080, '/api'), 'http://10.146.147.237:8080/api');
  assert.equal(buildLanBaseUrl('192.168.0.8', '9090', 'api'), 'http://192.168.0.8:9090/api');
});

test('patching app.js replaces only the LAN_BASE_URL assignment', () => {
  const source = "const SIMULATOR_BASE_URL = 'http://localhost:8080/api';\nconst LAN_BASE_URL = 'http://10.0.0.1:8080/api';\n";
  const next = patchLanBaseUrl(source, 'http://192.168.1.9:8080/api');
  assert.match(next, /const LAN_BASE_URL = 'http:\/\/192\.168\.1\.9:8080\/api';/);
  assert.match(next, /const SIMULATOR_BASE_URL = 'http:\/\/localhost:8080\/api';/);
});

test('the live machine address is a LAN IPv4 or explicitly absent', () => {
  const ip = pickLanAddress(os.networkInterfaces());
  if (ip) assert.match(ip, /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/);
});

test('lan-sync writes an uncommitted local config instead of app.js', () => {
  const { renderLocalConfig } = require('../scripts/sync-lan-ip.cjs');
  assert.match(renderLocalConfig('http://192.168.1.9:8080/api'), /module\.exports = \{ LAN_BASE_URL: 'http:\/\/192\.168\.1\.9:8080\/api' \};/);
  const ignore = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', '..', '.gitignore'), 'utf8');
  assert.match(ignore, /^miniprogram\/config\/local\.js$/m);
});
