import React, { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import Header from './components/Header';
import Hero from './components/Hero';
import Footer from './components/Footer';
import DocsLayout from './components/DocsLayout';

type NavItem = {
  label: string;
  href: string;
};

type Feature = {
  title: string;
  description: string;
  icon: string;
  tags: string[];
  span?: 'default' | 'wide' | 'full';
};

type Credit = {
  title: string;
  href: string;
  description: string;
  icon: string;
};

type SetupStep = {
  label: string;
  title: string;
  description: string;
  code: string;
};

const navItems: NavItem[] = [
  { label: 'Why', href: '#motivation' },
  { label: 'Features', href: '#features' },
  { label: 'Presence Engine', href: '#presence-engine' },
  { label: 'Architecture', href: '#architecture' },
  { label: 'Setup', href: '#setup' },
  { label: 'Security', href: '#security' },
  { label: 'Credits', href: '#credits' },
  { label: 'Docs', href: '#docs' },
];

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

const presenceCards = [
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

const architectureCards = [
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
    description: 'The fastest way to deploy the entire stack (Supabase, WebRTC, Ntfy, Nginx, SSL) to a fresh Ubuntu Droplet.',
    code: `curl -fsSL https://install.enclave.saifmukhtar.dev | sudo bash`,
  },
  {
    label: 'Clone (Advanced)',
    title: 'Manual setup (Advanced)',
    description: 'Start with the repository and scaffold local configuration files manually.',
    code: `git clone https://github.com/saifmukhtar/enclave.git
cd enclave

cp enclave-ui/local.properties.example enclave-ui/local.properties
cp enclave-server/.env.example enclave-server/.env`,
  },
  {
    label: 'Backend',
    title: 'Bootstrap the backend stack',
    description: 'Bring up PostgreSQL, Supabase services, Coturn, and Ntfy.',
    code: `chmod +x setup-local.sh
./setup-local.sh`,
  },
  {
    label: 'Android',
    title: 'Configure Android variables',
    description: 'Set your SDK path, server URLs, and push credentials.',
    code: `sdk.dir=/home/username/Android/Sdk
SUPABASE_URL=https://your-supabase-instance.com
SUPABASE_KEY=your-anon-key
SIGNALING_SERVER_URL=wss://wss.yourdomain.com
TURN_SERVER_URL=turn:your-vps-ip:3478
NTFY_SERVER_URL=https://ntfy.yourdomain.com
NTFY_USERNAME=your-ntfy-user
NTFY_PASSWORD=your-ntfy-pass`,
  },
  {
    label: 'Build',
    title: 'Compile the app',
    description: 'Build debug or release variants directly from Gradle.',
    code: `cd enclave-ui
./gradlew assembleDebug

# or
./gradlew assembleRelease`,
  },
  {
    label: 'Deploy',
    title: 'Cloud deploy',
    description: 'Harden the VPS and deploy the server stack with TLS and PM2.',
    code: `cd enclave-server
./deploy_to_cloud.sh`,
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
    icon: '🎨',
  },
  {
    title: 'Docker',
    href: 'https://www.docker.com/',
    description: 'Container orchestration for the self-hosted backend stack.',
    icon: '📦',
  },
];

const docs = [
  {
    title: 'README.md',
    description: 'Feature dictionary, architecture summary, quick start guide.',
    href: '#/docs/readme',
    icon: '📖',
  },
  {
    title: 'SETUP_GUIDE.md',
    description: 'VPS deployment, Nginx, Certbot, Coturn, and PM2 configuration.',
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

const paletteLinks = [
  { name: 'Why Enclave?', target: '#motivation', icon: '🏡' },
  { name: 'Explore Core Features', target: '#features', icon: '⚡' },
  { name: 'Presence Engine', target: '#presence-engine', icon: '📳' },
  { name: 'System Architecture', target: '#architecture', icon: '📐' },
  { name: 'Setup Guide', target: '#setup', icon: '💻' },
  { name: 'Security Model', target: '#security', icon: '🔒' },
  { name: 'Open-Source Credits', target: '#credits', icon: '👥' },
  { name: 'Workspace Docs', target: '#docs', icon: '📖' },
];

function scrollToTarget(target: string) {
  const element = document.querySelector(target);
  if (!(element instanceof HTMLElement)) return;
  element.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('hero');
  const [activeSetupIndex, setActiveSetupIndex] = useState(0);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [paletteQuery, setPaletteQuery] = useState('');
  const [toastVisible, setToastVisible] = useState(false);

  const filteredPalette = useMemo(() => {
    const query = paletteQuery.trim().toLowerCase();
    if (!query) return paletteLinks;
    return paletteLinks.filter((item) => item.name.toLowerCase().includes(query));
  }, [paletteQuery]);

  useEffect(() => {
    const nav = document.querySelector('.site-nav');
    const onScroll = () => nav?.classList.toggle('is-scrolled', window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const sections = ['hero', 'motivation', 'features', 'presence-engine', 'architecture', 'setup', 'security', 'credits', 'docs'];
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActiveSection((entry.target as HTMLElement).id);
          }
        });
      },
      { rootMargin: '-35% 0px -55% 0px', threshold: 0.01 },
    );

    sections.forEach((id) => {
      const element = document.getElementById(id);
      if (element) observer.observe(element);
    });

    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen((open) => !open);
        setPaletteQuery('');
      }
      if (event.key === 'Escape') {
        setPaletteOpen(false);
        setMenuOpen(false);
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const copySetupCode = async () => {
    try {
      await navigator.clipboard.writeText(setupSteps[activeSetupIndex].code);
      setToastVisible(true);
      window.setTimeout(() => setToastVisible(false), 1800);
    } catch {
      setToastVisible(true);
      window.setTimeout(() => setToastVisible(false), 1800);
    }
  };

  const activeSetupStep = setupSteps[activeSetupIndex];
  const location = useLocation();
  const isDocsRoute = location.pathname.startsWith('/docs');

  return (
    <div className="page">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <Header />

      {isDocsRoute ? (
        <main id="main-content" className="docs-page-shell">
          <DocsLayout />
        </main>
      ) : (
        <main id="main-content">
          <Hero />

        <section id="motivation" className="section-shell section-card">
          <div className="section-heading">
            <h2>Why Enclave Exists</h2>
            <p>
              Mainstream messaging apps centralize metadata, keys, and trust. Enclave inverts that model for a strictly
              private two-person communication space.
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
              <span className="pill" key={item}>
                {item}
              </span>
            ))}
          </div>
        </section>

        <section id="features" className="section-shell section-card">
          <div className="section-heading">
            <h2>Core Features</h2>
            <p>Responsive cards reflow from one column on mobile to a multi-column layout on larger screens.</p>
          </div>
          <div className="feature-grid">
            {features.map((feature) => (
              <article
                key={feature.title}
                className={[
                  'feature-card',
                  feature.span === 'wide' ? 'feature-card-wide' : '',
                  feature.span === 'full' ? 'feature-card-full' : '',
                ].join(' ')}
              >
                <div className="feature-icon" aria-hidden="true">
                  {feature.icon}
                </div>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
                <div className="tag-row">
                  {feature.tags.map((tag) => (
                    <span className="tag" key={tag}>
                      {tag}
                    </span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section id="presence-engine" className="section-shell section-card">
          <div className="section-heading">
            <p className="eyebrow eyebrow-inline">Signature Module</p>
            <h2>The Presence Engine</h2>
            <p>
              A multi-layered intimate interaction engine that translates touch, sound, and motion into synchronized
              sensory output.
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

          <div className="flow-card">
            <h3>Interaction Data Flow</h3>
            <div className="flow-diagram" aria-label="Presence engine data flow diagram">
              {[
                { title: 'Touch Input', detail: 'User touch or press input captured from device sensors.' },
                { title: 'Physics Mesh', detail: 'Input mapped to a soft-body physics mesh for realistic motion.' },
                { title: 'Synthesizer', detail: 'Audio/haptic synthesis converts energy maps to tactile output.' },
                { title: 'WebSocket Relay', detail: 'Realtime relay forwards synced interaction data between partners.' },
                { title: 'Haptic Engine', detail: 'Device-specific haptic motor instructions render the sensation.' },
              ].map((step, index, arr) => (
                <React.Fragment key={step.title}>
                  <div className="flow-node" tabIndex={0} aria-labelledby={`flow-${index}-title`}>
                    <span aria-hidden="true">{index + 1}</span>
                    <strong id={`flow-${index}-title`}>{step.title}</strong>
                    <div className="flow-node-detail" aria-hidden="true">
                      <div className="detail-row">
                        <svg className="detail-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden>
                          <path d="M12 2v6l4-2-4 8-4-2 4-8V2z" fill="currentColor" />
                        </svg>
                        <p className="detail-text">{step.detail} The system preserves timing and fidelity by batching updates and using low-latency transport where possible.</p>
                      </div>
                      <div className="detail-actions">
                        <a className="detail-link" href="#docs" onClick={(e) => { e.preventDefault(); scrollToTarget('#docs'); }}>
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden>
                            <path d="M14 3h7v7" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                            <path d="M10 14L21 3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                            <path d="M21 21H3V3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                          </svg>
                          <span>Learn</span>
                        </a>
                        <button
                          className="detail-copy button-secondary"
                          type="button"
                          onClick={() => {
                            try {
                              navigator.clipboard.writeText(`${step.title}: ${step.detail}`);
                              setToastVisible(true);
                              window.setTimeout(() => setToastVisible(false), 1400);
                            } catch (err) {
                              setToastVisible(true);
                              window.setTimeout(() => setToastVisible(false), 1400);
                            }
                          }}
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden>
                            <rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="1.6" />
                            <rect x="4" y="4" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="1.6" />
                          </svg>
                          <span>Copy</span>
                        </button>
                      </div>
                    </div>
                  </div>
                  {index < arr.length - 1 ? (
                    <span
                      className="flow-arrow"
                      role="img"
                      aria-label={`From ${step.title} to ${arr[index + 1].title}`}
                    >
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden>
                        <path d="M6 12h12M12 6l6 6-6 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                      <span className="visually-hidden">From {step.title} to {arr[index + 1].title}</span>
                    </span>
                  ) : null}
                </React.Fragment>
              ))}
            </div>
          </div>
        </section>

        <section id="architecture" className="section-shell section-card">
          <div className="section-heading">
            <h2>System Architecture</h2>
            <p>Every component is self-hosted. No third-party cloud infrastructure touches your data in transit or at rest.</p>
          </div>

          <div className="architecture-grid">
            {architectureCards.map((card) => (
              <article key={card.title} className="mini-card">
                <span className="mini-badge">{card.eyebrow}</span>
                <h3>{card.title}</h3>
                <p>{card.description}</p>
                <div className="tag-row">
                  {card.tags.map((tag) => (
                    <span className="tag" key={tag}>
                      {tag}
                    </span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section id="setup" className="section-shell section-card">
          <div className="section-heading">
            <h2>Setup Guide</h2>
            <p>A practical walkthrough that stays usable on mobile by collapsing into stacked panels and scrollable tabs.</p>
          </div>

          <div className="terminal-card">
            <div className="terminal-tabs" role="tablist" aria-label="Setup steps">
              {setupSteps.map((step, index) => (
                <button
                  key={step.label}
                  type="button"
                  className={index === activeSetupIndex ? 'terminal-tab is-active' : 'terminal-tab'}
                  onClick={() => setActiveSetupIndex(index)}
                >
                  {index + 1}. {step.label}
                </button>
              ))}
            </div>

            <div className="terminal-window">
              <div className="terminal-header">
                <div>
                  <strong>{activeSetupStep.title}</strong>
                  <p>{activeSetupStep.description}</p>
                </div>
                <button className="button button-secondary terminal-copy" type="button" onClick={copySetupCode}>
                  Copy
                </button>
              </div>

              <pre className="terminal-body">
                <code>{activeSetupStep.code}</code>
              </pre>
            </div>
          </div>
        </section>

        <section id="security" className="section-shell section-card">
          <div className="section-heading">
            <h2>Security Model</h2>
            <p>Authentication, pairing, local key derivation, and private notification wakes remain fully sovereign.</p>
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

        <section id="credits" className="section-shell section-card">
          <div className="section-heading">
            <h2>Open-Source Credits</h2>
            <p>The app builds on excellent open-source work across cryptography, UI, infrastructure, and media tooling.</p>
          </div>
          <div className="credits-grid">
            {credits.map((credit) => (
              <a key={credit.title} className="credit-card" href={credit.href} target="_blank" rel="noreferrer">
                <span className="credit-icon" aria-hidden="true">
                  {credit.icon}
                </span>
                <span className="credit-body">
                  <strong>{credit.title}</strong>
                  <small>{credit.description}</small>
                </span>
                <span aria-hidden="true">↗</span>
              </a>
            ))}
          </div>
        </section>

          <section id="docs" className="section-shell section-card">
            <div className="section-heading">
              <h2>Workspace Documentation</h2>
              <p>Supporting docs cover features, setup, and repository structure.</p>
            </div>

            <div className="docs-grid">
              {docs.map((doc) => (
                <a key={doc.title} className="doc-card" href={doc.href} target="_blank" rel="noreferrer">
                  <span className="doc-icon" aria-hidden="true">
                    {doc.icon}
                  </span>
                  <strong>{doc.title}</strong>
                  <small>{doc.description}</small>
                </a>
              ))}
            </div>
          </section>
        </main>
      )}

      <Footer />

      {paletteOpen ? (
        <div className="palette-backdrop" role="presentation" onClick={() => setPaletteOpen(false)}>
          <div className="palette" role="dialog" aria-modal="true" aria-label="Command palette" onClick={(e) => e.stopPropagation()}>
            <div className="palette-header">
              <input
                autoFocus
                value={paletteQuery}
                onChange={(event) => setPaletteQuery(event.target.value)}
                placeholder="Type to search sections..."
                aria-label="Search site sections"
              />
              <button className="button button-secondary" type="button" onClick={() => setPaletteOpen(false)}>
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

      {toastVisible ? <div className="toast">Copied to clipboard!</div> : null}
    </div>
  );
}
