'use strict';

const express = require('express');
const cors    = require('cors');
const Redis   = require('ioredis');
const fetch   = require('node-fetch');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app  = express();

// ── Port ──────────────────────────────────────────────────────────────────
// App Platform автоматаар PORT env var тохируулдаг → өөрчлөх хэрэггүй
const PORT = parseInt(process.env.PORT || '8080');

// ── Backend URLs ──────────────────────────────────────────────────────────
// DO App Platform → api-gateway app → Settings → Environment Variables дотор:
//   JSON_SERVICE_URL  = https://soa-json-service-7kfhj.ondigitalocean.app
//   SOAP_SERVICE_URL  = https://soa-soap-service-zdwzn.ondigitalocean.app
//   FILE_SERVICE_URL  = https://soa-file-manager-service-8hsqq.ondigitalocean.app
//
// ⚠️  Эдгээр URL нь танай deployed сервисүүдийнх — өөрчлөх хэрэггүй
//     (одоогоор дээрх default утга хэрэглэгдэнэ)
const JSON_URL = (process.env.JSON_SERVICE_URL  || 'https://soa-json-service-7kfhj.ondigitalocean.app').replace(/\/$/, '');
const SOAP_URL = (process.env.SOAP_SERVICE_URL  || 'https://soa-soap-service-zdwzn.ondigitalocean.app').replace(/\/$/, '');
const FILE_URL = (process.env.FILE_SERVICE_URL  || 'https://soa-file-manager-service-8hsqq.ondigitalocean.app').replace(/\/$/, '');

// Кэш хадгалах хугацаа (секунд)
const CACHE_TTL = parseInt(process.env.CACHE_TTL || '60');

// ── Redis холболт ──────────────────────────────────────────────────────────
// DO App Platform → api-gateway app → Settings → Environment Variables дотор:
//
//   Арга 1 (хялбар): DO Managed Redis database-г app-д холбоход
//     DO Dashboard → Databases → танай Redis → Settings → Trusted Sources
//     → "Add App" сонгож api-gateway-г нэм → тэгвэл DO автоматаар
//     REDIS_URL env var тохируулна. Тиймд доорх код REDIS_URL-г ашиглана.
//
//   Арга 2 (гараар): тусдаа тохируулах бол:
//     REDIS_HOST     = db-redis-sgp1-xxxxx.db.ondigitalocean.com  ← DO-оос авна
//     REDIS_PORT     = 25061                                       ← DO-оос авна
//     REDIS_PASSWORD = xxxxxxxxxxxxxxxx                            ← DO-оос авна
let redis;
if (process.env.REDIS_URL) {
  // DO managed Redis-г app-д холбосон үед автоматаар ирдэг бүтэн URL
  // Жишээ: rediss://default:PASSWORD@host:25061
  redis = new Redis(process.env.REDIS_URL, {
    lazyConnect: true,
    maxRetriesPerRequest: 1,
    retryStrategy: (n) => (n > 2 ? null : 500),
  });
} else {
  redis = new Redis({
    host:     process.env.REDIS_HOST     || '127.0.0.1',
    port:     parseInt(process.env.REDIS_PORT || '6379'),
    password: process.env.REDIS_PASSWORD || undefined,
    // DO Managed Redis нь TLS шаарддаг (port 25061)
    tls:      process.env.REDIS_HOST ? {} : undefined,
    lazyConnect: true,
    maxRetriesPerRequest: 1,
    retryStrategy: (n) => (n > 2 ? null : 500),
  });
}

redis.on('connect', () => console.log('[Redis] ✓ Connected'));
redis.on('error',   (e) => console.warn('[Redis] ✗ Unavailable:', e.message));
redis.connect().catch(() => {});

const isRedisReady = () => redis.status === 'ready';

// ── CORS ───────────────────────────────────────────────────────────────────
app.use(cors());

// ── Health ─────────────────────────────────────────────────────────────────
async function pingService(baseUrl) {
  try {
    const r = await fetch(baseUrl + '/health', { timeout: 5000 });
    return r.ok ? (await r.text()).trim() || 'OK' : `HTTP ${r.status}`;
  } catch (e) {
    return 'DOWN: ' + e.message;
  }
}

app.get('/health', async (req, res) => {
  const [jsonStatus, soapStatus, fileStatus] = await Promise.all([
    pingService(JSON_URL),
    pingService(SOAP_URL),
    pingService(FILE_URL),
  ]);
  res.json({
    status:   'API Gateway OK',
    redis:    redis.status,
    services: { json: jsonStatus, soap: soapStatus, file: fileStatus },
  });
});

