import React, { useEffect, useMemo, useState, useRef } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import Header from './components/Header';
import Hero from './components/Hero';
import Footer from './components/Footer';
import DocsLayout from './components/DocsLayout';
import { config } from './config';

// ── Data Types ──────────────────────────────────────────

type Feature = {
  title: string;
  description: string;
  icon: string;
  tags: string[];
  span?: 'default' | 'wide' | 'full';
};

type PresenceCard = {
  title: string;
  description: string;
  bullets: string[];
};

type ArchCard = {
  eyebrow: string;
  title: string;
  description: string;
  tags: string[];
};

type SetupStep = {
  label: string;
  title: string;
  description: string;
  code: string;
};

type Credit = {
  title: string;
  href: string;
  description: string;
  icon: string;
};

type Doc = {
  title: string;
  description: string;
  href: string;
  icon: string;
};

type PaletteLink = {
  name: string;
  target: string;
  icon: string;
};

// ── Static Data ─────────────────────────────────────────

const features: Feature[] = [
  {
    title: 'E2EE Chat',
    description:
      'On-device Double Ratchet encryption with X3DH key exchange before any message reaches the server.',
    icon: '💬',
    tags: ['Double Ratchet', 'X3DH', 'Signal Protocol'],
    span: 'wide',
  },
  {
    title: 'WebRTC Calls',
    description:
      'Peer-to-peer voice and video calls routed through your self-hosted TURN server.',
    icon: '📞',
    tags: ['WebRTC', 'Coturn', 'SRTP'],
    span: 'wide',
  },
  {
    title: 'Shared Canvas',
    description:
      'A live whiteboard with throttled WebSocket vector sync for fluid collaborative drawing.',
    icon: '🎨',
    tags: ['WebSocket', '60 FPS', 'Vector Sync'],
  },
  {
    title: 'Voice Notes',
    description:
      'Encrypted voice messages stored privately in object storage, never as plain files.',
    icon: '🎙️',
    tags: ['AES-256-GCM', 'Storage'],
  },
  {
    title: 'Vault & Backups',
    description:
      'Biometric-protected encrypted vault and backup workflow with local key derivation.',
    icon: '🗃️',
    tags: ['Biometrics', 'PBKDF2', 'AES-256-GCM'],
  },
  {
    title: 'Lounge & Status',
    description:
      'Shared presence and stories powered by realtime listeners and background sync.',
    icon: '🏡',
    tags: ['Realtime', 'PostgreSQL'],
    span: 'full',
  },
];

const presenceCards: PresenceCard[] = [
  {
    title: '2D Soft-Body Physics Mesh',
    description:
      'A live physical model where touch input deforms a spring-based mesh in real time.',
    bullets: [
      'Hooke spring anchors for elastic restoration',
      'Multi-touch pressure and contact ellipse response',
      'Accelerometer-aware mesh sag behavior',
    ],
  },
  {
    title: 'ASMR Whisper Mode',
    description:
      'A low-latency WebRTC audio path configured for intimate, close-range listening.',
    bullets: [
      'Raw audio path with no AGC or noise suppression',
      'Earpiece routing for private playback',
      'Runs alongside physics without blocking',
    ],
  },
  {
    title: 'Audio-Tactile Synthesis',
    description:
      'Kinetic energy and mesh stress feed a haptic heartbeat blended from both partners.',
    bullets: [
      'Energy maps to volume and pitch',
      'Mutual press detection via telemetry',
      'Personalized haptic pulse synthesis',
    ],
  },
  {
    title: 'Discreet Privacy Signals',
    description:
      'Silent wakeups and steganographic notifications reveal nothing about the sender.',
    bullets: [
      'No sender identity in push payloads',
      'Generic notification content',
      'Private wakeups through self-hosted relay',
    ],
  },
];

const architectureCards: ArchCard[] = [
  {
    eyebrow: 'Client',
    title: 'Android Client',
    description:
      'Jetpack Compose UI with Room, WorkManager, and hardware-backed local cryptography.',
    tags: ['Kotlin', 'Compose', 'Room', 'WorkManager'],
  },
  {
    eyebrow: 'Backend',
    title: 'Supabase Stack',
    description:
      'PostgreSQL, auth, realtime listeners, and private storage behind Docker and Kong.',
    tags: ['PostgreSQL', 'Docker', 'Kong', 'RLS'],
  },
  {
    eyebrow: 'Relay',
    title: 'Signaling Server',
    description:
      'TypeScript WebSocket coordination for SDP offers, ICE candidates, and live events.',
    tags: ['Node.js', 'TypeScript', 'WebSocket'],
  },
  {
    eyebrow: 'Infra',
    title: 'Coturn + Ntfy',
    description:
      'TURN traversal plus private push wakeups for resilient device connectivity.',
    tags: ['Coturn', 'Ntfy', 'Nginx'],
  },
];

