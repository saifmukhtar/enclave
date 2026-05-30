import { expect, test } from '@playwright/test';

test('homepage renders hero and docs cards', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Your Shared Digital Sanctuary' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'README.md' })).toHaveAttribute('href', '#/docs/readme');
  await expect(page.getByRole('button', { name: 'Toggle theme' })).toBeVisible();
});

test('docs page renders in-page markdown with permalinks', async ({ page }) => {
  await page.goto('/#/docs/readme');

  await expect(page.getByRole('heading', { name: 'Repository README' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Copy link' })).toBeVisible();
  await expect(page.locator('.docs-markdown h1').first()).toBeVisible();
});
