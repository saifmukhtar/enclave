import express from 'express';
import { IncomingMessage } from 'http';
import { RawData, WebSocket, WebSocketServer } from 'ws';
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

// Simple helper to load .env file
function loadEnv() {
  const envPaths = [
    path.join(__dirname, '..', '.env'),
    path.join(__dirname, '.env'),
    path.join(process.cwd(), '..', '.env'),
    path.join(process.cwd(), '.env'),
  ];
  for (const envPath of envPaths) {
    if (fs.existsSync(envPath)) {
      try {
        const content = fs.readFileSync(envPath, 'utf8');
        for (const line of content.split('\n')) {
          const trimmed = line.trim();
          if (!trimmed || trimmed.startsWith('#')) continue;
          const index = trimmed.indexOf('=');
          if (index > 0) {
            const key = trimmed.substring(0, index).trim();
            let value = trimmed.substring(index + 1).trim();
            if (value.startsWith('"') && value.endsWith('"')) {
              value = value.substring(1, value.length - 1);
            } else if (value.startsWith("'") && value.endsWith("'")) {
              value = value.substring(1, value.length - 1);
            }
            if (!process.env[key]) {
              process.env[key] = value;
            }
          }
        }
        console.log(`Loaded environment from: ${envPath}`);
        break;
      } catch (e) {
        console.warn(`Failed to read env file at ${envPath}:`, e);
      }
    }
  }
}
loadEnv();

// Map SERVICE_ROLE_KEY or ANON_KEY to SUPABASE_KEY if needed
if (!process.env.SUPABASE_KEY) {
  process.env.SUPABASE_KEY = process.env.SERVICE_ROLE_KEY || process.env.ANON_KEY;
}

// Removed firebase-admin initialization

const SUPABASE_URL = process.env.SUPABASE_URL ?? 'https://your-project.supabase.co';
const SUPABASE_KEY = process.env.SERVICE_ROLE_KEY ?? process.env.SUPABASE_KEY ?? process.env.ANON_KEY ?? 'your-anon-key';

if (process.env.SERVICE_ROLE_KEY && SUPABASE_KEY === process.env.SERVICE_ROLE_KEY) {
  console.log("Supabase API successfully initialized using SERVICE_ROLE_KEY (RLS bypass enabled).");
} else if (process.env.ANON_KEY && SUPABASE_KEY === process.env.ANON_KEY) {
  console.warn("Supabase API initialized using ANON_KEY (may fail push token lookup due to RLS).");
} else {
  console.log("Supabase API successfully initialized with provided SUPABASE_KEY.");
}

async function fetchTargetPushToken(targetId: string): Promise<string | null> {
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${targetId}&select=push_token,fcm_token`, {
      headers: {
        'apikey': SUPABASE_KEY,
        'Authorization': `Bearer ${SUPABASE_KEY}`
      }
    });
    if (!res.ok) {
      console.warn(`Supabase token lookup returned status ${res.status}`);
      return null;
    }
    const data = await res.json() as { push_token?: string }[];
    return data[0]?.push_token ?? null;
  } catch (err) {
    console.error(`Failed to fetch push token for ${targetId}:`, err);
    return null;
  }
}

async function setOfflineInSupabase(userId: string): Promise<void> {
  if (!userId || userId.startsWith('session:')) return;
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${userId}`, {
      method: 'PATCH',
      headers: {
        'apikey': SUPABASE_KEY,
        'Authorization': `Bearer ${SUPABASE_KEY}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=minimal'
      },
      body: JSON.stringify({
        is_online: false,
        last_seen: new Date().toISOString()
      })
    });
    if (!res.ok) {
      console.warn(`[Supabase] Offline update for user ${userId} returned status ${res.status}`);
    } else {
      console.log(`[Supabase] Marked user ${userId} as offline successfully`);
    }
  } catch (err) {
    console.error(`[Supabase] Failed to update offline status for user ${userId}:`, err);
  }
}

async function sendPushNotification(targetToken: string, payload: Record<string, string>) {
  const ntfyUrl = process.env.NTFY_SERVER_URL;
  const ntfyUser = process.env.NTFY_USERNAME;
  const ntfyPass = process.env.NTFY_PASSWORD;

  if (!ntfyUrl) {
    console.log(`[MOCK NTFY] Dispatched push to token ${targetToken} with payload:`, payload);
    return;
  }

  try {
    const headers: Record<string, string> = {
      'Title': 'New Message',
      'Priority': 'high',
      'Tags': 'speech_balloon'
    };
    if (ntfyUser && ntfyPass) {
      headers['Authorization'] = 'Basic ' + Buffer.from(`${ntfyUser}:${ntfyPass}`).toString('base64');
    }

    const res = await fetch(`${ntfyUrl}/${targetToken}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      console.log(`Successfully sent high-priority NTFY push to topic ${targetToken}`);
    } else {
      console.error(`Failed to send NTFY push: ${res.statusText}`);
    }
  } catch (err) {
    console.error("Failed to send NTFY push:", err);
  }
}

