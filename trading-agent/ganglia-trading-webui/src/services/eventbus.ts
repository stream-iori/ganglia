import type {
  TradingEventType,
  ServerEvent,
  PipelinePhaseData,
  PipelineCompletedData,
  DebateCycleData,
  FactData,
  FactSupersededData,
  TradingConfig,
  SignalHistoryEntry,
} from '@/types';
import { usePipelineStore } from '@/stores/pipeline';
import { useDebateStore } from '@/stores/debate';
import { useSignalStore } from '@/stores/signals';
import { useConfigStore } from '@/stores/config';
import { useMemoryStore } from '@/stores/memory';
import { useSystemStore } from '@/stores/system';

let ws: WebSocket | null = null;
let rpcId = 0;
const pendingRequests = new Map<number, { resolve: (v: unknown) => void; reject: (e: Error) => void }>();

function getWsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

export function connect(): void {
  const systemStore = useSystemStore.getState();
  systemStore.setConnectionStatus('connecting');

  ws = new WebSocket(getWsUrl());

  ws.onopen = () => {
    systemStore.setConnectionStatus('connected');
    sendRpc('SYNC', { sessionId: systemStore.sessionId });
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);

      // RPC response
      if (msg.id != null && msg.result !== undefined) {
        const pending = pendingRequests.get(msg.id);
        if (pending) {
          pendingRequests.delete(msg.id);
          pending.resolve(msg.result);
        }
        return;
      }

      // Server event (notification)
      if (msg.method === 'server_event' && msg.params) {
        handleServerEvent(msg.params as ServerEvent);
      }
    } catch {
      // ignore parse errors
    }
  };

  ws.onclose = () => {
    systemStore.setConnectionStatus('disconnected');
    ws = null;
    // Reconnect after delay
    setTimeout(connect, 3000);
  };

  ws.onerror = () => {
    ws?.close();
  };
}

export function disconnect(): void {
  if (ws) {
    ws.onclose = null;
    ws.close();
    ws = null;
  }
  useSystemStore.getState().setConnectionStatus('disconnected');
}

export function sendRpc(method: string, params: Record<string, unknown> = {}): Promise<unknown> {
  return new Promise((resolve, reject) => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      reject(new Error('WebSocket not connected'));
      return;
    }
    const id = ++rpcId;
    const sessionId = useSystemStore.getState().sessionId;
    pendingRequests.set(id, { resolve, reject });
    ws.send(
      JSON.stringify({
        jsonrpc: '2.0',
        method,
        params: { sessionId, ...params },
        id,
      })
    );
  });
}

function handleServerEvent(event: ServerEvent): void {
  const type = event.type as TradingEventType;
  const data = event.data;

  switch (type) {
    case 'PIPELINE_STARTED':
    case 'PIPELINE_PHASE_CHANGED': {
      const d = data as PipelinePhaseData;
      usePipelineStore.getState().setPhase(d.phase, d.status === 'RUNNING' ? 'RUNNING' : 'COMPLETED');
      break;
    }
    case 'PIPELINE_COMPLETED': {
      const d = data as PipelineCompletedData;
      usePipelineStore.getState().setResult(d);
      useSignalStore.getState().addSignal({
        ticker: usePipelineStore.getState().ticker,
        signal: d.signal,
        confidence: d.confidence,
        rationale: d.rationale,
        timestamp: Date.now(),
      });
      break;
    }
    case 'PIPELINE_ERROR':
      usePipelineStore.getState().setPhase(
        usePipelineStore.getState().currentPhase ?? 'PERCEPTION',
        'ERROR'
      );
      break;

    case 'DEBATE_CYCLE_STARTED': {
      const d = data as DebateCycleData;
      useDebateStore.getState().setCycle(d.debateType, d.cycleNumber, d.maxCycles);
      break;
    }
    case 'DEBATE_CYCLE_FINISHED': {
      const d = data as DebateCycleData;
      useDebateStore.getState().setStatus(d.debateType, 'RUNNING', d.decisionType);
      break;
    }
    case 'DEBATE_CONVERGED': {
      const d = data as DebateCycleData;
      useDebateStore.getState().setStatus(d.debateType ?? 'RESEARCH', 'CONVERGED');
      break;
    }
    case 'DEBATE_STALLED': {
      const d = data as DebateCycleData;
      useDebateStore.getState().setStatus(d.debateType ?? 'RESEARCH', 'STALLED');
      break;
    }

    case 'FACT_PUBLISHED': {
      const d = data as FactData & { debateType?: string };
      const debateType = (d.debateType as 'RESEARCH' | 'RISK') || 'RESEARCH';
      useDebateStore.getState().addFact(debateType, d);
      break;
    }
    case 'FACT_SUPERSEDED': {
      const d = data as FactSupersededData;
      useDebateStore.getState().supersedeFact(d.factId);
      break;
    }

    case 'INIT_CONFIG': {
      const d = data as { config: TradingConfig };
      if (d.config) {
        useConfigStore.getState().setConfig(d.config);
      }
      break;
    }

    default:
      break;
  }
}

