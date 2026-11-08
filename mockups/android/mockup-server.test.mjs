import assert from 'node:assert/strict';
import { request } from 'node:http';
import { mkdir, mkdtemp, readFile, rm, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { MOCKUP_FILES, startMockupServer } from './mockup-server.mjs';

const testDirectory = path.dirname(fileURLToPath(import.meta.url));

async function fixture(t) {
  // Keep every generated file, including link targets, inside this project.
  const directory = await mkdtemp(path.join(testDirectory, '.server-test-'));
  const root = path.join(directory, 'repo');
  await mkdir(root);
  t.after(async () => {
    const relative = path.relative(testDirectory, directory);
    assert.ok(relative.startsWith('.server-test-') && !relative.includes(path.sep));
    await rm(directory, { recursive: true, force: true });
  });
  return { directory, root };
}

async function put(root, relative, content = `fixture: ${relative}`) {
  const target = path.join(root, relative);
  await mkdir(path.dirname(target), { recursive: true });
  await writeFile(target, content);
  return content;
}

async function serve(t, root) {
  const server = await startMockupServer(root);
  t.after(() => server.close());
  return server;
}

function get(server, pathname, { method = 'GET', headers = {} } = {}) {
  const address = new URL(server.origin);
  return new Promise((resolve, reject) => {
    const req = request({
      hostname: address.hostname,
      port: address.port,
      path: pathname,
      method,
      headers,
      agent: false,
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => resolve({
        status: response.statusCode,
        headers: response.headers,
        body: Buffer.concat(chunks).toString('utf8'),
      }));
      response.on('error', reject);
    });
    req.on('error', reject);
    req.end();
  });
}

test('serves every explicitly allowed mockup file with GET and HEAD', async (t) => {
  const { root } = await fixture(t);
  for (const relative of MOCKUP_FILES) await put(root, relative);
  const server = await serve(t, root);
  assert.match(server.origin, /^http:\/\/127\.0\.0\.1:\d+$/);

  for (const relative of MOCKUP_FILES) {
    const response = await get(server, `/${relative}?preview=1`);
    assert.equal(response.status, 200, relative);
    assert.equal(response.body, `fixture: ${relative}`);
    assert.equal(response.headers['cache-control'], 'no-store');
    assert.equal(response.headers['x-content-type-options'], 'nosniff');
    const head = await get(server, `/${relative}`, { method: 'HEAD' });
    assert.equal(head.status, 200);
    assert.equal(head.body, '');
    assert.equal(head.headers['content-length'], response.headers['content-length']);
  }
  assert.equal((await get(server, '/mockups/android/index.html')).headers['content-type'], 'text/html; charset=utf-8');
  assert.equal((await get(server, '/mockups/android/styles.css')).headers['content-type'], 'text/css; charset=utf-8');
});

test('does not expose other repository files, directories, or missing assets', async (t) => {
  const { root } = await fixture(t);
  await put(root, 'internal-fixture.txt', 'not public');
  await put(root, 'mockups/android/render.mjs', 'not public');
  const server = await serve(t, root);
  for (const pathname of ['/', '/internal-fixture.txt', '/mockups/android/', '/mockups/android/render.mjs', '/assets/images/board/board_main.png']) {
    const response = await get(server, pathname);
    assert.equal(response.status, 404, pathname);
    assert.equal(response.body, 'Not Found');
  }
});

test('rejects raw, encoded, double encoded, and Windows-style traversal', async (t) => {
  const { directory, root } = await fixture(t);
  await put(root, 'mockups/android/index.html');
  await put(directory, 'repo-neighbor/public-fixture.txt', 'outside fixture');
  const server = await serve(t, root);
  for (const pathname of [
    '/../repo-neighbor/public-fixture.txt',
    '/..%2frepo-neighbor/public-fixture.txt',
    '/%2e%2e%2frepo-neighbor/public-fixture.txt',
    '/%252e%252e%252frepo-neighbor/public-fixture.txt',
    '/..%5crepo-neighbor%5cpublic-fixture.txt',
    '/mockups/android/../android/index.html',
    '//mockups/android/index.html',
    '/mockups/android/index.html%00',
  ]) {
    assert.equal((await get(server, pathname)).status, 404, pathname);
  }
});

