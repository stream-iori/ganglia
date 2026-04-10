import { test, expect } from '@playwright/test';

test.describe('Pipeline Monitor', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/pipeline?mock=true');
  });

  test('shows 4 phase columns', async ({ page }) => {
    await expect(page.getByText('Perception')).toBeVisible();
    await expect(page.getByText('Research Debate')).toBeVisible();
    await expect(page.getByText('Risk Debate')).toBeVisible();
    await expect(page.getByText('Signal Extraction')).toBeVisible();
  });

  test('phases start as IDLE', async ({ page }) => {
    const idleLabels = page.locator('text=IDLE');
    await expect(idleLabels.first()).toBeVisible();
  });

  test('shows reports section after pipeline completion', async ({ page }) => {
    // Navigate to dashboard to start pipeline, then back
    await page.goto('/?mock=true');
    const tickerInput = page.locator('input[placeholder*="ticker"]');
    await tickerInput.fill('GOOG');
    await page.getByRole('button', { name: /Run Pipeline/i }).click();

    // Wait for completion
    await expect(page.locator('p.text-3xl').getByText('OVERWEIGHT')).toBeVisible({ timeout: 5000 });

    // Navigate to pipeline page
    await page.getByRole('link', { name: 'Pipeline' }).click();
    await expect(page.getByText('Reports')).toBeVisible({ timeout: 5000 });
  });
});