// --- Mock Mode for E2E Testing ---

export function setupMockMode(): void {
  const params = new URLSearchParams(window.location.search);
  if (params.get('mock') !== 'true') return;

  useSystemStore.getState().setConnectionStatus('connected');
  useConfigStore.getState().setConfig({
    investmentStyle: 'VALUE',
    maxDebateRounds: 3,
    maxRiskDiscussRounds: 2,
    outputLanguage: 'en',
    instrumentContext: 'stock',
    dataVendor: 'YFINANCE',
    enableMemoryTwr: true,
    memoryHalfLifeDays: 180,
  });
}

export function runMockPipeline(ticker: string): void {
  const pipeline = usePipelineStore.getState();
  const debate = useDebateStore.getState();
  const signal = useSignalStore.getState();

  pipeline.startPipeline(ticker);

  // Simulate pipeline phases
  setTimeout(() => {
    pipeline.setPhase('PERCEPTION', 'COMPLETED');
    pipeline.setPhase('DEBATE', 'RUNNING');
    debate.setCycle('RESEARCH', 1, 3);
  }, 500);

  setTimeout(() => {
    debate.addFact('RESEARCH', {
      id: 'mock-fact-1',
      managerId: 'bull',
      summary: 'Strong momentum detected in recent price action',
      cycleNumber: 1,
      status: 'ACTIVE',
      tags: { role: 'bull', stance: 'bullish' },
      createdAt: Date.now(),
    });
  }, 800);

  setTimeout(() => {
    debate.addFact('RESEARCH', {
      id: 'mock-fact-2',
      managerId: 'bear',
      summary: 'Overvaluation risk based on P/E ratio',
      cycleNumber: 1,
      status: 'ACTIVE',
      tags: { role: 'bear', stance: 'bearish' },
      createdAt: Date.now(),
    });
  }, 1100);

  setTimeout(() => {
    debate.setStatus('RESEARCH', 'CONVERGED');
    pipeline.setPhase('DEBATE', 'COMPLETED');
    pipeline.setPhase('RISK', 'RUNNING');
    debate.setCycle('RISK', 1, 2);
  }, 1500);

  setTimeout(() => {
    debate.setStatus('RISK', 'CONVERGED');
    pipeline.setPhase('RISK', 'COMPLETED');
    pipeline.setPhase('SIGNAL', 'RUNNING');
  }, 2000);

  setTimeout(() => {
    const result: PipelineCompletedData = {
      signal: 'OVERWEIGHT',
      confidence: 0.72,
      rationale: `${ticker} shows strong momentum with manageable risk. P/E ratio slightly elevated but justified by growth trajectory.`,
      perceptionReport: 'Technical indicators suggest upward trend continuation.',
      debateReport: 'Bull case prevails with 3:2 consensus on growth potential.',
      riskReport: 'Risk within acceptable parameters. Key downside: sector rotation.',
    };
    pipeline.setResult(result);
    signal.addSignal({
      ticker,
      signal: result.signal,
      confidence: result.confidence,
      rationale: result.rationale,
      timestamp: Date.now(),
    });
  }, 2500);
}
