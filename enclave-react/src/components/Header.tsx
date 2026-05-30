import React from 'react';
import { Link } from 'react-router-dom';
import './header.css';

export default function Header() {
  const [theme, setTheme] = React.useState(() => (typeof window !== 'undefined' && localStorage.getItem('theme')) || 'light');
  React.useEffect(()=>{
    document.documentElement.classList.toggle('theme-dark', theme === 'dark');
    localStorage.setItem('theme', theme);
  },[theme]);

  return (
    <header className="site-header">
      <div className="container">
        <Link to="/" className="logo">
          <img src="/enclave.png" alt="Enclave logo" width={28} height={28} />
          <span>Enclave</span>
        </Link>
        <nav className="nav">
          <Link to="/docs">Docs</Link>
          <a href="https://github.com/saifmukhtar/enclave" target="_blank" rel="noopener noreferrer">GitHub</a>
          <button className="theme-toggle" onClick={()=> setTheme(t=> t === 'light' ? 'dark' : 'light') } aria-label="Toggle theme">{theme==='light' ? '🌙' : '☀️'}</button>
        </nav>
      </div>
    </header>
  );
}
