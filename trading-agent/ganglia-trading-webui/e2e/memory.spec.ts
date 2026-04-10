import { test, expect } from '@playwright/test';

test.describe('Memory Browser', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/memory?mock=true');
  });

  test('page loads', async ({ page }) => {
    await expect(page.locator('h1')).toHaveText('Memory Browser');
  });

  test('shows empty state initially', async ({ page }) => {
    await expect(page.getByText('No facts found')).toBeVisible();
  });

  test('shows role filter dropdown', async ({ page }) => {
    const select = page.locator('select');
    await expect(select).toBeVisible();
    await expect(select).toHaveValue('');
  });

  test('shows detail panel placeholder', async ({ page }) => {
    await expect(page.getByText('Select a fact to view details')).toBeVisible();
  });
});
