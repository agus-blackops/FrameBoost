'use strict';
/* Genera las capturas de docs/ con la interfaz real y datos simulados. */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const { chromium } = require('playwright-core');
const fake = require('./fake-upstream');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'docs');

(async () => {
  const up = await fake.start();
  const music = fs.mkdtempSync(path.join(os.tmpdir(), 'sonora-shots-'));
  const env = Object.assign({}, process.env, { SONORA_ALLOW_PRIVATE: '1', SONORA_CACHE_MS: '0',
    SONORA_UPSTREAMS: JSON.stringify({ deezer: up.base + '/deezer', apple: up.base + '/apple', appleTop: up.base + '/appletop', radio: [up.base + '/radio'], lrclib: up.base + '/lrclib' }) });
  const port = 5997;
  const child = spawn(process.execPath, [path.join(ROOT, 'server.js'), '--port', String(port), '--music', music], { env });
  await new Promise((r) => child.stdout.on('data', r));
  const exe = ['/opt/pw-browsers/chromium-1243/chrome-linux64/chrome'].find((p) => fs.existsSync(p));
  const browser = await chromium.launch({ executablePath: exe, args: ['--autoplay-policy=no-user-gesture-required', '--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 860 }, locale: 'es-AR' });
  const shot = (name) => page.screenshot({ path: path.join(OUT, name + '.png') });
  await page.goto(`http://127.0.0.1:${port}/`);
  await page.locator('.card', { hasText: 'Monaco' }).first().waitFor();
  await page.waitForTimeout(900);
  // listas y me gusta
  await page.evaluate(() => {
    const top = window.__sonora.SECTIONS.find((s) => s.id === 'top').tracks;
    const put = (k, v) => localStorage.setItem('sonora.' + k, JSON.stringify(v));
    put('likes', [top[1], top[4]]);
    put('history', [top[2], top[0], top[3]]);
    put('lists', { 'Para correr': [top[5], top[6]] });
  });
  await page.goto(`http://127.0.0.1:${port}/#/`);
  await page.reload();
  await page.locator('.card', { hasText: 'Monaco' }).first().waitFor();
  await page.waitForTimeout(900);
  await page.locator('.card', { hasText: 'Despacito' }).first().click();
  await page.waitForTimeout(1200);
  await shot('inicio');
  // letra
  await page.keyboard.press('l');
  await page.waitForTimeout(700);
  await page.evaluate(() => window.__sonora.api.seek(1600));
  await page.waitForTimeout(900);
  await shot('letra');
  await page.keyboard.press('q');
  await page.waitForTimeout(500);
  await shot('cola');
  await page.keyboard.press('q');
  // podcast
  await page.goto(`http://127.0.0.1:${port}/#/podcasts?q=noticias`);
  await page.locator('.card', { hasText: 'noticias al día' }).waitFor();
  await page.locator('.card', { hasText: 'noticias al día' }).click();
  await page.locator('.trow').first().waitFor();
  await page.locator('button', { hasText: 'Seguir' }).click();
  await page.locator('.trow').first().locator('.main').click();
  await page.waitForTimeout(1500);
  await page.evaluate(() => window.__sonora.api.seek(14000));
  await page.waitForTimeout(800);
  await shot('podcast');
  await browser.close();
  child.kill();
  await up.close();
  fs.rmSync(music, { recursive: true, force: true });
  console.log('capturas en', OUT);
})().catch((e) => { console.error(e); process.exit(1); });
