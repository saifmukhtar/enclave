import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { docsPages, type DocsPage } from '../docs/content';
import './docs.css';

function flattenText(node: React.ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(flattenText).join('');
  if (React.isValidElement(node)) return flattenText(node.props.children);
  return '';
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function heading(level: 1 | 2 | 3 | 4 | 5 | 6) {
  return function Heading({ children }: { children?: React.ReactNode }) {
    const text = flattenText(children);
    const id = slugify(text);
    const Tag = `h${level}` as keyof JSX.IntrinsicElements;
    return (
      <Tag id={id} className="docs-heading">
        <a className="docs-anchor" href={`#${id}`} aria-label={`Permalink to ${text}`}>
          ¶
        </a>
        {children}
      </Tag>
    );
  };
}

const markdownComponents = {
  h1: heading(1),
  h2: heading(2),
  h3: heading(3),
  h4: heading(4),
  h5: heading(5),
  h6: heading(6),
};

function getCurrentSlug(pathname: string): string {
  const parts = pathname.split('/').filter(Boolean);
  return parts[1] ?? docsPages[0].slug;
}

export default function DocsLayout() {
  const [query, setQuery] = React.useState('');
  const location = useLocation();
  const navigate = useNavigate();

  const currentSlug = getCurrentSlug(location.pathname);
  const selected = docsPages.find((page) => page.slug === currentSlug) ?? docsPages[0];

  const filtered = docsPages.filter((page) => {
    const haystack = `${page.title} ${page.description}`.toLowerCase();
    return haystack.includes(query.toLowerCase());
  });

  const copyPermalink = async () => {
    await navigator.clipboard.writeText(window.location.href);
  };

  return (
    <div className="docs-root container">
      <aside className="docs-sidebar">
        <div className="docs-sidebar-head">
          <p className="eyebrow eyebrow-inline">Docs</p>
          <h2>Repository docs</h2>
          <p>Full markdown from the repository, rendered with inline permalinks.</p>
        </div>

        <input
          className="docs-search"
          placeholder="Search docs…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />

        <nav className="docs-nav" aria-label="Documentation pages">
          {filtered.map((page) => (
            <button
              key={page.key}
              className={page.slug === selected.slug ? 'docs-nav-item is-active' : 'docs-nav-item'}
              onClick={() => navigate(`/docs/${page.slug}`)}
              type="button"
            >
              <strong>{page.title}</strong>
              <small>{page.description}</small>
            </button>
          ))}
        </nav>
      </aside>

      <main className="docs-content" aria-labelledby="docs-title">
        <div className="docs-article-head">
          <p className="eyebrow eyebrow-inline">Permalink</p>
          <h1 id="docs-title">{selected.title}</h1>
          <p>{selected.description}</p>
          <div className="docs-actions">
            <button className="button button-secondary" type="button" onClick={copyPermalink}>
              Copy link
            </button>
            <a className="button button-secondary" href={`#/docs/${selected.slug}`}>
              Open permalink
            </a>
          </div>
        </div>

        <article className="docs-markdown">
          <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
            {selected.content}
          </ReactMarkdown>
        </article>
      </main>
    </div>
  );
}
