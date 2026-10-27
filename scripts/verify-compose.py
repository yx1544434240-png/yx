"""Verify fresh production containers with disposable credentials/volumes, then remove only this test project."""
import json, os, secrets, subprocess, time, urllib.request, urllib.error, uuid
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / 'backend' / 'target'
target.mkdir(parents=True, exist_ok=True)
project = 'agedcare-audit-' + uuid.uuid4().hex[:8]
password = secrets.token_urlsafe(24)
env = target / (project + '.env')
override = target / (project + '.yml')
env.write_text('\n'.join([
    'MYSQL_ROOT_PASSWORD=' + secrets.token_urlsafe(24), 'DB_PASSWORD=' + secrets.token_urlsafe(24),
    'JWT_SECRET=' + secrets.token_urlsafe(48), 'BOOTSTRAP_ADMIN_PASSWORD=' + password,
]), encoding='utf-8')
env.chmod(0o600)
override.write_text('services:\n  backend:\n    image: agedcare-backend:audit\n  admin:\n    image: agedcare-admin:audit\n', encoding='utf-8')
command = ['docker', 'compose', '--project-name', project, '--env-file', str(env), '-f', str(root / 'compose.yml'), '-f', str(override)]
log = target / 'compose-smoke.log'
environment = os.environ.copy()
for key in ['MYSQL_ROOT_PASSWORD', 'DB_PASSWORD', 'JWT_SECRET', 'BOOTSTRAP_ADMIN_USERNAME', 'BOOTSTRAP_ADMIN_PASSWORD', 'APP_PUBLIC_BASE_URL', 'APP_CORS_ALLOWED_ORIGINS', 'WECHAT_APPID', 'WECHAT_SECRET']:
    environment.pop(key, None)

def call(path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request('http://localhost:8081/api' + path,
        data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)

try:
    with log.open('w', encoding='utf-8') as output:
        subprocess.run(command + ['up', '-d', '--no-build'], cwd=root, env=environment, stdout=output, stderr=subprocess.STDOUT, check=True)
    ready = False
    for _ in range(60):
        try:
            if call('/service-items/list-available')['code'] == 200:
                ready = True
                break
        except Exception: pass
        time.sleep(1)
    if not ready: raise RuntimeError('production containers did not become ready')
    login = call('/auth/login', {'username': 'admin', 'password': password})
    assert login['code'] == 200, 'bootstrap administrator cannot log in'
    token = login['data']['token']
    assert call('/auth/mock-login?username=admin', {}, token)['code'] == 404
    saved = call('/service-items/save', {'name': '隔离容器验收项目', 'category': 'MEAL', 'price': 15, 'durationMinutes': 30}, token)
    assert saved['code'] == 200, 'validated service item cannot be saved'
    assert int(call('/operation-logs/page', token=token)['data']['total']) > 0
    assert call('/media/private/fulfillment/1/2/01234567-89ab-cdef-0123-456789abcdef.jpg')['code'] == 401
    with urllib.request.urlopen('http://localhost:3001', timeout=10) as response:
        assert response.status == 200 and b'<html' in response.read().lower()
    print('PASS: fresh MySQL schema, secure admin bootstrap, real login, disabled mock login, service save, audit log, private photo denial, admin HTTP page')
finally:
    # The randomized project was created by this script; never operate on the user's existing project or volumes.
    assert project.startswith('agedcare-audit-') and len(project) == len('agedcare-audit-') + 8
    with log.open('a', encoding='utf-8') as output:
        subprocess.run(command + ['down', '-v'], cwd=root, env=environment, stdout=output, stderr=subprocess.STDOUT, check=False)
    env.unlink(missing_ok=True)
    override.unlink(missing_ok=True)
