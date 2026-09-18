const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = 3000;
const GROUPS_DIR = path.join(__dirname, 'groups');
const RING_ASSIGNMENTS_FILE = path.join(__dirname, 'ring-assignments.json');
const CONTROL_BOARD_DIR = path.join(__dirname, 'head-table');
const RESULTS_DIR = path.join(__dirname, 'Results');

const MAX_LETTERS = 26;
const MAX_NUMBERS = 50;
const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const DISCONNECT_AFTER_MS = 90 * 1000;

app.use(express.json());
app.use('/control-board-assets', express.static(CONTROL_BOARD_DIR));

function clampInt(value, fallback, min, max) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

function buildRingId(letter, number) {
  return `ring-${letter.toLowerCase()}-${number}`;
}

function buildRingLabel(letter, number) {
  return `${letter}${number}`;
}

function createEmptyRingState(ringLabel) {
  return {
    ringLabel,
    currentGroupId: '',
    queuedGroupIds: [],
    completedGroupIds: [],
    assistanceType: '',
    assistanceRequestedAt: '',
    tabletLabel: '',
    lastHeartbeatAt: '',
    phaseStartedAt: '',
    phase: 'idle'
  };
}

function normalizeRingState(ringState, ringLabel) {
  const normalized = createEmptyRingState(ringLabel);
  if (!ringState || typeof ringState !== 'object') {
    return normalized;
  }
  normalized.ringLabel = ringState.ringLabel || ringLabel;
  normalized.currentGroupId = ringState.currentGroupId || '';
  normalized.queuedGroupIds = Array.isArray(ringState.queuedGroupIds) ? ringState.queuedGroupIds : [];
  normalized.completedGroupIds = Array.isArray(ringState.completedGroupIds) ? ringState.completedGroupIds : [];
  normalized.assistanceType = ringState.assistanceType || '';
  normalized.assistanceRequestedAt = ringState.assistanceRequestedAt || '';
  normalized.tabletLabel = ringState.tabletLabel || '';
  normalized.lastHeartbeatAt = ringState.lastHeartbeatAt || '';
  normalized.phase = ringState.phase || 'idle';
  normalized.phaseStartedAt = ringState.phaseStartedAt || (normalized.phase !== 'idle' ? normalized.lastHeartbeatAt || '' : '');
  return normalized;
}

function normalizeConfig(rawConfig) {
  const defaults = { letterCount: 1, numberCount: 2 };
  if (!rawConfig || typeof rawConfig !== 'object') return defaults;
  return {
    letterCount: clampInt(rawConfig.letterCount, defaults.letterCount, 1, MAX_LETTERS),
    numberCount: clampInt(rawConfig.numberCount, defaults.numberCount, 1, MAX_NUMBERS)
  };
}

function generateRings(config, existingRings = {}) {
  const rings = {};
  for (let letterIndex = 0; letterIndex < config.letterCount; letterIndex += 1) {
    const letter = LETTERS[letterIndex];
    for (let number = 1; number <= config.numberCount; number += 1) {
      const ringId = buildRingId(letter, number);
      const ringLabel = buildRingLabel(letter, number);
      rings[ringId] = normalizeRingState(existingRings[ringId], ringLabel);
    }
  }
  return rings;
}

function createDefaultAssignments() {
  const config = { letterCount: 1, numberCount: 2 };
  return {
    config,
    rings: generateRings(config)
  };
}

function ensureStateStructure(rawState) {
  const state = rawState && typeof rawState === 'object' ? rawState : {};
  const config = normalizeConfig(state.config);
  return {
    config,
    rings: generateRings(config, state.rings || {})
  };
}

function readAssignmentsState() {
  if (!fs.existsSync(RING_ASSIGNMENTS_FILE)) {
    const defaults = createDefaultAssignments();
    fs.writeFileSync(RING_ASSIGNMENTS_FILE, JSON.stringify(defaults, null, 2));
    return defaults;
  }
  const raw = JSON.parse(fs.readFileSync(RING_ASSIGNMENTS_FILE, 'utf8'));
  const normalized = ensureStateStructure(raw);
  fs.writeFileSync(RING_ASSIGNMENTS_FILE, JSON.stringify(normalized, null, 2));
  return normalized;
}

