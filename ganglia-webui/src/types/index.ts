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
  | 'USER_MESSAGE';

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

export type ClientAction = 'START' | 'RESPOND_ASK' | 'CANCEL' | 'RETRY' | 'READ_FILE' | 'SYNC' | 'LIST_FILES';

export interface ClientRequest<T = any> {
  action: ClientAction;
  sessionId: string;
  payload: T;
}