type ClientId = string;

type RegisterMessage = {
  type: 'REGISTER';
  senderId: ClientId;
  targetId?: ClientId;
  payload?: unknown;
};

type SignalMessage = {
  type: string;
  senderId: ClientId;
  targetId: ClientId;
  payload?: unknown;
  contentType?: string;
};

type InboundMessage = RegisterMessage | SignalMessage;

type ClientRecord = {
  ws: WebSocket;
  id: ClientId;
  sessionId: string;
  registeredAt: number;
  lastSeenAt: number;
  remoteAddress: string;
  token: string | null;
};

type OfflineQueueItem = {
  message: unknown;
  serverTs: number;
};

type OfflineQueue = {
  items: OfflineQueueItem[];
  lastUpdatedAt: number;
};

const PORT = Number(process.env.PORT ?? 8085);
const MAX_WS_PAYLOAD_BYTES = Number(process.env.MAX_WS_PAYLOAD_BYTES ?? 1024 * 1024 * 20); // 20 MB
const HEARTBEAT_INTERVAL_MS = Number(process.env.HEARTBEAT_INTERVAL_MS ?? 30000);
const MAX_CLIENTS = Number(process.env.MAX_CLIENTS ?? 2);

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '1mb' }));

const server = http.createServer(app);
const wss = new WebSocketServer({
  server,
  maxPayload: MAX_WS_PAYLOAD_BYTES,
  perMessageDeflate: false,
});

const clientsById = new Map<ClientId, ClientRecord>();
const clientsBySocket = new Map<WebSocket, ClientRecord>();
const offlineQueues = new Map<ClientId, OfflineQueue>();

const json = (data: unknown): string => JSON.stringify(data);

const isNonEmptyString = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;

function parseInboundMessage(raw: RawData): any {
  const parsed = JSON.parse(raw.toString()) as any;

  if (!isNonEmptyString(parsed?.type)) {
    throw new Error('Message type must be a non-empty string');
  }

  if (parsed.type === 'REGISTER') {
    if (!isNonEmptyString(parsed.senderId)) {
      throw new Error('Invalid REGISTER: senderId is required');
    }
    return { type: 'REGISTER', senderId: parsed.senderId.trim() };
  }

  if (parsed.type === 'PONG') {
    if (!isNonEmptyString(parsed.senderId)) {
      throw new Error('Invalid PONG: senderId is required');
    }
    return { type: 'PONG', senderId: parsed.senderId.trim(), payload: parsed.payload };
  }

  // For all other message types (SIGNAL_PAYLOAD, WEBRTC_OFFER, WEBRTC_ANSWER,
  // ICE_CANDIDATE, WEBRTC_HANGUP, TYPING_STATUS, READ_RECEIPT, PROFILE_UPDATE,
  // KISS_*, LOUNGE_*, MUSIC_SYNC, etc.), just validate senderId + targetId.
  if (!isNonEmptyString(parsed.senderId)) {
    throw new Error('Invalid message: senderId is required');
  }
  if (!isNonEmptyString(parsed.targetId)) {
    throw new Error('Invalid message: targetId is required');
  }

  return {
    type: parsed.type.trim(),
    senderId: parsed.senderId.trim(),
    targetId: parsed.targetId.trim(),
    payload: parsed.payload,
    contentType: parsed.contentType,
    messageId: parsed.messageId ?? null,  // FIX BUG-9: forward messageId so delivery receipts work
  };
}

