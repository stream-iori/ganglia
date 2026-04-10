import { useConfigStore } from '@/stores/config';
import { useSystemStore } from '@/stores/system';
import { sendRpc } from '@/services/eventbus';
import { Save, RotateCcw } from 'lucide-react';

export function ConfigPanel() {
  const { config, isDirty, updateField, setConfig, resetDirty } = useConfigStore();
  const connectionStatus = useSystemStore((s) => s.connectionStatus);
  const isMock = new URLSearchParams(window.location.search).get('mock') === 'true';

  const handleSave = async () => {
    if (isMock) {
      resetDirty();
      return;
    }
    try {
      await sendRpc('UPDATE_CONFIG', { config });
      resetDirty();
    } catch (e) {
      console.error('Failed to save config', e);
    }
  };

  const handleReset = () => {
    setConfig({
      investmentStyle: 'VALUE',
      maxDebateRounds: 3,
      maxRiskDiscussRounds: 2,
      outputLanguage: 'en',
      instrumentContext: 'stock',
      dataVendor: 'YFINANCE',
      enableMemoryTwr: true,
      memoryHalfLifeDays: 180,
    });
  };

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Configuration</h1>
        <div className="flex gap-2">
          <button
            onClick={handleReset}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-input text-sm hover:bg-accent"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Reset
          </button>
          <button
            onClick={handleSave}
            disabled={!isDirty || (connectionStatus === 'disconnected' && !isMock)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
          >
            <Save className="h-3.5 w-3.5" />
            Save
          </button>
        </div>
      </div>

      <div className="space-y-5">
        {/* Investment Style */}
        <div>
          <label className="block text-sm font-medium mb-2">Investment Style</label>
          <div className="flex gap-2">
            {['VALUE', 'GROWTH', 'MOMENTUM', 'CONTRARIAN'].map((style) => (
              <button
                key={style}
                onClick={() => updateField('investmentStyle', style)}
                className={`px-3 py-1.5 rounded-md text-sm border ${
                  config.investmentStyle === style
                    ? 'border-primary bg-primary/10 text-foreground font-medium'
                    : 'border-input text-muted-foreground hover:bg-accent'
                }`}
              >
                {style}
              </button>
            ))}
          </div>
        </div>

        {/* Max Debate Rounds */}
        <div>
          <label className="block text-sm font-medium mb-2">
            Max Debate Rounds: {config.maxDebateRounds}
          </label>
          <input
            type="range"
            min={1}
            max={10}
            value={config.maxDebateRounds}
            onChange={(e) => updateField('maxDebateRounds', parseInt(e.target.value))}
            className="w-full"
          />
        </div>

        {/* Max Risk Discuss Rounds */}
        <div>
          <label className="block text-sm font-medium mb-2">
            Max Risk Discuss Rounds: {config.maxRiskDiscussRounds}
          </label>
          <input
            type="range"
            min={1}
            max={10}
            value={config.maxRiskDiscussRounds}
            onChange={(e) => updateField('maxRiskDiscussRounds', parseInt(e.target.value))}
            className="w-full"
          />
        </div>

        {/* Output Language */}
        <div>
          <label className="block text-sm font-medium mb-2">Output Language</label>
          <select
            value={config.outputLanguage}
            onChange={(e) => updateField('outputLanguage', e.target.value)}
            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm w-full focus:outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="en">English</option>
            <option value="zh">Chinese</option>
            <option value="ja">Japanese</option>
          </select>
        </div>

        {/* Instrument Context */}
        <div>
          <label className="block text-sm font-medium mb-2">Instrument Context</label>
          <input
            type="text"
            value={config.instrumentContext}
            onChange={(e) => updateField('instrumentContext', e.target.value)}
            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm w-full focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        {/* Data Vendor */}
        <div>
          <label className="block text-sm font-medium mb-2">Data Vendor</label>
          <select
            value={config.dataVendor}
            onChange={(e) => updateField('dataVendor', e.target.value)}
            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm w-full focus:outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="YFINANCE">Yahoo Finance</option>
            <option value="ALPHA_VANTAGE">Alpha Vantage</option>
          </select>
        </div>

        {/* Fallback Vendor */}
        <div>
          <label className="block text-sm font-medium mb-2">Fallback Vendor</label>
          <select
            value={config.fallbackVendor || ''}
            onChange={(e) => updateField('fallbackVendor', e.target.value || undefined)}
            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm w-full focus:outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="">None</option>
            <option value="YFINANCE">Yahoo Finance</option>
            <option value="ALPHA_VANTAGE">Alpha Vantage</option>
          </select>
        </div>

        {/* Enable Memory TWR */}
        <div className="flex items-center justify-between">
          <label className="text-sm font-medium">Enable Memory TWR</label>
          <button
            onClick={() => updateField('enableMemoryTwr', !config.enableMemoryTwr)}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
              config.enableMemoryTwr ? 'bg-primary' : 'bg-muted'
            }`}
          >
            <span
              className={`inline-block h-4 w-4 rounded-full bg-white transition-transform ${
                config.enableMemoryTwr ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </button>
        </div>

        {/* Memory Half Life Days */}
        {config.enableMemoryTwr && (
          <div>
            <label className="block text-sm font-medium mb-2">
              Memory Half Life: {config.memoryHalfLifeDays} days
            </label>
            <input
              type="range"
              min={1}
              max={365}
              value={config.memoryHalfLifeDays}
              onChange={(e) => updateField('memoryHalfLifeDays', parseInt(e.target.value))}
              className="w-full"
            />
          </div>
        )}
      </div>
    </div>
  );
}
