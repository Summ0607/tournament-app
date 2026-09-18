const fs = require('fs');
const path = require('path');
const { buildTimestampForFile, writeTextPdf } = require('./report-pdf');

const RANK_LEVELS = {
  '10th gup': 10,
  '9th gup': 9,
  '8th gup': 8,
  '7th gup': 7,
  '6th gup': 6,
  '5th gup': 5,
  '4th gup': 4,
  '3rd gup': 3,
  '2nd gup': 2,
  '1st gup': 1,
  cdb: 0,
  'cho dan bo': 0,
  'cho dan': -1,
  'e dan': -2,
  'sam dan': -3
};

const CATEGORY_ORDER = [
  'Youth Gup',
  'Youth Black Belt',
  'Adult Gup',
  'Adult Black Belt',
  'Senior Gup',
  'Senior Black Belt'
];

function normalizeRank(rank) {
  return String(rank || '').trim().toLowerCase();
}

function rankLevelFor(rank) {
  const normalized = normalizeRank(rank);
  return Object.prototype.hasOwnProperty.call(RANK_LEVELS, normalized)
    ? RANK_LEVELS[normalized]
    : Number.POSITIVE_INFINITY;
}

function ageBandFor(age) {
  const value = Number(age);
  if (!Number.isFinite(value)) return null;
  if (value < 18) return 'Youth';
  if (value <= 35) return 'Adult';
  return 'Senior';
}

function rankBandFor(rank, rankLevel) {
  const resolvedLevel = Number.isFinite(rankLevel) ? rankLevel : rankLevelFor(rank);
  return resolvedLevel > 0 ? 'Gup' : 'Black Belt';
}

function categoryForCompetitor(competitor) {
  const ageBand = ageBandFor(competitor.age);
  if (!ageBand) return null;
  return `${ageBand} ${rankBandFor(competitor.rank, Number(competitor.rankLevel))}`;
}

function pointsForPlacement(place) {
  const value = String(place || '');
  if (value.startsWith('1st')) return 10;
  if (value.startsWith('2nd')) return 8;
  if (value.includes('3rd')) return 5;
  return 0;
}

function parseArgs(argv) {
  const args = { input: 'Results' };
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === '--input') args.input = argv[index + 1];
  }
  return args;
}

function listJsonFiles(rootDir) {
  if (!fs.existsSync(rootDir)) return [];
  const stack = [rootDir];
  const files = [];
  while (stack.length) {
    const currentDir = stack.pop();
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });
    entries.forEach((entry) => {
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.json') && entry.name !== 'standings.json') {
        files.push(fullPath);
      }
    });
  }
  return files;
}

function loadPackets(rootDir) {
  return listJsonFiles(rootDir)
    .map((filePath) => {
      try {
        const packetText = fs.readFileSync(filePath, 'utf8').replace(/^\uFEFF/, '');
        return { filePath, packet: JSON.parse(packetText) };
      } catch (error) {
        return { filePath, error: error.message };
      }
    })
    .filter((entry) => entry.packet);
}

function collectCupStandings(rootDir) {
  const standings = new Map();
  const packets = loadPackets(rootDir);

  packets.forEach(({ packet }) => {
    const divisionId = String(packet.divisionId || packet.groupId || '').trim();
    const summary = packet.winnerSummary || {};
    ['weapons', 'hyungs', 'sparring'].forEach((key) => {
      const placements = Array.isArray(summary[key]) ? summary[key] : [];
      placements.forEach((placement) => {
        const competitor = placement && placement.competitor;
        if (!competitor) return;

        const category = categoryForCompetitor(competitor);
        if (!category) return;

        const competitorId = String(competitor.id || '').trim();
        if (!competitorId) return;

        const entry = standings.get(category) || new Map();
        const existing = entry.get(competitorId) || {
          competitorId,
          name: String(competitor.name || competitorId),
          studio: String(competitor.studio || ''),
          rank: String(competitor.rank || ''),
          age: Number(competitor.age),
          points: 0,
          divisions: new Set()
        };

        existing.points += pointsForPlacement(placement.place);
        if (divisionId) existing.divisions.add(divisionId);
        entry.set(competitorId, existing);
        standings.set(category, entry);
      });
    });
  });

  return standings;
}

function buildCategoryLines(category, rows) {
  const lines = ['', category];
  if (!rows.length) {
    lines.push('No winners.');
    return lines;
  }

  const header = ['Name', 'School', 'Points'];
  const widths = header.map((title, index) => {
    const values = rows.map((row) => [
      row.name,
      row.school,
      String(row.points)
    ][index]);
    return Math.max(title.length, ...values.map((value) => String(value || '').length));
  });

  lines.push([
    header[0].padEnd(widths[0]),
    header[1].padEnd(widths[1]),
    header[2].padStart(widths[2])
  ].join(' | '));
  lines.push([
    '-'.repeat(widths[0]),
    '-'.repeat(widths[1]),
    '-'.repeat(widths[2])
  ].join('-+-'));

  rows.forEach((row) => {
    lines.push([
      row.name.padEnd(widths[0]),
      row.school.padEnd(widths[1]),
      String(row.points).padStart(widths[2])
    ].join(' | '));
  });

  return lines;
}

function buildReportLines(rowsByCategory) {
  const lines = [];
  CATEGORY_ORDER.forEach((category) => {
    const rows = Array.from((rowsByCategory.get(category) || new Map()).values())
      .filter((row) => row.points > 0)
      .map((row) => ({
        name: row.name,
        school: row.studio,
        points: row.points
      }))
      .sort((left, right) => {
        if (right.points !== left.points) return right.points - left.points;
        return left.name.localeCompare(right.name);
      });
    lines.push(...buildCategoryLines(category, rows));
  });
  return lines;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const inputDir = path.resolve(process.cwd(), args.input);

  if (!fs.existsSync(inputDir)) {
    throw new Error(`Results folder not found: ${inputDir}`);
  }

  const rowsByCategory = collectCupStandings(inputDir);
  const lines = buildReportLines(rowsByCategory);
  const outputPath = path.join(inputDir, `Cup-Winners-${buildTimestampForFile()}.pdf`);
  await writeTextPdf(outputPath, 'Cup Winners', lines);
  console.log(path.basename(outputPath));
}

main().catch((error) => {
  console.error(`Error: ${error.message}`);
  process.exit(1);
});
