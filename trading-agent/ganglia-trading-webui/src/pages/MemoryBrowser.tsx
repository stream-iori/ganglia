import { useEffect } from 'react';
import { useMemoryStore } from '@/stores/memory';
import { useSystemStore } from '@/stores/system';
import { FactCard } from '@/components/FactCard';
import { sendRpc } from '@/services/eventbus';
import type { FactData } from '@/types';

export function MemoryBrowser() {
  const {
    facts,
    selectedFact,
    factDetail,
    roleFilter,
    setFacts,
    selectFact,
    setFactDetail,
    setRoleFilter,
    setLoading,
  } = useMemoryStore();
  const connectionStatus = useSystemStore((s) => s.connectionStatus);
  const isMock = new URLSearchParams(window.location.search).get('mock') === 'true';

  useEffect(() => {
    if (connectionStatus !== 'connected' && !isMock) return;

    const loadFacts = async () => {
      setLoading(true);
      try {
        const url = roleFilter
          ? `/api/memory?role=${encodeURIComponent(roleFilter)}`
          : '/api/memory';
        const response = await fetch(url);
        const data = await response.json();
        setFacts(data as FactData[]);
      } catch {
        // silent fail
      } finally {
        setLoading(false);
      }
    };

    if (!isMock) {
      loadFacts();
    }
  }, [connectionStatus, roleFilter, isMock, setFacts, setLoading]);

  const handleSelectFact = async (fact: FactData) => {
    selectFact(fact);
    if (fact.detailRef) {
      try {
        const response = await fetch(`/api/memory/${fact.id}`);
        if (response.ok) {
          const data = await response.json();
          setFactDetail(data.detail);
        }
      } catch {
        // silent fail
      }
    }
  };

  const filteredFacts = roleFilter
    ? facts.filter((f) => f.tags?.role === roleFilter)
    : facts;

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Memory Browser</h1>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <label className="text-sm text-muted-foreground">Filter by role:</label>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="px-3 py-1.5 rounded-md border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
        >
          <option value="">All roles</option>
          <option value="bull">Bull</option>
          <option value="bear">Bear</option>
          <option value="aggressive">Aggressive</option>
          <option value="neutral">Neutral</option>
          <option value="conservative">Conservative</option>
        </select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Fact List */}
        <div className="lg:col-span-2 space-y-2">
          {filteredFacts.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">
              No facts found. Run a pipeline to generate facts.
            </p>
          ) : (
            filteredFacts.map((fact) => (
              <FactCard
                key={fact.id}
                fact={fact}
                onClick={() => handleSelectFact(fact)}
              />
            ))
          )}
        </div>

        {/* Detail Panel */}
        <div className="lg:col-span-1">
          {selectedFact ? (
            <div className="rounded-lg border border-border bg-card p-4 sticky top-6">
              <h3 className="font-semibold mb-2">Fact Detail</h3>
              <div className="space-y-2 text-sm">
                <div>
                  <span className="text-muted-foreground">ID:</span>{' '}
                  <span className="font-mono text-xs">{selectedFact.id}</span>
                </div>
                <div>
                  <span className="text-muted-foreground">Manager:</span>{' '}
                  {selectedFact.managerId}
                </div>
                <div>
                  <span className="text-muted-foreground">Status:</span>{' '}
                  {selectedFact.status}
                </div>
                <div>
                  <span className="text-muted-foreground">Cycle:</span>{' '}
                  {selectedFact.cycleNumber}
                </div>
                <div>
                  <span className="text-muted-foreground">Summary:</span>
                  <p className="mt-1">{selectedFact.summary}</p>
                </div>
                {factDetail && (
                  <div>
                    <span className="text-muted-foreground">Detail:</span>
                    <p className="mt-1 whitespace-pre-wrap">{factDetail}</p>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="rounded-lg border border-border bg-card p-4 text-center text-muted-foreground text-sm">
              Select a fact to view details
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
