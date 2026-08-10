import { createRequire } from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('../../../webpage/node_modules/playwright');

const outputDir = path.resolve('output/playwright/solution-deck-20260809');
const baseUrl = 'http://127.0.0.1:5173';
const userName = process.env.DEMO_USER;
const password = process.env.DEMO_PASSWORD;

if (!userName || !password) {
  throw new Error('DEMO_USER and DEMO_PASSWORD are required');
}

await fs.mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({
  headless: true,
  channel: 'chrome',
  args: ['--disable-gpu'],
});

const context = await browser.newContext({
  viewport: { width: 1600, height: 900 },
  deviceScaleFactor: 1,
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
  colorScheme: 'light',
});

const page = await context.newPage();
const consoleMessages = [];
page.on('console', (message) => {
  if (['warning', 'error'].includes(message.type())) {
    consoleMessages.push(`${message.type()}: ${message.text()}`);
  }
});

const settle = async (delay = 2500) => {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(delay);
};

const anonymizeHeader = async () => {
  const user = page.locator('.username').first();
  if (await user.count()) {
    await user.evaluate((element) => {
      element.textContent = '演示管理员';
    });
  }
};

const capture = async (fileName) => {
  await anonymizeHeader();
  await page.screenshot({
    path: path.join(outputDir, fileName),
    fullPage: false,
    animations: 'disabled',
  });
};

await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
await settle(1500);
await capture('01-login.png');

await page.getByPlaceholder('请输入用户名').fill(userName);
await page.getByPlaceholder('请输入密码').fill(password);
await page.getByRole('button', { name: '立即登录' }).click();
await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20000 });
await settle(3500);
await capture('02-home-dashboard.png');

const routes = [
  ['/warehouse-map', '03-warehouse-map.png', 3500],
  ['/productStock', '04-inventory-overview.png', 3000],
  ['/production/orders', '05-production-orders.png', 3000],
  ['/assay', '06-quality-assay.png', 3000],
  ['/role', '07-rbac-roles.png', 2500],
  ['/operationlogs', '08-operation-audit.png', 3000],
];

for (const [route, fileName, delay] of routes) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: 'domcontentloaded' });
  await settle(delay);
  await capture(fileName);
}

await page.goto(`${baseUrl}/home`, { waitUntil: 'domcontentloaded' });
await settle(2500);
const assistantButton = page.getByRole('button', { name: /AI 助手/ }).first();
if (await assistantButton.count()) {
  await assistantButton.click();
  await page.getByRole('dialog', { name: 'AI 助手' }).waitFor({ state: 'visible', timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(5000);
  await capture('09-ai-assistant.png');
}

await page.goto('http://127.0.0.1:8080/doc.html', { waitUntil: 'domcontentloaded' });
await settle(6000);
await page.screenshot({
  path: path.join(outputDir, '10-api-documentation.png'),
  fullPage: false,
  animations: 'disabled',
});

await fs.writeFile(
  path.join(outputDir, 'capture-report.json'),
  JSON.stringify({ finalUrl: page.url(), consoleMessages }, null, 2),
  'utf8',
);

await browser.close();
