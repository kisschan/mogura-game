import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..', '..');
const outputDir = path.join(scriptDir, 'out');
await mkdir(outputDir, { recursive: true });

const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.png': 'image/png',
};

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://127.0.0.1');
  const requestPath = decodeURIComponent(url.pathname);
  const filePath = path.resolve(repoRoot, `.${requestPath}`);

  if (!filePath.startsWith(repoRoot)) {
    response.writeHead(403);
    response.end('Forbidden');
    return;
  }

  try {
    const body = await readFile(filePath);
    response.writeHead(200, {
      'Content-Type': mimeTypes[path.extname(filePath)] ?? 'application/octet-stream',
    });
    response.end(body);
  } catch {
    response.writeHead(404);
    response.end('Not Found');
  }
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const { port } = server.address();

const browser = await chromium.launch({ headless: true });

const outputPath = path.join(outputDir, 'android-goal-deck-food-ui.png');

try {
  const page = await browser.newPage({
    viewport: { width: 1080, height: 1920 },
    deviceScaleFactor: 1,
  });

  const htmlUrl = `http://127.0.0.1:${port}/mockups/android/index.html`;
  await page.goto(htmlUrl, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => window.__mockupReady === true, { timeout: 10000 });

  const brokenImages = await page.evaluate(() => (
    Array.from(document.images)
      .filter((img) => img.naturalWidth === 0)
      .map((img) => img.getAttribute('src'))
  ));

  if (brokenImages.length > 0) {
    throw new Error(`Broken images: ${brokenImages.join(', ')}`);
  }

  await page.screenshot({
    path: outputPath,
    clip: { x: 0, y: 0, width: 1080, height: 1920 },
  });

  console.log('broken images=0');
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}

console.log(`Rendered Android goal mockup to ${outputPath}`);