function readAndroidVersionInfo() {
  const candidates = [
    path.join(__dirname, 'app', 'build.gradle.kts'),
    path.join(__dirname, 'build.gradle.kts')
  ];

  for (const candidate of candidates) {
    if (!fs.existsSync(candidate)) continue;

    const gradleText = fs.readFileSync(candidate, 'utf8');
    const versionCodeMatch = gradleText.match(/versionCode\s*=\s*(\d+)/);
    const versionNameMatch = gradleText.match(/versionName\s*=\s*["']([^"']+)["']/);

    if (versionCodeMatch && versionNameMatch) {
      return {
        versionCode: Number.parseInt(versionCodeMatch[1], 10),
        versionName: versionNameMatch[1]
      };
    }
  }

  return { versionCode: 0, versionName: '0.0' };
}

function writeAssignmentsState(state) {
  fs.writeFileSync(RING_ASSIGNMENTS_FILE, JSON.stringify(state, null, 2));
}

function ensureResultsDir() {
  if (!fs.existsSync(RESULTS_DIR)) {
    fs.mkdirSync(RESULTS_DIR, { recursive: true });
  }
}

function sanitizeFilePart(value) {
  return String(value || '')
    .trim()
    .replace(/[<>:"/\\|?*\x00-\x1F]/g, '_')
    .replace(/\s+/g, '_')
    .slice(0, 120);
}

function saveUploadedDivisionPacket(ringId, packet) {
  if (!packet || typeof packet !== 'object') return;
  ensureResultsDir();
  const divisionId = sanitizeFilePart(packet.divisionId || packet.groupId || ringId || 'division-unknown');
  const filePath = path.join(RESULTS_DIR, `${divisionId}.json`);
  fs.writeFileSync(filePath, JSON.stringify(packet, null, 2));
}

function getRingState(state, ringId) {
  return state.rings[ringId] || null;
}

function listAllowedRingLabels(state) {
  return Object.values(state.rings).map((ring) => ring.ringLabel);
}

function loadGroup(groupId) {
  if (!groupId) return null;
  const filePath = path.join(GROUPS_DIR, `${groupId}.json`);
  if (!fs.existsSync(filePath)) return null;
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function listGroups() {
  if (!fs.existsSync(GROUPS_DIR)) return [];
  return fs.readdirSync(GROUPS_DIR)
    .filter((fileName) => fileName.endsWith('.json'))
    .sort()
    .map((fileName) => {
      const groupId = path.basename(fileName, '.json');
      const group = loadGroup(groupId) || {};
      return { groupId, name: group.name || groupId };
    });
}

function groupExists(groupId) {
  return !!loadGroup(groupId);
}

function findGroupUsageAcrossRings(state, targetGroupId, excludedRingId = '') {
  if (!targetGroupId) return null;
  const rings = state.rings || {};
  for (const [ringId, ringState] of Object.entries(rings)) {
    if (ringId === excludedRingId) continue;
    const queue = Array.isArray(ringState.queuedGroupIds) ? ringState.queuedGroupIds : [];
    if (ringState.currentGroupId === targetGroupId || queue.includes(targetGroupId)) {
      return {
        ringId,
        ringLabel: ringState.ringLabel || ringId
      };
    }
  }
  return null;
}

function serverBaseUrlForRequest(req) {
  return `${req.protocol}://${req.get('host')}`;
}

function buildRingResponse(req, ringId, ringState) {
  const tabletLabel = String(req.query.tabletLabel || '').trim();
  return {
    ringId,
    ringLabel: ringState.ringLabel,
    serverBaseUrl: serverBaseUrlForRequest(req),
    currentGroupId: ringState.currentGroupId,
    queuedGroupIds: ringState.queuedGroupIds,
    completedGroupIds: ringState.completedGroupIds,
    assistanceType: ringState.assistanceType,
    assistanceRequestedAt: ringState.assistanceRequestedAt,
    tabletLabel: ringState.tabletLabel,
    lastHeartbeatAt: ringState.lastHeartbeatAt,
    phaseStartedAt: ringState.phaseStartedAt,
    phase: ringState.phase,
    currentGroup: loadGroup(ringState.currentGroupId),
    ...buildRingAvailability(ringState, tabletLabel)
  };
}

function touchHeartbeat(ringState) {
  ringState.lastHeartbeatAt = new Date().toISOString();
}

function setRingPhase(ringState, phase) {
  const nextPhase = phase || 'idle';
  if (ringState.phase !== nextPhase || !ringState.phaseStartedAt) {
    ringState.phase = nextPhase;
    ringState.phaseStartedAt = new Date().toISOString();
    return;
  }
  ringState.phase = nextPhase;
}

function resetRingToScratch(ringState) {
  ringState.currentGroupId = '';
  ringState.queuedGroupIds = [];
  ringState.completedGroupIds = [];
  ringState.assistanceType = '';
  ringState.assistanceRequestedAt = '';
  ringState.tabletLabel = '';
  ringState.lastHeartbeatAt = '';
  ringState.phaseStartedAt = '';
  ringState.phase = 'idle';
}

function ringClaimAgeMs(ringState) {
  if (!ringState || !ringState.lastHeartbeatAt) return Number.POSITIVE_INFINITY;
  const heartbeatTime = Date.parse(ringState.lastHeartbeatAt);
  if (!Number.isFinite(heartbeatTime)) return Number.POSITIVE_INFINITY;
  return Date.now() - heartbeatTime;
}

function ringIsClaimedByAnotherClient(ringState, tabletLabel) {
  const claimedTabletLabel = String(ringState.tabletLabel || '').trim();
  if (!claimedTabletLabel) return false;
  if (tabletLabel && claimedTabletLabel === tabletLabel) return false;
  return ringClaimAgeMs(ringState) < DISCONNECT_AFTER_MS;
}

function buildRingAvailability(ringState, tabletLabel = '') {
  const inUse = ringIsClaimedByAnotherClient(ringState, tabletLabel);
  return {
    isAvailable: !inUse,
    isInUse: inUse,
    statusLabel: inUse ? 'In use' : 'Available',
    claimedByTabletLabel: inUse ? (ringState.tabletLabel || '') : ''
  };
}

function applyHeartbeatPolicy(state) {
  // Manual per-ring reset keeps a disconnected ring recoverable until an operator clears it.
  return false;
}

function resetAssignments(state) {
  state.rings = generateRings(state.config);
}

function setRingConfig(state, letterCount, numberCount) {
  state.config = normalizeConfig({ letterCount, numberCount });
  state.rings = generateRings(state.config);
}

app.get('/api/version', (req, res) => {
  const versionInfo = readAndroidVersionInfo();
  res.json({
    versionCode: versionInfo.versionCode,
    versionName: versionInfo.versionName
  });
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true, message: 'Group server is running' });
});

app.get('/api/groups', (req, res) => {
  res.json({ groups: listGroups() });
});

app.get('/api/groups/:groupId', (req, res) => {
  const { groupId } = req.params;
  const group = loadGroup(groupId);
  if (!group) {
    return res.status(404).json({
      error: 'Group not found',
      groupId,
      availableAt: `/api/groups/${groupId}`
    });
  }
  return res.json(group);
});

app.get('/api/rings/config', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const tabletLabel = String(req.query.tabletLabel || '').trim();
  return res.json({
    config: state.config,
    heartbeatPolicy: {
      disconnectAfterSeconds: DISCONNECT_AFTER_MS / 1000,
      resetMode: 'manual'
    },
    allowedRings: Object.entries(state.rings).map(([ringId, ringState]) => ({
      ringId,
      ringLabel: ringState.ringLabel,
      ...buildRingAvailability(ringState, tabletLabel)
    }))
  });
});

