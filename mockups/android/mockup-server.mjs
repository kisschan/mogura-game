import { createServer } from 'node:http';
import { lstat, open, realpath } from 'node:fs/promises';
import path from 'node:path';

// Deliberately list files, not directories: repository configuration and render
// tooling must never become part of this temporary screenshot server.
export const MOCKUP_FILES = Object.freeze([
  'mockups/android/index.html',
  'mockups/android/styles.css',
  'assets/images/board/board_main.png',
  'assets/images/dice/dice_1.png',
  'assets/images/dice/dice_3.png',
  'assets/images/foods/food_card_back.png',
  'assets/images/foods/food_centipede.jpg',
  'assets/images/foods/food_earthworm.jpg',
  'assets/images/foods/food_frog.jpg',
  'assets/images/foods/food_mole_cricket.jpg',
  'assets/images/players/player_moguka_yellow.png',
  'assets/images/players/player_mogumi_pink.png',
  'assets/images/players/player_moguo_blue.png',
  'assets/images/players/player_moguta_orange.png',
  'assets/images/tiles/tile_back.png',
  'assets/images/tiles/tile_cross.png',
  'assets/images/tiles/tile_l_shape.png',
  'assets/images/tiles/tile_straight.png',
  'assets/images/tiles/tile_t_shape.png',
  'assets/images/ui/hunger_meter_reference.jpg',
]);

const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.png': 'image/png',
};

async function readPublicFile(root, relative) {
  let handle;
  try {
    let candidate = root;
    const parts = relative.split('/');
    let expected;
    for (const [index, part] of parts.entries()) {
      candidate = path.join(candidate, part);
      expected = await lstat(candidate, { bigint: true });
      if (expected.isSymbolicLink()) return null;
      if (index < parts.length - 1 && !expected.isDirectory()) return null;
    }
    if (!expected.isFile() || expected.nlink !== 1n) return null;
    const actual = await realpath(candidate);
    if (path.relative(root, actual) !== path.join(...parts)) return null;

    handle = await open(actual, 'r');
    const opened = await handle.stat({ bigint: true });
    // Detect a link/file replacement between the checks and opening the file.
    if (!opened.isFile() || opened.nlink !== 1n || opened.dev !== expected.dev || opened.ino !== expected.ino) {
      return null;
    }
    return await handle.readFile();
  } catch (error) {
    if (['ENOENT', 'ENOTDIR', 'ELOOP', 'EACCES', 'EPERM'].includes(error.code)) return null;
    throw error;
  } finally {
    await handle?.close();
  }
}

export async function startMockupServer(repositoryRoot) {
  const configuredRoot = path.resolve(repositoryRoot);
  const root = await realpath(configuredRoot);
  if (path.relative(configuredRoot, root) !== '') {
    throw new Error('The mockup repository root must not traverse a symbolic link.');
  }

  // HTTP requests can only select an already loaded public buffer. They never
  // become filesystem paths, and subsequent link changes cannot expose files.
  const files = new Map();
  for (const relative of MOCKUP_FILES) {
    const body = await readPublicFile(root, relative);
    if (body !== null) {
      files.set(`/${relative}`, { body, type: mimeTypes[path.extname(relative)] });
    }
  }

  const server = createServer((request, response) => {
    const send = (status, body, headers = {}) => {
      response.writeHead(status, {
        'Content-Type': 'text/plain; charset=utf-8',
        'Content-Length': Buffer.byteLength(body),
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        ...headers,
      });
      response.end(request.method === 'HEAD' ? undefined : body);
    };

    if (request.headers.host !== `127.0.0.1:${server.address().port}`) {
      send(403, 'Forbidden');
      return;
    }
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      send(405, 'Method Not Allowed', { Allow: 'GET, HEAD' });
      return;
    }

    let pathname;
    try {
      // Do not normalize dot segments before the exact allowlist lookup.
      pathname = decodeURIComponent((request.url ?? '/').split('?', 1)[0]);
    } catch {
      send(400, 'Bad Request');
      return;
    }
    const file = files.get(pathname);
    if (!file) {
      send(404, 'Not Found');
      return;
    }
    send(200, file.body, { 'Content-Type': file.type });
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolve();
    });
  });

  let closing;
  return {
    origin: `http://127.0.0.1:${server.address().port}`,
    close() {
      closing ??= new Promise((resolve, reject) => {
        server.close((error) => error ? reject(error) : resolve());
        server.closeAllConnections();
      });
      return closing;
    },
  };
}