test('returns 400 for malformed percent encoding and keeps serving', async (t) => {
  const { root } = await fixture(t);
  await put(root, 'mockups/android/index.html');
  const server = await serve(t, root);
  for (const pathname of ['/%', '/%ZZ', '/%E0%A4%A']) {
    assert.equal((await get(server, pathname)).status, 400, pathname);
  }
  assert.equal((await get(server, '/mockups/android/index.html')).status, 200);
});

test('allows only GET and HEAD and rejects an unrelated Host', async (t) => {
  const { root } = await fixture(t);
  await put(root, 'mockups/android/index.html');
  const server = await serve(t, root);
  for (const method of ['POST', 'PUT', 'DELETE', 'OPTIONS']) {
    const response = await get(server, '/mockups/android/index.html', { method });
    assert.equal(response.status, 405);
    assert.equal(response.headers.allow, 'GET, HEAD');
  }
  assert.equal((await get(server, '/mockups/android/index.html', {
    headers: { host: 'unrelated.example' },
  })).status, 403);
});

test('does not follow a linked allowed directory into a same-prefix sibling', async (t) => {
  const { directory, root } = await fixture(t);
  const target = path.join(directory, 'repo-neighbor');
  await put(target, 'index.html', 'outside fixture');
  await mkdir(path.join(root, 'mockups'));
  await symlink(target, path.join(root, 'mockups/android'), process.platform === 'win32' ? 'junction' : 'dir');
  const server = await serve(t, root);
  assert.equal((await get(server, '/mockups/android/index.html')).status, 404);
});

test('serves an immutable snapshot if a permitted directory is replaced after startup', async (t) => {
  const { directory, root } = await fixture(t);
  const original = await put(root, 'mockups/android/index.html', 'original public fixture');
  const server = await serve(t, root);
  const target = path.join(directory, 'repo-neighbor');
  await put(target, 'index.html', 'outside fixture');
  const allowedDirectory = path.join(root, 'mockups/android');
  assert.equal(path.relative(root, allowedDirectory), path.join('mockups', 'android'));
  await rm(allowedDirectory, { recursive: true });
  await symlink(target, allowedDirectory, process.platform === 'win32' ? 'junction' : 'dir');
  assert.equal((await get(server, '/mockups/android/index.html')).body, original);
});

test('close is idempotent and releases the listening socket', async (t) => {
  const { root } = await fixture(t);
  const server = await serve(t, root);
  await server.close();
  await server.close();
  await assert.rejects(get(server, '/mockups/android/index.html'), { code: 'ECONNREFUSED' });
});

test('the allowlist covers exactly the current HTML and CSS asset references', async () => {
  const html = await readFile(path.join(testDirectory, 'index.html'), 'utf8');
  const css = await readFile(path.join(testDirectory, 'styles.css'), 'utf8');
  const htmlUrl = new URL('http://127.0.0.1/mockups/android/index.html');
  const references = new Set(['mockups/android/index.html']);
  for (const match of html.matchAll(/(?:src|href)="([^"]+)"/g)) {
    references.add(new URL(match[1], htmlUrl).pathname.slice(1));
  }
  for (const match of html.matchAll(/src:\s*'([^']+)'/g)) {
    references.add(`assets/images/${match[1]}`);
  }
  for (const match of css.matchAll(/url\(\s*['"]?([^'"\s)]+)['"]?\s*\)/g)) {
    references.add(new URL(match[1], htmlUrl).pathname.slice(1));
  }
  assert.deepEqual([...MOCKUP_FILES].sort(), [...references].sort());
});
