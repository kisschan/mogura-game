import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { startMockupServer } from './mockup-server.mjs';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..', '..');
const outputDir = path.join(scriptDir, 'out');
await mkdir(outputDir, { recursive: true });

const server = await startMockupServer(repoRoot);
const outputPath = path.join(outputDir, 'android-goal-deck-food-ui.png');
let browser;

try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 1080, height: 1920 },
    deviceScaleFactor: 1,
  });

  const htmlUrl = `${server.origin}/mockups/android/index.html`;
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
  try {
    await browser?.close();
  } finally {
    await server.close();
  }
}

console.log(`Rendered Android goal mockup to ${outputPath}`);
