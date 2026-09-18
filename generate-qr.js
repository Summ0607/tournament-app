/**
 * generate-qr.js
 * Generates QR codes for:
 * 1. Specific group URLs (manual override / fallback)
 * 2. Ring bootstrap URLs (normal tablet setup)
 *
 * Usage:
 *   node generate-qr.js --server http://192.168.x.x:3000 [--groups ./groups]
 *                       [--assignments ./ring-assignments.json] [--out ./qr-codes]
 */

const fs = require('fs');
const path = require('path');
const QRCode = require('qrcode');

const args = process.argv.slice(2);

function getArg(name, defaultValue) {
  const index = args.indexOf(name);
  return index !== -1 && args[index + 1] ? args[index + 1] : defaultValue;
}

const SERVER_BASE = getArg('--server', '');
const GROUPS_DIR = getArg('--groups', path.join(__dirname, 'groups'));
const ASSIGNMENTS_FILE = getArg('--assignments', path.join(__dirname, 'ring-assignments.json'));
const OUT_DIR = getArg('--out', path.join(__dirname, 'qr-codes'));

function ensureDirectory(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function htmlEscape(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function normalizeServerBase(value) {
  return String(value || '').trim().replace(/\/+$/, '');
}

function validateServerBase(serverBase) {
  if (!serverBase) {
    throw new Error('Missing --server. Example: node generate-qr.js --server http://HomePC-Sum2:3000');
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(serverBase);
  } catch (_) {
    throw new Error(`Invalid --server value: ${serverBase}`);
  }

  if (parsedUrl.protocol !== 'http:') {
    throw new Error(`Use http:// for local event service, not ${parsedUrl.protocol}//`);
  }

  const host = parsedUrl.hostname.toLowerCase();
  if (host === '127.0.0.1' || host === 'localhost') {
    throw new Error("Do not generate event QR codes with localhost/127.0.0.1. Use the PC's real Wi-Fi IP address.");
  }

  return normalizeServerBase(serverBase);
}

function buildCard({ imagePath, title, subtitle, url }) {
  return `
    <div class="card">
      <img src="${htmlEscape(imagePath)}" alt="QR for ${htmlEscape(title)}">
      <div class="title">${htmlEscape(title)}</div>
      <div class="subtitle">${htmlEscape(subtitle)}</div>
      <div class="url">${htmlEscape(url)}</div>
    </div>`;
}

async function writeQrFile(outputPath, value) {
  await QRCode.toFile(outputPath, value, {
    errorCorrectionLevel: 'M',
    type: 'png',
    width: 300,
    margin: 2
  });
}

async function run() {
  const validatedServerBase = validateServerBase(SERVER_BASE);
  ensureDirectory(OUT_DIR);

  const groupCards = [];
  const ringCards = [];

  const groupFiles = fs.existsSync(GROUPS_DIR)
    ? fs.readdirSync(GROUPS_DIR).filter((file) => file.endsWith('.json')).sort()
    : [];

  for (const filename of groupFiles) {
    const groupId = path.basename(filename, '.json');
    const groupUrl = `${validatedServerBase}/api/groups/${groupId}`;
    const outputPath = path.join(OUT_DIR, `${groupId}.png`);
    const groupJson = readJson(path.join(GROUPS_DIR, filename));
    const label = groupJson.name || groupId;

    await writeQrFile(outputPath, groupUrl);
    console.log(`✓ group ${groupId} -> ${groupUrl}`);

    groupCards.push(buildCard({
      imagePath: path.basename(outputPath),
      title: groupId,
      subtitle: label,
      url: groupUrl
    }));
  }

  if (fs.existsSync(ASSIGNMENTS_FILE)) {
    const assignments = readJson(ASSIGNMENTS_FILE);
    const ringEntries = Object.entries(assignments.rings || {}).sort(([left], [right]) => left.localeCompare(right));

    for (const [ringId, ringState] of ringEntries) {
      const bootstrapUrl = `${validatedServerBase}/api/rings/${ringId}/bootstrap`;
      const outputPath = path.join(OUT_DIR, `${ringId}-bootstrap.png`);
      const currentGroupId = ringState.currentGroupId || 'No current group';
      const ringLabel = ringState.ringLabel || ringId;

      await writeQrFile(outputPath, bootstrapUrl);
      console.log(`✓ ring  ${ringId} -> ${bootstrapUrl}`);

      ringCards.push(buildCard({
        imagePath: path.basename(outputPath),
        title: ringLabel,
        subtitle: `Current: ${currentGroupId}`,
        url: bootstrapUrl
      }));
    }
  }

  const html = `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Tournament QR Codes</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    h1 { font-size: 20px; margin-bottom: 8px; }
    h2 { font-size: 16px; margin: 20px 0 10px; }
    p { margin: 0 0 12px; }
    .grid { display: flex; flex-wrap: wrap; gap: 20px; }
    .card {
      border: 1px solid #ccc;
      border-radius: 8px;
      padding: 12px;
      text-align: center;
      width: 240px;
      break-inside: avoid;
    }
    .card img { width: 200px; height: 200px; }
    .title { font-size: 14px; font-weight: bold; margin-top: 6px; }
    .subtitle { font-size: 12px; color: #333; margin-top: 4px; }
    .url { font-size: 9px; color: #888; margin-top: 4px; word-break: break-all; }
    @media print {
      body { margin: 0; }
      .grid { gap: 10px; }
    }
  </style>
</head>
<body>
  <h1>Tournament QR Codes - ${new Date().toLocaleDateString()}</h1>
  <p>Use ring bootstrap QR codes for normal tablet setup. Group QR codes remain available as a manual fallback.</p>
  <h2>Ring Bootstrap QR Codes</h2>
  <div class="grid">
${ringCards.join('\n')}
  </div>
  <h2>Manual Group Load QR Codes</h2>
  <div class="grid">
${groupCards.join('\n')}
  </div>
</body>
</html>`;

  const htmlPath = path.join(OUT_DIR, 'print-all.html');
  fs.writeFileSync(htmlPath, html, 'utf8');
  console.log(`\nPrintable page: ${htmlPath}`);
  console.log(`Done. Generated ${ringCards.length} ring QR code(s) and ${groupCards.length} group QR code(s).`);
}

run().catch((error) => {
  console.error('Error:', error.message);
  process.exit(1);
});
