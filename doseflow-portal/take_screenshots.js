const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const edgePaths = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe'
];

const executablePath = edgePaths.find(p => fs.existsSync(p));
if (!executablePath) {
  console.error('Edge executable not found');
  process.exit(1);
}

const artifactDir = 'C:\\Users\\Alwin\\.gemini\\antigravity-ide\\brain\\4107cfd3-fce3-4c8f-a48b-b8df90fc4512';

async function main() {
  console.log('Launching browser with', executablePath);
  const browser = await puppeteer.launch({
    executablePath,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  try {
    const page = await browser.newPage();
    
    // First, test mobile viewport (iPhone / Pixel mobile screen)
    await page.setViewport({ width: 412, height: 915, isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
    console.log('Navigating to http://localhost:3000 (Mobile viewport)...');
    await page.goto('http://localhost:3000', { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 800));

    // Ensure LIVE state is active
    await page.click('#btnSimLive');
    await new Promise(r => setTimeout(r, 800));

    // Capture Mobile LIVE state
    const mobileLivePath = path.join(artifactDir, 'freshness_guard_live_mobile.png');
    await page.screenshot({ path: mobileLivePath, fullPage: false });
    console.log('Saved:', mobileLivePath);

    // Click Stale button
    console.log('Simulating STALE state...');
    await page.click('#btnSimStale');
    await new Promise(r => setTimeout(r, 800));

    // Capture Mobile STALE state
    const mobileStalePath = path.join(artifactDir, 'freshness_guard_stale_mobile.png');
    await page.screenshot({ path: mobileStalePath, fullPage: false });
    console.log('Saved:', mobileStalePath);

    // Click Dead-Letter button
    console.log('Simulating DEAD-LETTER state...');
    await page.click('#btnSimDeadLetter');
    await new Promise(r => setTimeout(r, 800));

    // Capture Mobile DEAD-LETTER state
    const mobileDeadPath = path.join(artifactDir, 'freshness_guard_deadletter_mobile.png');
    await page.screenshot({ path: mobileDeadPath, fullPage: false });
    console.log('Saved:', mobileDeadPath);

    // Also capture desktop viewports
    await page.setViewport({ width: 1280, height: 850, deviceScaleFactor: 2 });
    
    // Desktop Live
    await page.click('#btnSimLive');
    await new Promise(r => setTimeout(r, 800));
    const desktopLivePath = path.join(artifactDir, 'freshness_guard_live_desktop.png');
    await page.screenshot({ path: desktopLivePath, fullPage: false });
    console.log('Saved:', desktopLivePath);

    // Desktop Stale
    await page.click('#btnSimStale');
    await new Promise(r => setTimeout(r, 800));
    const desktopStalePath = path.join(artifactDir, 'freshness_guard_stale_desktop.png');
    await page.screenshot({ path: desktopStalePath, fullPage: false });
    console.log('Saved:', desktopStalePath);

    console.log('All screenshots captured successfully!');
  } finally {
    await browser.close();
  }
}

main().catch(err => {
  console.error('Error during capture:', err);
  process.exit(1);
});