const setupSteps: SetupStep[] = [
  {
    label: '1-Click Install',
    title: 'Production Auto-Deploy',
    description:
      'The fastest way to deploy the entire stack (Supabase, WebRTC, Ntfy, Nginx, SSL) to a fresh Ubuntu Droplet.',
    code: `curl -fsSL ${config.installScriptUrl} | sudo bash`,
  },
];

const credits: Credit[] = [
  {
    title: 'Signal Android',
    href: 'https://github.com/signalapp/Signal-Android',
    description: 'E2EE session model and encrypted database patterns.',
    icon: '🔐',
  },
  {
    title: 'Libsignal',
    href: 'https://github.com/signalapp/libsignal',
    description: 'Cryptographic engine for prekeys, signatures, and session ciphers.',
    icon: '⚗️',
  },
  {
    title: 'Ntfy',
    href: 'https://github.com/binwiederhier/ntfy',
    description: 'Self-hosted private push notification server.',
    icon: '🔔',
  },
  {
    title: 'Element X Android',
    href: 'https://github.com/element-hq/element-x-android',
    description: 'Vector stroke batching and modern calling UI patterns.',
    icon: '📐',
  },
  {
    title: 'Fossify Gallery',
    href: 'https://github.com/FossifyOrg/Gallery',
    description: 'Secure vault and media picker patterns.',
    icon: '🖼️',
  },
  {
    title: 'MusicPlayer Compose',
    href: 'https://github.com/DawinderGill/MusicPlayer-JetpackCompose',
    description: 'In-memory waveform rendering and Compose media patterns.',
    icon: '🎵',
  },
  {
    title: 'Camera Samples',
    href: 'https://github.com/android/camera-samples',
    description: 'CameraX integration patterns for recording and capture.',
    icon: '📷',
  },
  {
    title: 'Supabase',
    href: 'https://github.com/supabase/supabase',
    description: 'Self-hosted auth, realtime listeners, and object storage.',
    icon: '🗄️',
  },
  {
    title: 'Coturn',
    href: 'https://github.com/coturn/coturn',
    description: 'STUN/TURN traversal behind complex NAT environments.',
    icon: '🌐',
  },
  {
    title: 'WebRTC',
    href: 'https://webrtc.org/',
    description: 'Open standard for peer-to-peer real-time communication.',
    icon: '📡',
  },
  {
    title: 'Jetpack Compose',
    href: 'https://developer.android.com/compose',
    description: 'Modern declarative UI toolkit for Android.',
    icon: '✨',
  },
  {
    title: 'Docker',
    href: 'https://www.docker.com/',
    description: 'Container orchestration for the self-hosted backend stack.',
    icon: '📦',
  },
];

const docs: Doc[] = [
  {
    title: 'README.md',
    description: 'Feature dictionary, architecture summary, quick start guide.',
    href: '#/docs/readme',
    icon: '📖',
  },
  {
    title: 'SETUP_GUIDE.md',
    description: 'Local development setup, prerequisites, and build steps.',
    href: '#/docs/setup',
    icon: '⚙️',
  },
  {
    title: 'REPO_STRUCTURE.md',
    description: 'Full architecture map and workflow patterns.',
    href: '#/docs/structure',
    icon: '🗂️',
  },
];

const paletteLinks: PaletteLink[] = [
  { name: 'Why Enclave?', target: '#motivation', icon: '🏡' },
  { name: 'Explore Core Features', target: '#features', icon: '⚡' },
  { name: 'Presence Engine', target: '#presence-engine', icon: '📳' },
  { name: 'System Architecture', target: '#architecture', icon: '📐' },
  { name: 'Setup Guide', target: '#setup', icon: '💻' },
  { name: 'Security Model', target: '#security', icon: '🔒' },
  { name: 'Open-Source Credits', target: '#credits', icon: '👥' },
  { name: 'Workspace Docs', target: '#docs', icon: '📖' },
];

