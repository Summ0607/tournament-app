const fs = require('fs');
const path = require('path');

const RANK_LEVELS = {
  ttld: 11,
  g10: 10,
  g9: 9,
  g8: 8,
  g7: 7,
  g6: 6,
  g5: 5,
  g4: 4,
  g3: 3,
  g2: 2,
  g1: 1,
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

function ordinalSuffix(value) {
  const lastTwo = value % 100;
  if (lastTwo >= 11 && lastTwo <= 13) return 'th';
  switch (value % 10) {
    case 1: return 'st';
    case 2: return 'nd';
    case 3: return 'rd';
    default: return 'th';
  }
}

function formatGupLabel(level) {
  return `${level}${ordinalSuffix(level)} Gup`;
}

function formatRankLabel(level) {
  if (level === 11) return 'TTLD';
  if (level >= 1 && level <= 10) return formatGupLabel(level);
  if (level === 0) return 'CDB';
  if (level === -1) return 'Cho Dan';
  if (level === -2) return 'E Dan';
  if (level === -3) return 'Sam Dan';
  return String(level);
}

function normalizeRank(rank) {
  return String(rank || '').trim().toLowerCase();
}

function rankLevelFor(rank) {
  return Object.prototype.hasOwnProperty.call(RANK_LEVELS, normalizeRank(rank))
    ? RANK_LEVELS[normalizeRank(rank)]
    : Number.POSITIVE_INFINITY;
}

function parseRankRangeFromTitle(groupName) {
  const title = String(groupName || '').split('·')[0].trim();
  const normalizedTitle = title.replace(/[–—]/g, '-');

  let match = normalizedTitle.match(/\b(\d+)(?:st|nd|rd|th)\s*-\s*(\d+)(?:st|nd|rd|th)\s+Gup\b/i);
  if (match) {
    return {
      low: formatGupLabel(Number.parseInt(match[1], 10)),
      high: formatGupLabel(Number.parseInt(match[2], 10))
    };
  }

  match = normalizedTitle.match(/\b(\d+)(?:st|nd|rd|th)\s+Gup\b/i);
  if (match) {
    const label = formatGupLabel(Number.parseInt(match[1], 10));
    return { low: label, high: label };
  }

  match = normalizedTitle.match(/\bTTLD\b/i);
  if (match) {
    return {
      low: 'TTLD',
      high: 'TTLD'
    };
  }

  match = normalizedTitle.match(/\b(CDB|Cho Dan Bo|Cho Dan|E Dan|Sam Dan)\s*-\s*(CDB|Cho Dan Bo|Cho Dan|E Dan|Sam Dan)\b/i);
  if (match) {
    return {
      low: match[1],
      high: match[2]
    };
  }

  match = normalizedTitle.match(/\b(CDB|Cho Dan Bo|Cho Dan|E Dan|Sam Dan)\b/i);
  if (match) {
    return {
      low: match[1],
      high: match[1]
    };
  }

  return null;
}

function deriveRankRangeFromCompetitors(group) {
  const ranks = Array.isArray(group.competitors)
    ? group.competitors
        .map((competitor) => rankLevelFor(competitor?.rank))
        .filter((value) => Number.isFinite(value))
    : [];

  if (!ranks.length) {
    return null;
  }

  const lowLevel = Math.max(...ranks);
  const highLevel = Math.min(...ranks);
  return {
    low: formatRankLabel(lowLevel),
    high: formatRankLabel(highLevel)
  };
}

function buildRankRange(group) {
  return parseRankRangeFromTitle(group.name) || deriveRankRangeFromCompetitors(group);
}

function parseArgs(argv) {
  const args = { input: 'master-groups.json', output: 'groups' };

  for (let i = 0; i < argv.length; i += 1) {
    const current = argv[i];
    if (current === '--input') args.input = argv[i + 1];
    if (current === '--output') args.output = argv[i + 1];
  }

  return args;
}

function exportGroups(inputPath, outputDir) {
  if (!fs.existsSync(inputPath)) {
    throw new Error(`Input file not found: ${inputPath}`);
  }

  const master = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
  const groups = master.groups;

  if (!Array.isArray(groups)) {
    throw new Error('The JSON file must contain a top-level "groups" array.');
  }

  fs.mkdirSync(outputDir, { recursive: true });

  groups.forEach((group) => {
    const groupId = group.groupId || group.id;
    if (!groupId) {
      throw new Error('Each group must include a groupId or id value.');
    }

    const ageValues = Array.isArray(group.competitors)
      ? group.competitors
          .map((competitor) => Number(competitor?.age))
          .filter((value) => Number.isFinite(value))
      : [];

    const ageRange = ageValues.length
      ? { min: Math.min(...ageValues), max: Math.max(...ageValues) }
      : { min: 0, max: 0 };
    const rankRange = buildRankRange(group);

    const groupPayload = {
      tournamentId: master.tournamentId,
      tournamentName: master.tournamentName || '',
      groupId,
      name: group.name || `Group ${groupId}`,
      ageRange,
      rankRange,
      matNumber: group.matNumber ?? null,
      competitors: Array.isArray(group.competitors) ? group.competitors : []
    };

    const outputPath = path.join(outputDir, `${groupId}.json`);
    fs.writeFileSync(outputPath, JSON.stringify(groupPayload, null, 2));
    console.log(`Created: ${outputPath}`);
  });

  console.log(`Finished. ${groups.length} group files exported to ${outputDir}.`);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  exportGroups(args.input, args.output);
}

try {
  main();
} catch (error) {
  console.error('Error exporting groups:', error.message);
  process.exit(1);
}
