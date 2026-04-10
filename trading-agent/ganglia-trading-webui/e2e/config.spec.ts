import { test, expect } from '@playwright/test';

test.describe('Config Panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/config?mock=true');
  });

  test('page loads with title', async ({ page }) => {
    await expect(page.locator('h1')).toHaveText('Configuration');
  });

  test('shows investment style options', async ({ page }) => {
    await expect(page.getByText('VALUE')).toBeVisible();
    await expect(page.getByText('GROWTH')).toBeVisible();
    await expect(page.getByText('MOMENTUM')).toBeVisible();
    await expect(page.getByText('CONTRARIAN')).toBeVisible();
  });

  test('clicking style button highlights it', async ({ page }) => {
    const growthBtn = page.locator('button', { hasText: 'GROWTH' });
    await growthBtn.click();
    // The button should now have primary styling
    await expect(growthBtn).toHaveClass(/border-primary/);
  });

  test('save and reset buttons are present', async ({ page }) => {
    await expect(page.getByText('Save')).toBeVisible();
    await expect(page.getByText('Reset')).toBeVisible();
  });

  test('memory TWR toggle is present', async ({ page }) => {
    await expect(page.getByText('Enable Memory TWR')).toBeVisible();
  });
});