const flowSteps = [
  { title: 'Touch Input', detail: 'User touch or press input captured from device sensors.' },
  { title: 'Physics Mesh', detail: 'Input mapped to a soft-body physics mesh for realistic motion.' },
  { title: 'Synthesizer', detail: 'Audio/haptic synthesis converts energy maps to tactile output.' },
  { title: 'WebSocket Relay', detail: 'Realtime relay forwards synced interaction data between partners.' },
  { title: 'Haptic Engine', detail: 'Device-specific haptic motor instructions render the sensation.' },
];

// ── Utility ─────────────────────────────────────────────

function scrollToTarget(target: string) {
  const element = document.querySelector(target);
  if (!(element instanceof HTMLElement)) return;
  element.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ── Home Page ────────────────────────────────────────────

function HomePage() {
  const [activeSetupIndex, setActiveSetupIndex] = useState(0);
  const [activeFlowIndex, setActiveFlowIndex] = useState<number | null>(0);
  const [toastVisible, setToastVisible] = useState(false);
  const [toastMsg, setToastMsg] = useState('Copied!');
  const [terminalCopied, setTerminalCopied] = useState(false);

  const toastTimeoutRef = useRef<number | null>(null);

  const showToast = (msg = 'Copied!') => {
    if (toastTimeoutRef.current) {
      window.clearTimeout(toastTimeoutRef.current);
    }
    setToastMsg(msg);
    setToastVisible(true);
    toastTimeoutRef.current = window.setTimeout(() => {
      setToastVisible(false);
      toastTimeoutRef.current = null;
    }, 1800);
  };

  const copySetupCode = async () => {
    try {
      await navigator.clipboard.writeText(setupSteps[activeSetupIndex].code);
      setTerminalCopied(true);
      setTimeout(() => setTerminalCopied(false), 2000);
      showToast('Copied to clipboard!');
    } catch {
      showToast('Copied!');
    }
  };

  const activeSetupStep = setupSteps[activeSetupIndex];

  return (
    <main id="main-content">
      <Hero />

      {/* ── Why Enclave ─────────────────────────────── */}
      <section id="motivation" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Why Enclave</p>
          <h2>Why Enclave Exists</h2>
          <p>
            Mainstream messaging apps centralize metadata, keys, and trust. Enclave inverts that
            model for a strictly private two-person communication space.
          </p>
        </div>
        <div className="pill-grid">
          {[
            'Signal-grade Double Ratchet + X3DH',
            'Self-hosted Supabase backend',
            'Dedicated WebSocket signaling server',
            'Coturn STUN/TURN for resilient calls',
            'Private push notifications',
            'Android 14+ hardware-backed client',
            'Biometric Keystore protection',
            'AGPLv3 open source',
          ].map((item) => (
            <span className="pill" key={item}>{item}</span>
          ))}
        </div>
      </section>

      {/* ── Core Features ────────────────────────────── */}
      <section id="features" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Features</p>
          <h2>Core Features</h2>
          <p>
            Every feature is built around the principle that your private life should stay
            private — encrypted on-device before anything touches the network.
          </p>
        </div>
        <div className="feature-grid">
          {features.map((feature) => (
            <article
              key={feature.title}
              className={[
                'feature-card',
                feature.span === 'wide' ? 'feature-card-wide' : '',
                feature.span === 'full' ? 'feature-card-full' : '',
              ].join(' ').trim()}
            >
              <div className="feature-icon" aria-hidden="true">
                {feature.icon}
              </div>
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
              <div className="tag-row">
                {feature.tags.map((tag) => (
                  <span className="tag" key={tag}>{tag}</span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* ── Presence Engine ──────────────────────────── */}
      <section id="presence-engine" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Signature Module</p>
          <h2>The Presence Engine</h2>
          <p>
            A multi-layered intimate interaction engine that translates touch, sound, and
            motion into synchronized sensory output.
          </p>
        </div>

        <div className="presence-grid">
          {presenceCards.map((card) => (
            <article key={card.title} className="presence-card">
              <h3>{card.title}</h3>
              <p>{card.description}</p>
              <ul>
                {card.bullets.map((bullet) => (
                  <li key={bullet}>{bullet}</li>
                ))}
              </ul>
            </article>
          ))}
        </div>

        {/* Interaction data flow */}
        <div className="flow-card">
          <div className="flow-card-header">
            <h3>Interaction Data Flow</h3>
            <small>Tap a step for details</small>
          </div>
          <div className="flow-steps-track" role="tablist" aria-label="Data flow steps">
            {flowSteps.map((step, index) => {
              const isActive = index === activeFlowIndex;
              const isDone = activeFlowIndex !== null && index < activeFlowIndex;

              return (
                <button
                  key={step.title}
                  type="button"
                  role="tab"
                  aria-selected={isActive}
                  className={`flow-step ${isActive ? 'is-active' : ''} ${isDone ? 'is-done' : ''}`}
                  onClick={() => setActiveFlowIndex(index)}
                >
                  <span className="flow-step-num">{index + 1}</span>
                  <span className="flow-step-label">{step.title}</span>
                </button>
              );
            })}
          </div>

          {activeFlowIndex !== null && (
            <div className="flow-detail-panel" role="tabpanel">
              <span className="flow-detail-title">{flowSteps[activeFlowIndex].title}</span>
              <p className="flow-detail-body">
                {flowSteps[activeFlowIndex].detail} The system preserves timing and fidelity by batching
                updates and using low-latency transport where possible.
              </p>
              <div className="flow-detail-actions">
                <a
                  className="detail-link"
                  href="#docs"
                  onClick={(e) => {
                    e.preventDefault();
                    scrollToTarget('#docs');
                  }}
                >
                  Learn more ↗
                </a>
                <button
                  className="detail-copy"
                  type="button"
                  onClick={() => {
                    try {
                      navigator.clipboard.writeText(`${flowSteps[activeFlowIndex!].title}: ${flowSteps[activeFlowIndex!].detail}`);
                      showToast('Copied!');
                    } catch {
                      showToast('Copied!');
                    }
                  }}
                >
                  Copy step
                </button>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* ── System Architecture ───────────────────────── */}
      <section id="architecture" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Architecture</p>
          <h2>System Architecture</h2>
          <p>
            Every component is self-hosted. No third-party cloud infrastructure touches your
            data in transit or at rest.
          </p>
        </div>
        <div className="architecture-grid">
          {architectureCards.map((card) => (
            <article key={card.title} className="mini-card">
              <span className="mini-badge">{card.eyebrow}</span>
              <h3>{card.title}</h3>
              <p>{card.description}</p>
              <div className="tag-row">
                {card.tags.map((tag) => (
                  <span className="tag" key={tag}>{tag}</span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* ── Setup Guide ───────────────────────────────── */}
      <section id="setup" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Setup</p>
          <h2>Setup Guide</h2>
          <p>
            Run a single command to deploy the complete stack on a fresh Ubuntu server.
            For advanced manual deployment, <a href="#/docs/readme">see the README</a>.
          </p>
        </div>

        <div className="terminal-card">
          {/* Tab bar — only shown when there are multiple setup steps */}
          {setupSteps.length > 1 && (
            <div className="terminal-tabs" role="tablist" aria-label="Setup steps">
              {setupSteps.map((step, index) => (
                <button
                  key={step.label}
                  type="button"
                  role="tab"
                  aria-selected={index === activeSetupIndex}
                  className={index === activeSetupIndex ? 'terminal-tab is-active' : 'terminal-tab'}
                  onClick={() => setActiveSetupIndex(index)}
                >
                  {step.label}
                </button>
              ))}
            </div>
          )}

          {/* Terminal window */}
          <div className="terminal-window">
            <div className="terminal-header">
              <div>
                <strong>{activeSetupStep.title}</strong>
                <p>{activeSetupStep.description}</p>
              </div>
              <button
                className="btn button-secondary terminal-copy"
                type="button"
                onClick={copySetupCode}
              >
                {terminalCopied ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <pre className="terminal-body">
              <code>{activeSetupStep.code}</code>
            </pre>
          </div>
        </div>
      </section>

      {/* ── Security Model ────────────────────────────── */}
      <section id="security" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Security</p>
          <h2>Security Model</h2>
          <p>
            Authentication, pairing, local key derivation, and private notification wakes
            remain fully sovereign.
          </p>
        </div>
        <div className="security-grid">
          {[
            {
              title: 'Client-side email whitelist',
              description: 'Registration is restricted to a pre-approved identity list.',
            },
            {
              title: 'Deterministic peer pairing',
              description: 'Pairing logic is fixed client-side for a no-discovery trust model.',
            },
            {
              title: 'Permanent lockdown',
              description: 'Disable signups after provisioning both partners.',
            },
            {
              title: 'Zero-knowledge keys',
              description: 'Keys derive locally and never leave the device.',
            },
          ].map((card) => (
            <article key={card.title} className="mini-card">
              <h3>{card.title}</h3>
              <p>{card.description}</p>
            </article>
          ))}
        </div>
      </section>

      {/* ── Credits ───────────────────────────────────── */}
      <section id="credits" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Acknowledgements</p>
          <h2>Open-Source Credits</h2>
          <p>
            The app builds on excellent open-source work across cryptography, UI,
            infrastructure, and media tooling.
          </p>
        </div>
        <div className="credits-grid">
          {credits.map((credit) => (
            <a
              key={credit.title}
              className="credit-card"
              href={credit.href}
              target="_blank"
              rel="noreferrer"
            >
              <span className="credit-icon" aria-hidden="true">{credit.icon}</span>
              <span className="credit-body">
                <strong>{credit.title}</strong>
                <small>{credit.description}</small>
              </span>
              <span aria-hidden="true">↗</span>
            </a>
          ))}
        </div>
      </section>

      {/* ── Docs ──────────────────────────────────────── */}
      <section id="docs" className="section-shell section-card">
        <div className="section-heading">
          <p className="eyebrow">Documentation</p>
          <h2>Workspace Documentation</h2>
          <p>Supporting docs cover features, setup, and repository structure.</p>
        </div>
        <div className="docs-grid">
          {docs.map((doc) => (
            <a key={doc.title} className="doc-card" href={doc.href}>
              <span className="doc-icon" aria-hidden="true">{doc.icon}</span>
              <strong>{doc.title}</strong>
              <small>{doc.description}</small>
            </a>
          ))}
        </div>
      </section>

      {/* ── Toast notification ──────────────────────────── */}
      <div className={`toast ${toastVisible ? 'is-visible' : ''}`} role="status" aria-live="polite">
        {toastMsg}
      </div>
    </main>
  );
}

// ── App Component ────────────────────────────────────────

export default function App() {
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [paletteQuery, setPaletteQuery] = useState('');

  const filteredPalette = useMemo(() => {
    const q = paletteQuery.trim().toLowerCase();
    if (!q) return paletteLinks;
    return paletteLinks.filter((item) => item.name.toLowerCase().includes(q));
  }, [paletteQuery]);

  // Sticky header shadow on scroll
  useEffect(() => {
    const header = document.getElementById('site-header');
    const onScroll = () => header?.classList.toggle('is-scrolled', window.scrollY > 4);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Keyboard palette toggle
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen((open) => !open);
        setPaletteQuery('');
      }
      if (event.key === 'Escape') {
        setPaletteOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  // Body scroll locking when command palette is open
  useEffect(() => {
    document.body.style.overflow = paletteOpen ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [paletteOpen]);

  return (
    <div className="page">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <Header />

      {/*
       * CRITICAL BUG FIX:
       * Previously, App used `useLocation().pathname.startsWith('/docs')` to detect the docs route.
       * But the app uses HashRouter, where `location.pathname` is always '/' — the route lives in
       * the hash (e.g. /#/docs/readme). This means the docs route NEVER activated.
       *
       * Fix: Use <Routes> / <Route> which work correctly with HashRouter.
       * react-router-dom's HashRouter + Routes matches on the hash portion automatically.
       */}
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route
          path="/docs"
          element={
            <main id="main-content">
              <DocsLayout />
            </main>
          }
        />
        <Route
          path="/docs/:slug"
          element={
            <main id="main-content">
              <DocsLayout />
            </main>
          }
        />
        {/* Catch-all → home */}
        <Route path="*" element={<HomePage />} />
      </Routes>

      <Footer />

      {/* ── Command Palette ─────────────────────────────── */}
      {paletteOpen ? (
        <div
          className="palette-backdrop"
          role="presentation"
          onClick={() => setPaletteOpen(false)}
        >
          <div
            className="palette"
            role="dialog"
            aria-modal="true"
            aria-label="Command palette"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="palette-header">
              <input
                autoFocus
                value={paletteQuery}
                onChange={(event) => setPaletteQuery(event.target.value)}
                placeholder="Type to search sections…"
                aria-label="Search site sections"
              />
              <button className="btn btn-ghost" type="button" onClick={() => setPaletteOpen(false)}>
                Close
              </button>
            </div>
            <div className="palette-list">
              {filteredPalette.map((item) => (
                <button
                  key={item.target}
                  type="button"
                  className="palette-item"
                  onClick={() => {
                    scrollToTarget(item.target);
                    setPaletteOpen(false);
                  }}
                >
                  <span>{item.icon}</span>
                  <strong>{item.name}</strong>
                </button>
              ))}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
