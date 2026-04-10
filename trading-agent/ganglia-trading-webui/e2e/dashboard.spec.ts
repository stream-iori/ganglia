import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/?mock=true');
  });

  test('page loads with title', async ({ page }) => {
    await expect(page.locator('h1')).toHaveText('Trading Dashboard');
  });

  test('signal card shows no signal initially', async ({ page }) => {
    await expect(page.getByText('Run a pipeline to generate a signal')).toBeVisible();
  });

  test('pipeline strip shows 4 phases', async ({ page }) => {
    await expect(page.getByText('Perception')).toBeVisible();
    await expect(page.getByText('Research Debate')).toBeVisible();
    await expect(page.getByText('Risk Debate')).toBeVisible();
    await expect(page.getByText('Signal Extraction')).toBeVisible();
  });

  test('run pipeline generates signal', async ({ page }) => {
    const tickerInput = page.locator('input[placeholder*="ticker"]');
    await tickerInput.fill('TSLA');

    await page.getByRole('button', { name: /Run Pipeline/i }).click();

    // Wait for the pipeline to complete (mock takes ~2.5s)
    await expect(page.locator('p.text-3xl').getByText('OVERWEIGHT')).toBeVisible({ timeout: 5000 });
  });

  test('signal history populates after run', async ({ page }) => {
    const tickerInput = page.locator('input[placeholder*="ticker"]');
    await tickerInput.fill('AAPL');
    await page.getByRole('button', { name: /Run Pipeline/i }).click();

    // Wait for signal to appear in history table
    await expect(page.getByText('Signal History')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'AAPL', exact: true })).toBeVisible({ timeout: 5000 });
  });
});