function safeSend(ws: WebSocket, payload: unknown): boolean {
  if (ws.readyState !== WebSocket.OPEN) {
    return false;
  }
  try {
    ws.send(json(payload));
    return true;
  } catch (err) {
    console.error('[safeSend] Failed to send frame:', (err as Error).message);
    return false;
  }
}

function closeAndCleanup(record: ClientRecord, code = 1000, reason = 'normal closure'): void {
  const mapped = clientsById.get(record.id);
  if (mapped?.sessionId === record.sessionId) {
    // Notify other registered clients that this user went offline BEFORE deleting from maps
    for (const [otherId, otherRecord] of clientsById.entries()) {
      if (otherId !== record.id && otherRecord.ws.readyState === WebSocket.OPEN) {
        safeSend(otherRecord.ws, {
          type: 'PROFILE_UPDATE',
          senderId: record.id,
          targetId: otherId,
          payload: JSON.stringify({
            username: '',
            displayName: '',
            bio: '',
            avatarUrl: '',
            isOnline: false,
            lastSeen: Date.now(),
          }),
        });
      }
    }
    setOfflineInSupabase(record.id).catch(err => console.error("Failed to update offline status in Supabase", err));
    clientsById.delete(record.id);
  }

  clientsBySocket.delete(record.ws);

  if (record.ws.readyState === WebSocket.OPEN || record.ws.readyState === WebSocket.CONNECTING) {
    try {
      record.ws.close(code, reason);
    } catch (_) {}
  }
}

function registerClient(record: ClientRecord, clientId: string): void {
  const existing = clientsById.get(clientId);

  if (existing && existing.sessionId !== record.sessionId) {
    safeSend(existing.ws, {
      type: 'SYSTEM',
      payload: { message: 'A new session replaced this connection.' },
    });
    closeAndCleanup(existing, 4001, 'session replaced');
  }

  record.id = clientId;
  record.lastSeenAt = Date.now();
  clientsById.set(clientId, record);
}

app.get('/healthz', (_req, res) => {
  res.status(200).json({
    ok: true,
    activeClients: clientsById.size,
    maxClients: MAX_CLIENTS,
    uptimeSec: Math.round(process.uptime()),
    now: new Date().toISOString(),
  });
});

async function verifySupabaseToken(token: string): Promise<string | null> {

  try {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: {
        'apikey': SUPABASE_KEY,
        'Authorization': `Bearer ${token}`
      }
    });
    if (!res.ok) {
      console.warn(`Supabase JWT verification returned status ${res.status}`);
      return null;
    }
    const user = await res.json() as { id: string };
    return user?.id ?? null;
  } catch (err) {
    console.error("JWT verification error against Supabase Auth:", err);
    return null;
  }
}

