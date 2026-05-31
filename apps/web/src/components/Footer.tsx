import { config } from '../config';

export default function Footer() {
  return (
    <footer>
      <div className="footer-shell">
        {/* Brand column */}
        <div className="footer-brand">
          <div className="footer-left">
            <img src="/enclave.png" alt="Enclave logo" width={36} height={36} />
            <div>
              <strong>Enclave</strong>
              <div>© {new Date().getFullYear()} {config.authorName}</div>
            </div>
          </div>
          <p className="footer-tagline">
            Private by design. Sovereign by default.
          </p>
        </div>

        {/* Links column */}
        <nav className="footer-links" aria-label="Footer links">
          <a
            href={config.repoUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            Source
          </a>
          <a
            href={config.authorUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            Author ↗
          </a>
          <a
            href={`${config.repoUrl}/blob/main/LICENSE`}
            target="_blank"
            rel="noopener noreferrer"
          >
            AGPLv3
          </a>
        </nav>
      </div>
    </footer>
  );
}
