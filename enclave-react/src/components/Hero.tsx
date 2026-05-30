import { motion } from 'framer-motion';
import { useReducedMotion } from 'framer-motion';
import './hero.css';

export default function Hero() {
  const reduceMotion = useReducedMotion();

  return (
    <section className="hero-section">
      <div className="hero-grid container">
        <motion.div
          className="hero-copy"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
        >
          <p className="eyebrow">Private · Self-hosted · Open source</p>
          <h1>Your Shared Digital Sanctuary</h1>
          <p className="lead">
            Enclave is a private communication platform for couples. Signal-grade E2EE, WebRTC calls, and a
            self-hosted stack keep the keys, data, and trust graph under your control.
          </p>
          <div className="hero-points" aria-label="Key product highlights">
            <span>Signal-style E2EE</span>
            <span>WebRTC + TURN</span>
            <span>Android 14+</span>
          </div>
          <div className="hero-actions">
            <a className="btn" href="#setup">Start Self-Hosting</a>
            <a className="btn secondary" href="https://github.com/saifmukhtar/enclave" target="_blank" rel="noopener noreferrer">View on GitHub</a>
          </div>
        </motion.div>

        <motion.div
          className="hero-visual-wrap"
          aria-hidden
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.85, ease: 'easeOut', delay: 0.08 }}
        >
          <div className="hero-visual-panel">
            <img src="/enclave.png" alt="Enclave logo" className="hero-mini-logo" />
            <div className="hero-visual-copy">
              <span className="hero-visual-kicker">Live encrypted session</span>
              <strong>Private messages, calls, and presence in one sovereign stack</strong>
            </div>
            <div className="hero-orbit" role="img" aria-label="Abstract pulse and orbit illustration">
              <div className="hero-orbit-grid" />
              <motion.span
                className="hero-orbit-ring hero-orbit-ring-outer"
                animate={reduceMotion ? {} : { rotate: 360 }}
                transition={{ duration: 60, ease: 'linear', repeat: Infinity }}
              />
              <motion.span
                className="hero-orbit-ring hero-orbit-ring-inner"
                animate={reduceMotion ? {} : { rotate: -360 }}
                transition={{ duration: 44, ease: 'linear', repeat: Infinity }}
              />
              <motion.div
                className="hero-orbit-core"
                animate={reduceMotion ? {} : { scale: [1, 1.012, 1] }}
                transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
              >
                <span className="hero-orbit-core-mark" />
              </motion.div>
              <motion.div
                className="hero-signal-card hero-signal-card-top"
                animate={reduceMotion ? {} : { y: [0, -4, 0], rotate: [-0.6, 0.6, -0.6] }}
                transition={{ duration: 9, repeat: Infinity, ease: 'easeInOut' }}
              >
                <strong>Encrypted</strong>
                <span>Double Ratchet sessions</span>
              </motion.div>
              <motion.div
                className="hero-signal-card hero-signal-card-right"
                animate={reduceMotion ? {} : { y: [0, 3, 0], rotate: [0.6, -0.6, 0.6] }}
                transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut', delay: 0.6 }}
              >
                <strong>Calls</strong>
                <span>WebRTC + TURN</span>
              </motion.div>
              <motion.div
                className="hero-signal-card hero-signal-card-bottom"
                animate={reduceMotion ? {} : { y: [0, -3, 0], rotate: [-0.6, 0.6, -0.6] }}
                transition={{ duration: 8.5, repeat: Infinity, ease: 'easeInOut', delay: 0.3 }}
              >
                <strong>Self-hosted</strong>
                <span>Android + Supabase + Node</span>
              </motion.div>
            </div>
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
                <span>self-hosted stack</span>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  )
}
