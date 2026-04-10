// --- Enums ---

export type TradingEventType =
  | 'PIPELINE_STARTED'
  | 'PIPELINE_PHASE_CHANGED'
  | 'PIPELINE_COMPLETED'
  | 'PIPELINE_ERROR'
  | 'DEBATE_CYCLE_STARTED'
  | 'DEBATE_CYCLE_FINISHED'
  | 'DEBATE_CONVERGED'
  | 'DEBATE_STALLED'
  | 'FACT_PUBLISHED'
  | 'FACT_SUPERSEDED'
  | 'FACT_ARCHIVED'
  | 'AGENT_TOKEN'
  | 'AGENT_THOUGHT'
  | 'AGENT_MESSAGE'
  | 'TOOL_START'
  | 'TOOL_RESULT'
  | 'INIT_CONFIG'
  | 'SIGNAL_HISTORY_UPDATE';

export type PipelinePhase = 'PERCEPTION' | 'DEBATE' | 'RISK' | 'SIGNAL';

export type PipelineStatus = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'ERROR';

export type SignalType = 'BUY' | 'OVERWEIGHT' | 'HOLD' | 'UNDERWEIGHT' | 'SELL';

export type DebateType = 'RESEARCH' | 'RISK';

export type InvestmentStyle = 'VALUE' | 'GROWTH' | 'MOMENTUM' | 'CONTRARIAN';

export type DataVendor = 'YFINANCE' | 'ALPHA_VANTAGE';

// --- Config ---

export interface TradingConfig {
  investmentStyle: string;
  maxDebateRounds: number;
  maxRiskDiscussRounds: number;
  outputLanguage: string;
  instrumentContext: string;
  dataVendor: string;
  fallbackVendor?: string;
  enableMemoryTwr: boolean;
  memoryHalfLifeDays: number;
}

// --- Signals ---

export interface TradingSignal {
  signal: SignalType;
  confidence: number;
  rationale: string;
}

export interface SignalHistoryEntry {
  ticker: string;
  signal: string;
  confidence: number;
  rationale: string;
  timestamp: number;
}

// --- Pipeline Events ---

export interface PipelinePhaseData {
  phase: PipelinePhase;
  status: string;
  ticker: string;
}

export interface PipelineCompletedData {
  signal: string;
  confidence: number;
  rationale: string;
  perceptionReport: string;
  debateReport: string;
  riskReport: string;
}

// --- Debate Events ---

export interface DebateCycleData {
  debateType: DebateType;
  cycleNumber: number;
  maxCycles: number;
  decisionType?: string;
}

// --- Blackboard / Facts ---

export interface FactData {
  id: string;
  managerId: string;
  summary: string;
  detailRef?: string;
  cycleNumber: number;
  status: string;
  tags: Record<string, string>;
  createdAt: number;
}

export interface FactSupersededData {
  factId: string;
  reason: string;
}

// --- Server Event Wrapper ---

export interface ServerEvent<T = unknown> {
  id: string;
  timestamp: number;
  type: TradingEventType;
  data: T;
}

// --- Client Actions ---

export type ClientAction =
  | 'SYNC'
  | 'RUN_PIPELINE'
  | 'GET_CONFIG'
  | 'UPDATE_CONFIG'
  | 'GET_MEMORY';