wss.on('connection', (ws: WebSocket, req: IncomingMessage) => {
  try {
    if (clientsBySocket.size >= MAX_CLIENTS) {
      ws.close(1013, 'server at capacity');
      return;
    }

    const sessionId = crypto.randomUUID();
    const remoteAddress = req.socket?.remoteAddress ?? 'unknown';

    // Extract authorization token from headers or query parameters
    const authHeader = req.headers.authorization;
    const urlParams = new URL(req.url ?? '', `http://${req.headers.host}`).searchParams;
    const token = authHeader?.startsWith('Bearer ')
      ? authHeader.substring(7)
      : (urlParams.get('token') ?? null);

    const record: ClientRecord = {
      ws,
      id: `session:${sessionId}`,
      sessionId,
      registeredAt: Date.now(),
      lastSeenAt: Date.now(),
      remoteAddress,
      token,
    };

    clientsBySocket.set(ws, record);
    console.log(`[connect] session=${sessionId} ip=${remoteAddress} active=${clientsBySocket.size}`);

    safeSend(ws, {
      type: 'HELLO',
      payload: {
        sessionId,
        maxClients: MAX_CLIENTS,
        heartbeatIntervalMs: HEARTBEAT_INTERVAL_MS,
      },
    });

  ws.on('message', async (raw: RawData) => {
    try {
      const message = parseInboundMessage(raw);
      record.lastSeenAt = Date.now();

      if (message.type === 'PONG') {
        return;
      }

      if (message.type === 'REGISTER') {
        if (!record.token) {
          console.warn(`[register] Missing auth token for connection session=${record.sessionId}`);
          safeSend(ws, {
            type: 'ERROR',
            payload: { code: 'UNAUTHORIZED', message: 'Authentication token required.' }
          });
          closeAndCleanup(record, 4002, 'unauthorized: missing token');
          return;
        }

        const verifiedUserId = await verifySupabaseToken(record.token);
        if (!verifiedUserId || verifiedUserId !== message.senderId) {
          console.warn(`[register] Auth failed. verifiedUserId=${verifiedUserId} senderId=${message.senderId}`);
          safeSend(ws, {
            type: 'ERROR',
            payload: { code: 'UNAUTHORIZED', message: 'JWT verification failed.' }
          });
          closeAndCleanup(record, 4003, 'unauthorized: invalid token');
          return;
        }

        registerClient(record, message.senderId);
        safeSend(ws, { type: 'REGISTERED', senderId: message.senderId });

        // Deliver offline queued messages for this registered senderId
        const queue = offlineQueues.get(message.senderId);
        if (queue && queue.items.length > 0) {
          console.log(`[offline-queue] Delivering ${queue.items.length} messages to registered client ${message.senderId}`);
          for (const item of queue.items) {
            safeSend(ws, item.message);
          }
          offlineQueues.delete(message.senderId);
        }
        return;
      }

      const owner = clientsById.get(message.senderId);
      if (!owner || owner.sessionId !== sessionId) {
        safeSend(ws, {
          type: 'ERROR',
          payload: { code: 'NOT_REGISTERED', message: 'Sender must register before signaling.' },
        });
        return;
      }

      if (message.targetId === message.senderId) {
        safeSend(ws, {
          type: 'ERROR',
          payload: { code: 'INVALID_TARGET', message: 'targetId must be different from senderId.' },
        });
        return;
      }

      const target = clientsById.get(message.targetId);
      const isTargetOnline = target && target.ws.readyState === WebSocket.OPEN;

      let delivered = false;
      if (isTargetOnline) {
        delivered = safeSend(target!.ws, {
          ...message,
          serverTs: Date.now(),
        });
      }

      // Messages that should be queued for offline delivery.
      // NOTE: WEBRTC_OFFER/ANSWER/ICE_CANDIDATE are intentionally excluded —
      // SDP offers are time-sensitive. Replaying a stale offer breaks ICE negotiation.
      // The caller is responsible for retrying call setup.
      const shouldQueue =
        message.type === 'SIGNAL_PAYLOAD' ||
        message.type === 'READ_RECEIPT' ||
        message.type === 'DELIVERY_RECEIPT' ||
        message.type === 'PROFILE_UPDATE' ||
        message.type === 'STORY_SHARE' ||
        message.type === 'STORY_VIEWED' ||
        message.type === 'LOUNGE_PROFILE_UPDATE' ||
        message.type === 'LOUNGE_LETTER_SEND';

      if (!delivered) {
        if (shouldQueue) {
          // Add to offline queue
          let queue = offlineQueues.get(message.targetId);
          if (!queue) {
            queue = { items: [], lastUpdatedAt: Date.now() };
            offlineQueues.set(message.targetId, queue);
          }
          queue.lastUpdatedAt = Date.now();
          if (queue.items.length < 100) {
            queue.items.push({
              message: {
                ...message,
                serverTs: Date.now(),
              },
              serverTs: Date.now(),
            });
            console.log(`[offline-queue] Queued ${message.type} for offline target ${message.targetId} (queue: ${queue.items.length})`);
          } else {
            console.warn(`[offline-queue] Queue full for target ${message.targetId}, dropping message`);
          }
        }

        // Push notification for E2EE signals, lounge letters, status stories, and mutual kiss triggers
        if (
          message.type === 'SIGNAL_PAYLOAD' ||
          message.type === 'LOUNGE_LETTER_SEND' ||
          message.type === 'STORY_SHARE' ||
          message.type === 'KISS_WORKFLOW_TRIGGER' ||
          message.type === 'WEBRTC_OFFER' ||
          message.type === 'OFFER'
        ) {
          fetchTargetPushToken(message.targetId).then(token => {
            if (token) {
              const pushType = (message.type === 'WEBRTC_OFFER' || message.type === 'OFFER') ? 'incoming_call' : 'sync';
              sendPushNotification(token, { action: pushType, type: message.type });
            } else {
              console.log(`No registered push token found for target ${message.targetId}`);
            }
          });
        }
      }

      safeSend(ws, {
        type: 'DELIVERY_STATUS',
        payload: {
          delivered,
          targetId: message.targetId,
          type: message.type,
        },
      });

      if (message.type === 'KISS_WORKFLOW_TRIGGER') {
        console.log(`[kiss] from=${message.senderId} to=${message.targetId}`);
      }
    } catch (error) {
      console.error(`[error] Failed to parse message from ip=${remoteAddress}: ${raw.toString()}`, error);
      const err = error instanceof Error ? error.message : 'invalid message';
      safeSend(ws, {
        type: 'ERROR',
        payload: { code: 'BAD_MESSAGE', message: err },
      });
    }
  });

  ws.on('error', (err) => {
    console.error(`[ws-error] session=${sessionId} error=${err.message}`);
  });

  ws.on('ping', () => {
    record.lastSeenAt = Date.now();
  });

  ws.on('pong', () => {
    record.lastSeenAt = Date.now();
  });

  ws.on('close', (code, reason) => {
    const current = clientsBySocket.get(ws);
    if (current) {
      closeAndCleanup(current, code, reason.toString() || 'closed');
    }
    console.log(`[disconnect] session=${sessionId} code=${code} active=${clientsBySocket.size}`);
  });
  } catch (err) {
    console.error('[connection] Unexpected error during connection setup:', (err as Error).message);
    try { ws.close(1011, 'internal server error'); } catch (_) {}
  }
});