app.post('/api/rings/config', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const { letterCount, numberCount } = req.body || {};
  setRingConfig(state, letterCount, numberCount);
  writeAssignmentsState(state);
  return res.json({
    ok: true,
    config: state.config,
    message: 'Ring configuration applied. All rings reset to unassigned.'
  });
});

app.get('/api/rings', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const tabletLabel = String(req.query.tabletLabel || '').trim();
  const rings = Object.entries(state.rings).map(([ringId, ringState]) => ({
    ringId,
    ringLabel: ringState.ringLabel,
    currentGroupId: ringState.currentGroupId,
    queuedGroupIds: ringState.queuedGroupIds,
    completedGroupIds: ringState.completedGroupIds,
    assistanceType: ringState.assistanceType,
    assistanceRequestedAt: ringState.assistanceRequestedAt,
    tabletLabel: ringState.tabletLabel,
    lastHeartbeatAt: ringState.lastHeartbeatAt,
    phaseStartedAt: ringState.phaseStartedAt,
    phase: ringState.phase,
    currentGroupName: (loadGroup(ringState.currentGroupId) || {}).name || '',
    ...buildRingAvailability(ringState, tabletLabel)
  }));

  return res.json({
    serverBaseUrl: serverBaseUrlForRequest(req),
    ringConfig: state.config,
    heartbeatPolicy: {
      disconnectAfterSeconds: DISCONNECT_AFTER_MS / 1000,
      resetMode: 'manual'
    },
    rings
  });
});

