import { test, expect } from '@playwright/test';

test.describe('Debate View', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/debate?mock=true');
  });

  test('shows Research Debate tab active by default', async ({ page }) => {
    await expect(page.getByText('Research Debate')).toBeVisible();
    await expect(page.getByText('Risk Debate')).toBeVisible();
  });

  test('shows bull and bear columns', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Bull' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Bear' })).toBeVisible();
  });

  test('facts appear after pipeline run', async ({ page }) => {
    // Start pipeline from dashboard
    await page.goto('/?mock=true');
    const tickerInput = page.locator('input[placeholder*="ticker"]');
    await tickerInput.fill('AAPL');
    await page.getByRole('button', { name: /Run Pipeline/i }).click();

    // Wait for facts to be published
    await page.waitForTimeout(1500);

    // Navigate to debate view
    await page.getByRole('link', { name: 'Debate' }).click();
    await expect(page.getByText('Strong momentum detected')).toBeVisible({ timeout: 3000 });
  });

  test('can switch to Risk Debate tab', async ({ page }) => {
    const riskTab = page.locator('button', { hasText: 'Risk Debate' });
    await riskTab.click();
    await expect(page.getByRole('heading', { name: 'Aggressive' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Conservative' })).toBeVisible();
  });
});