const heartbeatTimer = setInterval(() => {
  const now = Date.now();
  // Clone clientsBySocket list to avoid concurrent modification issues during iteration
  const records = Array.from(clientsBySocket.values());
  for (const record of records) {
    if (record.ws.readyState !== WebSocket.OPEN) {
      closeAndCleanup(record, 1001, 'socket not open');
      continue;
    }

    if (now - record.lastSeenAt > HEARTBEAT_INTERVAL_MS * 2) {
      safeSend(record.ws, {
        type: 'ERROR',
        payload: { code: 'HEARTBEAT_TIMEOUT', message: 'Connection timed out due to inactivity.' },
      });
      closeAndCleanup(record, 1001, 'heartbeat timeout');
      continue;
    }

    safeSend(record.ws, { type: 'PING', payload: { ts: now } });
  }

  // Clean up expired offline messages (older than 24 hours) or inactive queues (older than 7 days)
  const expirationMs = 24 * 60 * 60 * 1000;
  const inactiveQueueMs = 7 * 24 * 60 * 60 * 1000;
  for (const [targetId, queue] of offlineQueues.entries()) {
    if (now - queue.lastUpdatedAt > inactiveQueueMs) {
      offlineQueues.delete(targetId);
      continue;
    }

    const fresh = queue.items.filter(item => now - item.serverTs < expirationMs);
    if (fresh.length === 0) {
      offlineQueues.delete(targetId);
    } else if (fresh.length < queue.items.length) {
      queue.items = fresh;
    }
  }
}, HEARTBEAT_INTERVAL_MS);

heartbeatTimer.unref();

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Enclave signaling server listening on 127.0.0.1:${PORT}`);
});

// Graceful shutdown handlers
const shutdown = () => {
  console.log('Shutting down server gracefully...');
  clearInterval(heartbeatTimer);
  server.close(() => {
    console.log('HTTP server closed.');
    process.exit(0);
  });
  // Force exit after 5 seconds if graceful close hangs
  setTimeout(() => {
    console.error('Forcing exit...');
    process.exit(1);
  }, 5000);
};

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
