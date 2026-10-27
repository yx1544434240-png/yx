const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const SKIP_ADAPTER = /vEthernet|WSL|Hyper-V|Loopback|VirtualBox|VMware|docker|jumeng|Clash|TAP-Windows|VPN|ZeroTier|Bluetooth|Pseudo-Interface/i;
const PREFER_ADAPTER = /Wi-?Fi|WLAN|无线|以太网|Ethernet/i;
const LAN_ASSIGNMENT = /const LAN_BASE_URL = 'http:\/\/[^']+'/;

function isLanIpv4(ip) {
  const parts = String(ip || '').split('.').map(Number);
  if (parts.length !== 4 || parts.some(part => !Number.isInteger(part) || part < 0 || part > 255)) return false;
  if (parts[0] === 127 || (parts[0] === 169 && parts[1] === 254)) return false;
  if (parts[0] === 198 && (parts[1] === 18 || parts[1] === 19)) return false;
  if (parts[0] === 10) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  return parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31;
}

function isIpv4(entry) {
  return entry && (entry.family === 'IPv4' || entry.family === 4);
}

function pickLanAddress(interfaces = os.networkInterfaces()) {
  const candidates = [];
  for (const [name, entries] of Object.entries(interfaces || {})) {
    if (SKIP_ADAPTER.test(name)) continue;
    for (const entry of entries || []) {
      if (!entry || entry.internal || !isIpv4(entry) || !isLanIpv4(entry.address)) continue;
      const prefer = PREFER_ADAPTER.test(name) ? 0 : 1;
      const privateClass = entry.address.startsWith('10.') || entry.address.startsWith('192.168.') ? 0 : 1;
      candidates.push({ ip: entry.address, rank: prefer * 10 + privateClass, name });
    }
  }
  candidates.sort((left, right) => left.rank - right.rank || left.name.localeCompare(right.name));
  return candidates[0] ? candidates[0].ip : null;
}

function buildLanBaseUrl(ip, port = 8080, contextPath = '/api') {
  const normalizedPort = String(port || 8080);
  const context = `/${String(contextPath || 'api').replace(/^\/+|\/+$/g, '')}`;
  return `http://${ip}:${normalizedPort}${context}`;
}

function patchLanBaseUrl(source, url) {
  if (!LAN_ASSIGNMENT.test(source)) throw new Error('LAN_BASE_URL assignment was not found');
  return source.replace(LAN_ASSIGNMENT, `const LAN_BASE_URL = '${url}'`);
}

// 写入未提交的 config/local.js（.gitignore），app.js 仅在开发版读取它
function renderLocalConfig(url) {
  return `// 本文件由 lan-sync 自动生成，已加入 .gitignore，请勿提交。仅开发版(develop)读取。\nmodule.exports = { LAN_BASE_URL: '${url}' };\n`;
}

function parseArgs(argv) {
  const options = { port: 8080, contextPath: '/api', localConfig: path.join(__dirname, '..', 'config', 'local.js') };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === '--port') options.port = value;
    else if (key === '--context-path') options.contextPath = value;
    else if (key === '--local-config') options.localConfig = value;
    else continue;
    index += 1;
  }
  return options;
}

function syncLanBaseUrl(options = {}) {
  const ip = pickLanAddress();
  if (!ip) return { ip: null, url: null, written: false };
  const url = buildLanBaseUrl(ip, options.port, options.contextPath);
  const file = path.resolve(options.localConfig || path.join(__dirname, '..', 'config', 'local.js'));
  const source = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  const next = renderLocalConfig(url);
  const written = next !== source;
  if (written) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, next); }
  return { ip, url, written };
}

function main() {
  if (process.argv.includes('--clear')) {
    // 上传体验版/正式版前可清除本机地址文件，确保包内没有任何局域网 IP
    const file = parseArgs(process.argv.slice(2)).localConfig;
    if (fs.existsSync(file)) fs.unlinkSync(file);
    console.log('[lan-sync] cleared local LAN config');
    return;
  }
  const result = syncLanBaseUrl(parseArgs(process.argv.slice(2)));
  if (!result.ip) {
    console.warn('[lan-sync] no usable LAN IPv4 address was found');
    process.exitCode = 1;
    return;
  }
  console.log(`[lan-sync] ${result.written ? 'updated' : 'unchanged'} ${result.url}`);
}

if (require.main === module) main();

module.exports = { pickLanAddress, isLanIpv4, buildLanBaseUrl, patchLanBaseUrl, renderLocalConfig, syncLanBaseUrl };