// ── File upload/list — streaming proxy (multipart дамжуулна) ───────────────
// ⚠️ Body parser-аас ӨМНӨ mount хийх ёстой — эс бөгөөс multipart эвдэрнэ
app.use('/api/files', createProxyMiddleware({
  target:       FILE_URL,
  changeOrigin: true,
  pathRewrite:  { '^/api': '' },   // /api/files/upload → /files/upload
  on: {
    proxyReq: (proxyReq, req) => {
      if (req.headers.authorization) {
        proxyReq.setHeader('authorization', req.headers.authorization);
      }
    },
    error: (err, req, res) => {
      console.error('[FILE PROXY]', err.message);
      res.status(502).json({ error: 'File service error', detail: err.message });
    },
  },
}));

// ── JSON body parser — /api/soap болон /api/users дамжуулахад хэрэглэнэ ────
app.use(express.json());

// ── Generic proxy + Redis cache ────────────────────────────────────────────
// targetBase : forwarding хийх backend URL
// stripPrefix: URL-аас хасах prefix
//   /api/soap/login  → strip /api/soap → /login   (SOAP service)
//   /api/users/1     → strip /api      → /users/1  (JSON service)
function makeProxy(targetBase, stripPrefix) {
  return async (req, res) => {
    const afterStrip = req.originalUrl.startsWith(stripPrefix)
      ? req.originalUrl.slice(stripPrefix.length) || '/'
      : req.originalUrl;
    const backendUrl = targetBase + afterStrip;
    const cacheKey   = `gw:${stripPrefix}:${afterStrip}`;
    const isGet      = req.method === 'GET';

    // ── Cache lookup (зөвхөн GET) ─────────────────────────────────────────
    if (isGet && isRedisReady()) {
      try {
        const hit = await redis.get(cacheKey);
        if (hit) {
          console.log(`[CACHE HIT ] GET ${cacheKey}`);
          res.setHeader('X-Cache', 'HIT');
          res.setHeader('Content-Type', 'application/json');
          return res.send(hit);
        }
        console.log(`[CACHE MISS] GET ${cacheKey}`);
      } catch (e) { console.warn('[Redis] get error:', e.message); }
    }

    // ── Backend руу дамжуулах ─────────────────────────────────────────────
    try {
      const headers = { 'Content-Type': 'application/json' };
      if (req.headers.authorization) headers['authorization'] = req.headers.authorization;

      const opts = { method: req.method, headers };
      if (['POST', 'PUT', 'PATCH'].includes(req.method) && req.body) {
        opts.body = JSON.stringify(req.body);
      }

      const t0  = Date.now();
      const br  = await fetch(backendUrl, opts);
      const ms  = Date.now() - t0;
      const txt = await br.text();

      console.log(`[PROXY    ] ${req.method} ${backendUrl} → ${br.status} (${ms}ms)`);

      res.setHeader('X-Cache',         isGet ? 'MISS' : 'SKIP');
      res.setHeader('X-Response-Time', `${ms}ms`);
      res.setHeader('Content-Type',    br.headers.get('content-type') || 'application/json');
      res.status(br.status);

      // Амжилттай GET хариуг Redis-д хадгална
      if (isGet && br.ok && isRedisReady()) {
        redis.setEx(cacheKey, CACHE_TTL, txt).catch(() => {});
      }

      // POST/PUT/DELETE хийхэд тухайн prefix-ийн cache-г устгана (invalidation)
      if (!isGet && isRedisReady()) {
        redis.keys(`gw:${stripPrefix}:*`)
          .then(keys => {
            if (keys.length > 0) {
              redis.del(keys).then(() =>
                console.log(`[CACHE INV] Deleted ${keys.length} key(s) for ${stripPrefix}`));
            }
          }).catch(() => {});
      }

      res.send(txt);
    } catch (e) {
      console.error(`[ERROR    ] ${req.method} ${backendUrl}:`, e.message);
      res.status(502).json({ error: 'Gateway upstream error', detail: e.message });
    }
  };
}

// ── Route table ────────────────────────────────────────────────────────────
// Client хүсэлт              Gateway strip      Backend хаягдах
// ─────────────────────────  ─────────────────  ──────────────────────────
// POST /api/soap/login        /api/soap    →    POST /login      (SOAP)
// GET  /api/users/1           /api         →    GET  /users/1    (JSON)
// POST /api/users             /api         →    POST /users      (JSON)
// (file routes above handled by streaming proxy)
app.use('/api/soap',  makeProxy(SOAP_URL, '/api/soap'));
app.use('/api/users', makeProxy(JSON_URL, '/api'));

// ── Start ──────────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`\nAPI Gateway started on port ${PORT}`);
  console.log(`  /api/users/* → ${JSON_URL}`);
  console.log(`  /api/soap/*  → ${SOAP_URL}`);
  console.log(`  /api/files/* → ${FILE_URL}`);
  console.log(`  Redis: ${redis.options?.host || 'from REDIS_URL'}`);
});
