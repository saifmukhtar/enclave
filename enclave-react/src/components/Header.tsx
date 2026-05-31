import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

export default function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const drawerRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);

  // Close mobile menu when route changes
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  // Close mobile menu on outside click
  useEffect(() => {
    if (!mobileOpen) return;
    const onPointerDown = (e: PointerEvent) => {
      const target = e.target as Node;
      if (
        drawerRef.current && !drawerRef.current.contains(target) &&
        toggleRef.current && !toggleRef.current.contains(target)
      ) {
        setMobileOpen(false);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [mobileOpen]);

  // Close on Escape key
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && mobileOpen) {
        setMobileOpen(false);
        toggleRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [mobileOpen]);

  const handleGetStarted = (e: React.MouseEvent<HTMLAnchorElement | HTMLButtonElement>) => {
    e.preventDefault();
    setMobileOpen(false);
    if (location.pathname.startsWith('/docs')) {
      navigate('/');
      setTimeout(() => {
        document.getElementById('setup')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 100);
    } else {
      document.getElementById('setup')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const handleNavClick = (target: string) => {
    setMobileOpen(false);
    setTimeout(() => {
      document.querySelector(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 10);
  };

  return (
    <>
      <header className="site-header" id="site-header">
        <div className="nav-inner">
          <Link to="/" className="logo">
            <img src="/enclave.png" alt="Enclave logo" width={28} height={28} />
            <span>Enclave</span>
          </Link>

          {/* Desktop navigation */}
          <nav className="site-nav" aria-label="Main navigation">
            <Link to="/docs" className="nav-link">Docs</Link>
            <a
              className="nav-link"
              href="https://github.com/saifmukhtar/enclave"
              target="_blank"
              rel="noopener noreferrer"
            >
              GitHub ↗
            </a>
            <a
              className="btn btn-nav"
              href="#setup"
              onClick={handleGetStarted}
            >
              Get started
            </a>
          </nav>

          {/* Mobile hamburger toggle */}
          <button
            ref={toggleRef}
            className="nav-toggle"
            type="button"
            aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={mobileOpen}
            aria-controls="mobile-nav-drawer"
            onClick={() => setMobileOpen((prev) => !prev)}
          >
            <span className="nav-toggle-icon" aria-hidden="true">
              <span />
              <span />
              <span />
            </span>
          </button>
        </div>
      </header>

      {/* Mobile nav drawer — rendered outside header so it can flow below it */}
      <div
        id="mobile-nav-drawer"
        ref={drawerRef}
        className={`mobile-nav-drawer${mobileOpen ? ' is-open' : ''}`}
        aria-hidden={!mobileOpen}
        role="navigation"
        aria-label="Mobile navigation"
      >
        <Link
          to="/docs"
          className="mobile-nav-link"
          onClick={() => setMobileOpen(false)}
        >
          <span>Docs</span>
          <span aria-hidden="true">→</span>
        </Link>
        <a
          className="mobile-nav-link"
          href="https://github.com/saifmukhtar/enclave"
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => setMobileOpen(false)}
        >
          <span>GitHub</span>
          <span aria-hidden="true">↗</span>
        </a>
        <button
          className="mobile-nav-link mobile-nav-cta"
          type="button"
          onClick={handleGetStarted as React.MouseEventHandler<HTMLButtonElement>}
        >
          Get started
        </button>
      </div>
    </>
  );
}
