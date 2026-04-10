import { test, expect } from '@playwright/test';

test.describe('Trace Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/trace?mock=true');
  });

  test('page loads with title', async ({ page }) => {
    await expect(page.locator('h1')).toHaveText('Trace');
  });

  test('shows waiting state', async ({ page }) => {
    await expect(page.getByText('Waiting for trace events...')).toBeVisible();
  });

  test('pause and clear buttons present', async ({ page }) => {
    await expect(page.getByText('Pause')).toBeVisible();
    await expect(page.getByText('Clear')).toBeVisible();
  });
});
