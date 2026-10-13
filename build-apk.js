#!/usr/bin/env node
const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

console.log('====================================================');
console.log(' 🚀 DoseFlow Android APK Build System');
console.log('====================================================\n');

const isWindows = process.platform === 'win32';
const gradlewCmd = isWindows ? path.join(__dirname, 'gradlew.bat') : path.join(__dirname, 'gradlew');

if (!fs.existsSync(gradlewCmd)) {
  console.error(`❌ gradlew executable not found at ${gradlewCmd}`);
  process.exit(1);
}

console.log('Building debug APK via Gradle wrapper...');
const result = spawnSync(gradlewCmd, ['assembleDebug'], {
  cwd: __dirname,
  stdio: 'inherit',
  shell: isWindows
});

if (result.status !== 0) {
  console.error('\n❌ Build failed with exit code', result.status);
  process.exit(result.status || 1);
}

const apkPath = path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

if (fs.existsSync(apkPath)) {
  const stats = fs.statSync(apkPath);
  const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);
  console.log('\n====================================================');
  console.log(' 🎉 APK BUILT SUCCESSFULLY!');
  console.log(` 📦 File: ${apkPath}`);
  console.log(` 📏 Size: ${sizeMb} MB`);
  console.log('====================================================\n');
} else {
  console.log('Build completed, check app/build/outputs/apk/debug/ for the APK.');
}