app.get('/api/rings/:ringId/bootstrap', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  const tabletLabel = String(req.query.tabletLabel || '').trim();
  if (ringIsClaimedByAnotherClient(ringState, tabletLabel)) {
    return res.status(409).json({
      error: `Ring ${ringState.ringLabel} is in use.`,
      ringId,
      ringLabel: ringState.ringLabel,
      claimedByTabletLabel: ringState.tabletLabel || ''
    });
  }
  if (tabletLabel) {
    ringState.tabletLabel = tabletLabel;
    touchHeartbeat(ringState);
    if (!ringState.phase || ringState.phase === 'idle') {
      setRingPhase(ringState, 'check-in');
    }
  }

  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.get('/api/rings/:ringId/current', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/complete', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  saveUploadedDivisionPacket(ringId, req.body);

  if (ringState.currentGroupId) {
    ringState.completedGroupIds.push(ringState.currentGroupId);
  }
  ringState.currentGroupId = ringState.queuedGroupIds.shift() || '';
  setRingPhase(ringState, ringState.currentGroupId ? 'check-in' : 'idle');
  touchHeartbeat(ringState);
  writeAssignmentsState(state);

  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/reset', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  resetRingToScratch(ringState);
  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/queue', (req, res) => {
  const groupId = String(req.body.groupId || '').trim();
  if (!groupId) {
    return res.status(400).json({ error: 'groupId is required' });
  }
  if (!groupExists(groupId)) {
    return res.status(404).json({ error: `Group not found: ${groupId}` });
  }

  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  if (ringState.currentGroupId === groupId || ringState.queuedGroupIds.includes(groupId)) {
    return res.status(409).json({ error: `Group already assigned or queued: ${groupId}` });
  }
  const usage = findGroupUsageAcrossRings(state, groupId, ringId);
  if (usage) {
    return res.status(409).json({
      error: `Group ${groupId} is already assigned or queued in ${usage.ringLabel}. Remove it there before reassigning.`
    });
  }

  ringState.queuedGroupIds.push(groupId);
  touchHeartbeat(ringState);
  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/heartbeat', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  const tabletLabel = String(req.body.tabletLabel || '').trim();
  const phase = String(req.body.phase || '').trim();
  if (tabletLabel) ringState.tabletLabel = tabletLabel;
  if (phase) setRingPhase(ringState, phase);
  touchHeartbeat(ringState);
  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/assistance', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  const assistanceType = String(req.body.type || '').trim().toLowerCase();
  if (!['medical', 'arbitrator', 'general'].includes(assistanceType)) {
    return res.status(400).json({ error: 'type must be one of: medical, arbitrator, general' });
  }

  ringState.assistanceType = assistanceType;
  ringState.assistanceRequestedAt = new Date().toISOString();
  touchHeartbeat(ringState);
  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/rings/:ringId/assistance/clear', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringId = String(req.params.ringId || '').trim();
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  ringState.assistanceType = '';
  ringState.assistanceRequestedAt = '';
  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.post('/api/reset', (req, res) => {
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  resetAssignments(state);
  writeAssignmentsState(state);
  return res.json({ ok: true, message: 'Tournament reset. All rings cleared.' });
});

app.delete('/api/rings/:ringId/queue/:groupId', (req, res) => {
  const { ringId, groupId } = req.params;
  const state = readAssignmentsState();
  if (applyHeartbeatPolicy(state)) writeAssignmentsState(state);
  const ringState = getRingState(state, ringId);
  if (!ringState) {
    return res.status(400).json({
      error: `Invalid ring '${ringId}'. Allowed rings: ${listAllowedRingLabels(state).join(', ')}`
    });
  }

  const before = ringState.queuedGroupIds.length;
  ringState.queuedGroupIds = ringState.queuedGroupIds.filter((id) => id !== groupId);
  if (ringState.queuedGroupIds.length === before) {
    return res.status(404).json({ error: `Group not in queue: ${groupId}` });
  }

  writeAssignmentsState(state);
  return res.json(buildRingResponse(req, ringId, ringState));
});

app.get('/download-app', (req, res) => {
  const apkPath = path.join(__dirname, 'app-debug.apk');
  if (!fs.existsSync(apkPath)) {
    return res.status(404).send('APK not found. Build the app first and place app-debug.apk in the server folder.');
  }
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', 'attachment; filename="tournament-scoring.apk"');
  res.sendFile(apkPath);
});

app.get('/control-board', (req, res) => {
  res.sendFile(path.join(CONTROL_BOARD_DIR, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Group server running at http://localhost:${PORT}`);
  console.log(`Control board: http://localhost:${PORT}/control-board`);
  console.log(`Group example: http://localhost:${PORT}/api/groups/group-1`);
  console.log(`Ring bootstrap example: http://localhost:${PORT}/api/rings/ring-a-1/bootstrap`);
});
