/* ═══════════════════════════════════════════════
   Enclave — script.js
   Handles: smooth scroll, nav scroll-shadow,
   mobile hamburger, scroll reveal, copy-to-clipboard
═══════════════════════════════════════════════ */

(function () {
  'use strict';

  /* ── Smooth scroll with fixed-nav offset ── */
  const NAV_HEIGHT = 72;

  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener('click', (e) => {
      const id = anchor.getAttribute('href');
      if (id === '#') return;
      const target = document.querySelector(id);
      if (!target) return;
      e.preventDefault();
      const top = target.getBoundingClientRect().top + window.scrollY - NAV_HEIGHT;
      window.scrollTo({ top, behavior: 'smooth' });
      // Close mobile nav if open
      setNavOpen(false);
    });
  });

  /* ── Nav shadow on scroll ── */
  const nav = document.getElementById('main-nav');
  const onScroll = () => {
    nav.classList.toggle('scrolled', window.scrollY > 20);
  };
  window.addEventListener('scroll', onScroll, { passive: true });

  /* ── Mobile hamburger ── */
  const hamburger = document.getElementById('nav-hamburger');
  const navLinks  = document.getElementById('nav-links');

  function setNavOpen(open) {
    hamburger.classList.toggle('open', open);
    navLinks.classList.toggle('open', open);
    hamburger.setAttribute('aria-expanded', String(open));
  }

  if (hamburger) {
    hamburger.addEventListener('click', () => {
      const isOpen = navLinks.classList.contains('open');
      setNavOpen(!isOpen);
    });
  }

  // Close nav when clicking outside
  document.addEventListener('click', (e) => {
    if (!nav.contains(e.target)) setNavOpen(false);
  });

  /* ── Scroll reveal (Intersection Observer) ── */
  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('visible');
        revealObserver.unobserve(entry.target);
      });
    },
    { threshold: 0.09, rootMargin: '0px 0px -40px 0px' }
  );

  document.querySelectorAll('.section-reveal').forEach((el) => {
    revealObserver.observe(el);
  });

  /* ── Copy to clipboard ── */
  const toast = document.getElementById('copy-toast');
  let toastTimer;

  function showToast() {
    if (toastTimer) clearTimeout(toastTimer);
    toast.classList.add('show');
    toastTimer = setTimeout(() => toast.classList.remove('show'), 2200);
  }

  document.querySelectorAll('.copy-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const targetId = btn.getAttribute('data-target');
      const codeEl   = document.getElementById(targetId);
      if (!codeEl) return;

      const text = codeEl.textContent || '';
      try {
        await navigator.clipboard.writeText(text);
        btn.textContent = '✓ Copied';
        btn.classList.add('copied');
        showToast();
        setTimeout(() => {
          btn.textContent = 'Copy';
          btn.classList.remove('copied');
        }, 2000);
      } catch {
        // Fallback for older browsers / non-HTTPS
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;opacity:0;top:0;left:0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
        btn.textContent = '✓ Copied';
        btn.classList.add('copied');
        showToast();
        setTimeout(() => {
          btn.textContent = 'Copy';
          btn.classList.remove('copied');
        }, 2000);
      }
    });
  });

  /* ── Active nav link highlight on scroll ── */
  const sections = document.querySelectorAll('section[id], header[id]');
  const navAnchors = document.querySelectorAll('.nav-links a[href^="#"]');

  const activeObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const id = entry.target.getAttribute('id');
          navAnchors.forEach((a) => {
            a.style.color = a.getAttribute('href') === `#${id}`
              ? 'var(--dark-pink)'
              : '';
          });
        }
      });
    },
    { threshold: 0.4, rootMargin: `-${NAV_HEIGHT}px 0px 0px 0px` }
  );

  sections.forEach((s) => activeObserver.observe(s));

  /* ── Command Palette dialog controller (Linear style) ── */
  const palette = document.getElementById('command-palette');
  const searchBtn = document.getElementById('nav-search-btn');
  const searchInput = document.getElementById('palette-search-input');
  const paletteItems = document.querySelectorAll('.palette-item');
  let activeIndex = 0;

  function openPalette() {
    if (!palette) return;
    palette.showModal();
    searchInput.value = '';
    filterItems('');
    setActiveItem(0);
    setTimeout(() => searchInput.focus(), 50);
  }

  function closePalette() {
    if (!palette) return;
    palette.close();
  }

  if (searchBtn && palette) {
    searchBtn.addEventListener('click', openPalette);
    palette.addEventListener('click', (e) => {
      if (e.target === palette) closePalette();
    });
    palette.addEventListener('cancel', closePalette);
  }

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      filterItems(e.target.value);
    });
  }

  function filterItems(query) {
    const q = query.toLowerCase().trim();
    let firstVisible = -1;
    paletteItems.forEach((item, index) => {
      const text = item.textContent.toLowerCase();
      if (text.includes(q)) {
        item.style.display = 'flex';
        if (firstVisible === -1) firstVisible = index;
      } else {
        item.style.display = 'none';
      }
    });
    if (firstVisible !== -1) setActiveItem(firstVisible);
  }

  function setActiveItem(index) {
    paletteItems.forEach((item, idx) => {
      item.classList.toggle('active', idx === index);
    });
    activeIndex = index;
    const activeItem = paletteItems[index];
    if (activeItem) {
      activeItem.scrollIntoView({ block: 'nearest' });
    }
  }

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      if (palette) {
        if (palette.open) {
          closePalette();
        } else {
          openPalette();
        }
      }
    }
  });

  if (palette) {
    palette.addEventListener('keydown', (e) => {
      const visibleItems = Array.from(paletteItems).filter(item => item.style.display !== 'none');
      const currentIdx = visibleItems.indexOf(paletteItems[activeIndex]);

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const nextIdx = (currentIdx + 1) % visibleItems.length;
        const targetItem = visibleItems[nextIdx];
        const actualIndex = Array.from(paletteItems).indexOf(targetItem);
        setActiveItem(actualIndex);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const prevIdx = (currentIdx - 1 + visibleItems.length) % visibleItems.length;
        const targetItem = visibleItems[prevIdx];
        const actualIndex = Array.from(paletteItems).indexOf(targetItem);
        setActiveItem(actualIndex);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        const activeItem = paletteItems[activeIndex];
        if (activeItem) {
          activeItem.click();
          closePalette();
        }
      }
    });
  }

  paletteItems.forEach((item) => {
    item.addEventListener('click', () => {
      const action = item.getAttribute('data-action');
      if (action === 'scroll') {
        const targetId = item.getAttribute('data-target');
        const target = document.querySelector(targetId);
        if (target) {
          closePalette();
          const top = target.getBoundingClientRect().top + window.scrollY - NAV_HEIGHT;
          window.scrollTo({ top, behavior: 'smooth' });
        }
      } else if (action === 'copy') {
        const targetId = item.getAttribute('data-target');
        const copyBtn = document.getElementById(targetId);
        if (copyBtn) {
          closePalette();
          copyBtn.click();
        }
      }
    });
  });

  /* ── macOS Setup Terminal switcher (Stripe style) ── */
  const tabBtns = document.querySelectorAll('.terminal-tab-btn');
  const tabContents = document.querySelectorAll('.terminal-tab-content');
  const termTitle = document.querySelector('.terminal-title');
  const termCopyBtn = document.getElementById('terminal-copy-all');

  const pathPrompts = {
    1: 'saif@enclave-vps: ~',
    2: 'saif@enclave-vps: ~/enclave',
    3: 'saif@enclave-vps: ~/enclave/enclave-ui',
    4: 'saif@enclave-vps: ~/enclave/enclave-ui',
    5: 'saif@enclave-vps: ~/enclave/enclave-server'
  };

  tabBtns.forEach((btn, idx) => {
    btn.addEventListener('click', () => {
      tabBtns.forEach(b => b.classList.remove('active'));
      tabBtns.forEach(b => b.setAttribute('aria-selected', 'false'));
      tabContents.forEach(c => {
        c.style.display = 'none';
        c.classList.remove('active');
      });

      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
      
      const contentId = btn.getAttribute('aria-controls');
      const targetContent = document.getElementById(contentId);
      if (targetContent) {
        targetContent.style.display = 'block';
        targetContent.classList.add('active');
      }

      if (termTitle) {
        termTitle.textContent = pathPrompts[idx + 1] || 'saif@enclave-vps: ~';
      }
    });
  });

  if (termCopyBtn) {
    termCopyBtn.addEventListener('click', async () => {
      const activeTab = document.querySelector('.terminal-tab-content.active');
      if (!activeTab) return;
      const codeEl = activeTab.querySelector('code');
      if (!codeEl) return;
      const text = codeEl.textContent || '';
      try {
        await navigator.clipboard.writeText(text);
        termCopyBtn.textContent = '✓ Copied';
        setTimeout(() => { termCopyBtn.textContent = 'Copy Tab'; }, 2000);
      } catch {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;opacity:0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
        termCopyBtn.textContent = '✓ Copied';
        setTimeout(() => { termCopyBtn.textContent = 'Copy Tab'; }, 2000);
      }
    });
  }

  /* ── Cryptographic Specifications Explorer (Signal style) ── */
  const specTabs = document.querySelectorAll('.spec-tab');
  const specPanels = document.querySelectorAll('.spec-panel');

  specTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      specTabs.forEach(t => t.classList.remove('active'));
      specTabs.forEach(t => t.setAttribute('aria-selected', 'false'));
      specPanels.forEach(p => p.style.display = 'none');

      tab.classList.add('active');
      tab.setAttribute('aria-selected', 'true');
      
      const panelId = tab.getAttribute('aria-controls');
      const targetPanel = document.getElementById(panelId);
      if (targetPanel) {
        targetPanel.style.display = 'block';
      }
    });
  });

})();
