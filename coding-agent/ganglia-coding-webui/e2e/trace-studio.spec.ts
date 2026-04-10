import { test, expect } from '@playwright/test';

const MOCK_FILES = ['session-001.jsonl', 'session-002.jsonl'];

function mockEvent(overrides: Record<string, unknown> = {}) {
  return {
    sessionId: 'sess-1',
    type: 'TOOL_STARTED',
    content: 'read_file',
    data: {},
    timestamp: Date.now(),
    spanId: 'span-1',
    ...overrides,
  };
}

function setupRoutes(page: import('@playwright/test').Page, events: Record<string, unknown>[] = []) {
  return Promise.all([
    page.route('**/api/traces', (route) =>
      route.fulfill({ json: MOCK_FILES }),
    ),
    page.route('**/api/traces/session-001.jsonl', (route) =>
      route.fulfill({ json: events.length > 0 ? events : [mockEvent()] }),
    ),
    page.route('**/api/traces/session-002.jsonl', (route) =>
      route.fulfill({ json: [mockEvent({ spanId: 'span-2', content: 'bash' })] }),
    ),
  ]);
}

test.describe('TraceStudio', () => {
  test('page loads with title and file list', async ({ page }) => {
    await setupRoutes(page);
    await page.goto('/trace.html');

    await expect(page.getByText('Trace Studio')).toBeVisible();
    await expect(page.getByText('session-001')).toBeVisible();
    await expect(page.getByText('session-002')).toBeVisible();
  });

  test('selecting a trace file renders events', async ({ page }) => {
    const events = [
      mockEvent({ type: 'TOOL_STARTED', content: 'read_file', spanId: 'span-a' }),
      mockEvent({ type: 'TOOL_FINISHED', content: 'done', spanId: 'span-a' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');

    await page.getByText('session-001').click();
    await expect(page.getByText('read_file')).toBeVisible();
  });

  test('MANAGER event uses violet color', async ({ page }) => {
    const events = [
      mockEvent({ type: 'MANAGER_CYCLE_STARTED', content: 'Cycle 1', spanId: 'span-mgr' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    const badge = page.locator('span', { hasText: 'MANAGER_CYCLE' });
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/violet/);
  });

  test('FACT event uses teal color', async ({ page }) => {
    const events = [
      mockEvent({ type: 'FACT_PUBLISHED', content: 'New fact', spanId: 'span-fact' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    const badge = page.locator('span', { hasText: 'FACT' });
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/teal/);
  });

  test('WORKTREE event uses pink color', async ({ page }) => {
    const events = [
      mockEvent({ type: 'WORKTREE_CREATED', content: 'worktree-1', spanId: 'span-wt' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    const badge = page.locator('span', { hasText: 'WORKTREE' });
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/pink/);
  });

  test('REALITY_ANCHOR event uses lime color', async ({ page }) => {
    const events = [
      mockEvent({ type: 'REALITY_ANCHOR_STARTED', content: 'anchor', spanId: 'span-ra' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    const badge = page.locator('span', { hasText: 'REALITY_ANCHOR' });
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/lime/);
  });

  test('FINGERPRINT event uses sky color', async ({ page }) => {
    const events = [
      mockEvent({ type: 'FINGERPRINT_CACHE_HIT', content: 'cached node', spanId: 'span-fp' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    const badge = page.locator('span', { hasText: 'FINGERPRINT_CACHE' });
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/sky/);
  });

  test('MANAGER_GRAPH_CONVERGED shows Converged badge', async ({ page }) => {
    const events = [
      mockEvent({
        type: 'MANAGER_GRAPH_CONVERGED',
        content: 'All resolved',
        spanId: 'span-conv',
      }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    await expect(page.getByText('Converged')).toBeVisible();
  });

  test('MANAGER_GRAPH_STALLED shows Stalled badge', async ({ page }) => {
    const events = [
      mockEvent({
        type: 'MANAGER_GRAPH_STALLED',
        content: 'No progress',
        spanId: 'span-stall',
      }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    await expect(page.getByText('Stalled')).toBeVisible();
  });

  test('FACT_SUPERSEDED shows Superseded badge and strikethrough', async ({ page }) => {
    const events = [
      mockEvent({
        type: 'FACT_SUPERSEDED',
        content: 'Old fact replaced',
        spanId: 'span-sup',
      }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    await expect(page.getByText('Superseded')).toBeVisible();
    // Content should have line-through style
    const content = page.locator('.line-through', { hasText: 'Old fact replaced' });
    await expect(content).toBeVisible();
  });

  test('FINGERPRINT_CACHE_HIT shows Cached badge', async ({ page }) => {
    const events = [
      mockEvent({
        type: 'FINGERPRINT_CACHE_HIT',
        content: 'cached result',
        spanId: 'span-cached',
      }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    await expect(page.getByText('Cached')).toBeVisible();
  });

  test('type label strips suffixes correctly', async ({ page }) => {
    const events = [
      mockEvent({ type: 'MANAGER_GRAPH_CONVERGED', content: 'conv', spanId: 'span-s1' }),
      mockEvent({ type: 'FACT_SUPERSEDED', content: 'sup', spanId: 'span-s2' }),
      mockEvent({ type: 'FINGERPRINT_CACHE_HIT', content: 'hit', spanId: 'span-s3' }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    // The type labels should have suffixes stripped
    // MANAGER_GRAPH_CONVERGED → MANAGER_GRAPH (suffix _CONVERGED removed)
    await expect(page.locator('span', { hasText: 'MANAGER_GRAPH' }).first()).toBeVisible();
    // FACT_SUPERSEDED → FACT (suffix _SUPERSEDED removed)
    await expect(page.locator('span', { hasText: /^FACT$/ }).first()).toBeVisible();
    // FINGERPRINT_CACHE_HIT → FINGERPRINT_CACHE (suffix _HIT removed)
    await expect(page.locator('span', { hasText: 'FINGERPRINT_CACHE' }).first()).toBeVisible();
  });

  test('span collapse and expand toggles children', async ({ page }) => {
    const events = [
      mockEvent({
        type: 'TOOL_STARTED',
        content: 'parent tool',
        spanId: 'span-parent',
      }),
      mockEvent({
        type: 'TOOL_STARTED',
        content: 'child tool',
        spanId: 'span-child',
        parentSpanId: 'span-parent',
      }),
      mockEvent({
        type: 'TOOL_FINISHED',
        content: 'child done',
        spanId: 'span-child',
        parentSpanId: 'span-parent',
      }),
      mockEvent({
        type: 'TOOL_FINISHED',
        content: 'parent done',
        spanId: 'span-parent',
      }),
    ];
    await setupRoutes(page, events);
    await page.goto('/trace.html');
    await page.getByText('session-001').click();

    // Both parent and child should be visible (all expanded by default)
    await expect(page.getByText('parent tool')).toBeVisible();
    await expect(page.getByText('child tool')).toBeVisible();

    // Click parent to collapse — child should disappear
    await page.getByText('parent tool').click();
    await expect(page.getByText('child tool')).not.toBeVisible();

    // Click parent again to expand — child reappears
    await page.getByText('parent tool').click();
    await expect(page.getByText('child tool')).toBeVisible();
  });

  test('live mode button is visible', async ({ page }) => {
    await setupRoutes(page);
    await page.goto('/trace.html');

    await expect(page.getByText('Connect Live')).toBeVisible();
  });
});
