// Runs after `npx cap add android`: manifest permissions + service, theme, icon, native plugin.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', 'android', 'app', 'src', 'main');

// 1) Manifest: legacy storage, foreground service, notifications
const manPath = path.join(root, 'AndroidManifest.xml');
let man = fs.readFileSync(manPath, 'utf8');
const perms = [
  ['android.permission.WRITE_EXTERNAL_STORAGE', ' android:maxSdkVersion="32"'],
  ['android.permission.READ_EXTERNAL_STORAGE', ' android:maxSdkVersion="32"'],
  ['android.permission.FOREGROUND_SERVICE', ''],
  ['android.permission.FOREGROUND_SERVICE_DATA_SYNC', ''],
  ['android.permission.POST_NOTIFICATIONS', ''],
  ['android.permission.WAKE_LOCK', ''],
];
for (const [p, extra] of perms) {
  if (!man.includes(p)) {
    man = man.replace('</manifest>', '    <uses-permission android:name="' + p + '"' + extra + ' />\n</manifest>');
  }
}
if (!man.includes('requestLegacyExternalStorage')) {
  man = man.replace('<application', '<application android:requestLegacyExternalStorage="true"');
}
if (!man.includes('KeepAliveService')) {
  man = man.replace('</application>',
    '    <service android:name=".KeepAliveService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>');
}
fs.writeFileSync(manPath, man);

// 2) Theme: keep the app below the status bar on Android 15+
const stylePath = path.join(root, 'res', 'values', 'styles.xml');
if (fs.existsSync(stylePath)) {
  let s = fs.readFileSync(stylePath, 'utf8');
  if (!s.includes('windowOptOutEdgeToEdgeEnforcement')) {
    s = s.replace(
      /(<style name="AppTheme\.NoActionBar"[^>]*>)/,
      '$1\n        <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>'
    );
    fs.writeFileSync(stylePath, s);
  }
}

// 3) Icons from resources/
const dens = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
const fgDens = { mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432 };
for (const d of Object.keys(dens)) {
  const dir = path.join(root, 'res', 'mipmap-' + d);
  const ic = path.join(__dirname, '..', 'resources', `icon-${dens[d]}.png`);
  const fg = path.join(__dirname, '..', 'resources', `icon-fg-${fgDens[d]}.png`);
  if (fs.existsSync(ic) && fs.existsSync(dir)) {
    fs.copyFileSync(ic, path.join(dir, 'ic_launcher.png'));
    fs.copyFileSync(ic, path.join(dir, 'ic_launcher_round.png'));
  }
  if (fs.existsSync(fg) && fs.existsSync(dir)) {
    fs.copyFileSync(fg, path.join(dir, 'ic_launcher_foreground.png'));
  }
}
const bgXml = path.join(root, 'res', 'values', 'ic_launcher_background.xml');
if (fs.existsSync(bgXml)) {
  fs.writeFileSync(
    bgXml,
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#0B1220</color>\n</resources>\n'
  );
}

// 4) Native code: SAF plugin + keep-alive service + MainActivity that registers the plugin
const javaDir = path.join(root, 'java', 'com', 'ghpusher', 'app');
fs.mkdirSync(javaDir, { recursive: true });
fs.copyFileSync(path.join(__dirname, 'SafPlugin.java'), path.join(javaDir, 'SafPlugin.java'));
fs.copyFileSync(path.join(__dirname, 'KeepAliveService.java'), path.join(javaDir, 'KeepAliveService.java'));
fs.writeFileSync(path.join(javaDir, 'MainActivity.java'),
  'package com.ghpusher.app;\n\nimport android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;\n\npublic class MainActivity extends BridgeActivity {\n    @Override\n    public void onCreate(Bundle savedInstanceState) {\n        registerPlugin(SafPlugin.class);\n        super.onCreate(savedInstanceState);\n    }\n}\n');
console.log('Android patched.');
