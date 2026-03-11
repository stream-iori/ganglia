export type EventType = 
  | 'THOUGHT' 
  | 'TOOL_START' 
  | 'TOOL_OUTPUT_STREAM' 
  | 'TOOL_RESULT' 
  | 'ASK_USER' 
  | 'AGENT_MESSAGE' 
  | 'SYSTEM_ERROR'
  | 'FILE_CONTENT'
  | 'FILE_TREE'
  | 'TOKEN'
  | 'USER_MESSAGE'
  | 'INIT_CONFIG';

export interface InitConfigData {
  workspacePath: string;
  sessionId: string;
}

export interface ThoughtData {
  content: string;
}

export interface UserMessageData {
  content: string;
}

export interface ToolStartData {
  toolCallId: string;
  toolName: string;
  command: string;
}

export interface ToolResultData {
  toolCallId: string;
  exitCode: number;
  summary: string;
  fullOutput: string;
  isError: boolean;
  errorType?: string;
}

export interface AskOption {
  value: string;
  label: string;
  description: string;
}

export interface AskUserData {
  askId: string;
  question: string;
  options: AskOption[];
  diffContext?: string; // Optional diff patch string
}

export interface AgentMessageData {
  content: string;
}

export interface SystemErrorData {
  code: string;
  message: string;
  stackTrace?: string;
  canRetry: boolean;
}

export interface FileContentData {
  path: string;
  content: string;
  language: string;
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileTreeNode[];
}

export interface TokenData {
  content: string;
  role?: 'thought' | 'answer';
}

export interface TtyData {
  toolCallId: string;
  text: string;
  isError: boolean;
}

export interface ServerEvent<T = any> {
  eventId: string;
  timestamp: number;
  type: EventType;
  data: T;
}

export interface JsonRpcRequest<T = any> {
  jsonrpc: '2.0';
  method: string;
  params: T & { sessionId: string };
  id?: string | number;
}

export interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: string | number;
  result?: any;
  error?: {
    code: number;
    message: string;
    data?: any;
  };
}

export interface JsonRpcNotification {
  jsonrpc: '2.0';
  method: string;
  params: any;
}

export interface SyncParams {}
export interface StartParams { prompt: string }
export interface RespondAskParams { askId: string, selectedOption: string }
export interface CancelParams {}
export interface RetryParams {}
export interface ReadFileParams { path: string }
export interface ListFilesParams {}

export type ClientAction = 'START' | 'RESPOND_ASK' | 'CANCEL' | 'RETRY' | 'READ_FILE' | 'SYNC' | 'LIST_FILES';
