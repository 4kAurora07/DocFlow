const path = require('path');
const puppeteer = require(path.resolve(__dirname, '..', 'doseflow-portal', 'node_modules', 'puppeteer-core'));
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

const logoSvgPath = path.resolve(__dirname, '..', 'logo.svg');
const svgContent = fs.readFileSync(logoSvgPath, 'utf8');

const sizes = [
  { folder: 'mipmap-mdpi', size: 48 },
  { folder: 'mipmap-hdpi', size: 72 },
  { folder: 'mipmap-xhdpi', size: 96 },
  { folder: 'mipmap-xxhdpi', size: 144 },
  { folder: 'mipmap-xxxhdpi', size: 192 }
];

async function main() {
  console.log('Generating Android Launcher Icons from logo.svg...');
  const browser = await puppeteer.launch({
    executablePath,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const page = await browser.newPage();

  // Create HTML wrapper for rendering square icon
  const getSquareHtml = (size) => `
    <!DOCTYPE html>
    <html>
    <head>
      <style>
        body, html { margin: 0; padding: 0; width: ${size}px; height: ${size}px; overflow: hidden; background: transparent; }
        svg { width: 100%; height: 100%; display: block; }
      </style>
    </head>
    <body>
      ${svgContent}
    </body>
    </html>
  `;

  // Create HTML wrapper for rendering circular/round icon
  const getRoundHtml = (size) => `
    <!DOCTYPE html>
    <html>
    <head>
      <style>
        body, html { margin: 0; padding: 0; width: ${size}px; height: ${size}px; overflow: hidden; background: transparent; }
        .circle-wrap { width: ${size}px; height: ${size}px; border-radius: 50%; overflow: hidden; }
        svg { width: 100%; height: 100%; display: block; }
      </style>
    </head>
    <body>
      <div class="circle-wrap">
        ${svgContent}
      </div>
    </body>
    </html>
  `;

  const resDir = path.resolve(__dirname, '..', 'app', 'src', 'main', 'res');

  for (const item of sizes) {
    const dir = path.join(resDir, item.folder);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    await page.setViewport({ width: item.size, height: item.size, deviceScaleFactor: 1 });

    // Square
    await page.setContent(getSquareHtml(item.size));
    const squareWebp = path.join(dir, 'ic_launcher.webp');
    await page.screenshot({ path: squareWebp, type: 'webp', omitBackground: true });
    console.log(`Rendered ${squareWebp} (${item.size}x${item.size})`);

    // Round
    await page.setContent(getRoundHtml(item.size));
    const roundWebp = path.join(dir, 'ic_launcher_round.webp');
    await page.screenshot({ path: roundWebp, type: 'webp', omitBackground: true });
    console.log(`Rendered ${roundWebp} (${item.size}x${item.size})`);
  }

  // Also render a 512x512 logo.png in drawable for in-app UI usage
  const drawableDir = path.join(resDir, 'drawable');
  if (!fs.existsSync(drawableDir)) fs.mkdirSync(drawableDir, { recursive: true });

  await page.setViewport({ width: 512, height: 512, deviceScaleFactor: 1 });
  await page.setContent(getSquareHtml(512));
  const appLogoPng = path.join(drawableDir, 'app_logo.png');
  await page.screenshot({ path: appLogoPng, type: 'png', omitBackground: true });
  console.log(`Rendered high-res in-app logo: ${appLogoPng}`);

  await browser.close();
  console.log('✅ All Android launcher icons and drawables generated successfully!');
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
