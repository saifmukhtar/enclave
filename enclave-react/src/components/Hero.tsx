import { useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';

// Opacity-only fade — GPU composited, no layout thrash, battery safe
const fadeIn = (delay = 0) => ({
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  transition: { duration: 0.6, ease: 'easeOut', delay },
});

const pulse = {
  animate: { opacity: [0.5, 1, 0.5] as number[] },
  transition: { duration: 4, repeat: Infinity as number, ease: 'easeInOut' as const },
};

export default function Hero() {
  const reduceMotion = useReducedMotion();
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText('curl -fsSL https://install.enclave.saifmukhtar.dev | sudo bash');
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard access denied — silently fail
    }
  };

  return (
    <section className="hero-section">
      <div className="container">
        <div className="hero-grid">

          {/* ── Left: Copy ─────────────────────────────── */}
          <motion.div className="hero-copy" {...fadeIn(0)}>
            <p className="eyebrow">Private · Self-hosted · Open source</p>
            <h1>Your Shared Digital Sanctuary</h1>
            <p className="hero-lead">
              Enclave is a private communication platform for couples. Signal-grade E2EE, WebRTC
              calls, and a self-hosted stack keep the keys, data, and trust graph under your control.
            </p>
            <div className="hero-points" aria-label="Key product highlights">
              <span className="tag">Signal-style E2EE</span>
              <span className="tag">WebRTC + TURN</span>
              <span className="tag">Android 14+</span>
              <span className="tag">AGPLv3</span>
            </div>
            <div className="hero-actions">
              <a className="btn btn-primary" href="#setup">Start Self-Hosting</a>
              <a
                className="btn btn-ghost"
                href="https://github.com/saifmukhtar/enclave"
                target="_blank"
                rel="noopener noreferrer"
              >
                View on GitHub ↗
              </a>
            </div>

            <div className="hero-install-box">
              <span className="hero-install-label">1-Click Production Deploy</span>
              <div className="hero-install-cmd">
                <code>curl -fsSL https://install.enclave.saifmukhtar.dev | sudo bash</code>
                <button
                  className="hero-install-copy"
                  onClick={handleCopy}
                  aria-label="Copy install command"
                  type="button"
                >
                  {copied ? 'Copied!' : (
                    <>
                      <span>Copy</span>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
                        <rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="2" />
                        <rect x="4" y="4" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="2" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
            </div>
          </motion.div>

          {/* ── Right: Visual Panel ─────────────────────── */}
          <motion.div className="hero-visual-wrap" aria-hidden {...fadeIn(0.12)}>
            <div className="hero-visual-panel">
              <img src="/enclave.png" alt="Enclave logo" className="hero-mini-logo" />

              <div className="hero-visual-copy">
                <span className="hero-visual-kicker">Live encrypted session</span>
                <strong>Private messages, calls, and presence in one sovereign stack</strong>
              </div>

              {/* Orbit — opacity pulse only, no rotate/translate (GPU, battery safe) */}
              <div className="hero-orbit" role="img" aria-label="Abstract orbit illustration">
                <div className="hero-orbit-grid" />

                <motion.span
                  className="hero-orbit-ring hero-orbit-ring-outer"
                  animate={reduceMotion ? {} : pulse.animate}
                  transition={reduceMotion ? {} : { ...pulse.transition, delay: 0 }}
                />
                <motion.span
                  className="hero-orbit-ring hero-orbit-ring-inner"
                  animate={reduceMotion ? {} : pulse.animate}
                  transition={reduceMotion ? {} : { ...pulse.transition, delay: 0.9 }}
                />
                <div className="hero-orbit-core">
                  <span className="hero-orbit-core-mark" />
                </div>

                {/* Signal cards — opacity fade only */}
                <motion.div
                  className="hero-signal-card hero-signal-card-top"
                  animate={reduceMotion ? {} : pulse.animate}
                  transition={reduceMotion ? {} : { ...pulse.transition, delay: 0.3 }}
                >
                  <strong>Encrypted</strong>
                  <span>Double Ratchet sessions</span>
                </motion.div>

                <motion.div
                  className="hero-signal-card hero-signal-card-right"
                  animate={reduceMotion ? {} : pulse.animate}
                  transition={reduceMotion ? {} : { ...pulse.transition, delay: 1.2 }}
                >
                  <strong>Calls</strong>
                  <span>WebRTC + TURN</span>
                </motion.div>

                <motion.div
                  className="hero-signal-card hero-signal-card-bottom"
                  animate={reduceMotion ? {} : pulse.animate}
                  transition={reduceMotion ? {} : { ...pulse.transition, delay: 0.7 }}
                >
                  <strong>Self-hosted</strong>
                  <span>Android + Supabase + Node</span>
                </motion.div>
              </div>

              {/* Stats row — always 3 columns, min-width:0 prevents overflow */}
              <div className="hero-visual-stats">
                <div>
                  <strong>2</strong>
                  <span>partners</span>
                </div>
                <div>
                  <strong>0</strong>
                  <span>cloud trust</span>
                </div>
                <div>
                  <strong>1</strong>
                  <span>sovereign stack</span>
                </div>
              </div>
            </div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
