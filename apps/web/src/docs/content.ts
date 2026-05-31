import readme from '../../../README.md?raw';
import setupGuide from '../../../SETUP_GUIDE.md?raw';
import repoStructure from '../../../REPO_STRUCTURE.md?raw';

export type DocsPage = {
  key: string;
  title: string;
  slug: string;
  description: string;
  content: string;
};

export const docsPages: DocsPage[] = [
  {
    key: 'readme',
    title: 'Repository README',
    slug: 'readme',
    description: 'Project overview, architecture summary, and quick start flow.',
    content: readme,
  },
  {
    key: 'setup',
    title: 'Setup Guide',
    slug: 'setup',
    description: 'Local development and production deployment instructions.',
    content: setupGuide,
  },
  {
    key: 'structure',
    title: 'Repository Structure',
    slug: 'structure',
    description: 'Workspace map and file-by-file organization.',
    content: repoStructure,
  },
];
