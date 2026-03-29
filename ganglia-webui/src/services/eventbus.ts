import { useSystemStore } from '../stores/system';
import { useLogStore } from '../stores/log';
import { usePlanStore } from '../stores/plan';
import type {
  ServerEvent,
  ClientAction,
  TtyData,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcNotification,
  StartParams,
  RespondAskParams,
  ReadFileParams,
  SyncParams,
  ListFilesParams,
  InitConfigData,
  ToDoList,
} from '../types';

type PendingRequest = {
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
};

class EventBusService {
  private ws: WebSocket | null = null;
  private url: string = 'ws://' + window.location.host + '/ws';
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private retryCount: number = 0;
  private maxRetryCount: number = 10;
  private isManualClosed: boolean = false;
  private requestCounter: number = 0;
  private pendingRequests: Map<string | number, PendingRequest> = new Map();

  constructor() {
    if (import.meta.env.DEV) {
      this.url = 'ws://localhost:8080/ws';
    }

    // Check if mock mode is requested via query param
    const params = new URLSearchParams(window.location.search);
    if (params.has('mock')) {
      this.setupMockMode();
    }
  }

  private setupMockMode() {
    console.warn('RUNNING IN MOCK MODE - No backend required');
    // @ts-expect-error Mocking global WebSocket for demo purposes
    window.WebSocket = class MockWebSocket {
      static OPEN = 1;
      readyState = 1;
      onopen: (() => void) | null = null;
      onmessage: ((ev: { data: string }) => void) | null = null;
      private activeIntervals: ReturnType<typeof setInterval>[] = [];

      constructor() {
        setTimeout(() => this.onopen && this.onopen(), 100);
      }

      private clearSimulations() {
        this.activeIntervals.forEach((id) => clearInterval(id));
        this.activeIntervals = [];
      }

      send(data: string) {
        const msg: JsonRpcRequest<Record<string, unknown>> = JSON.parse(data);
        console.log('[MockWS] Received:', msg);

        if (msg.method === 'SYNC') {
          // Backend sends INIT_CONFIG before SYNC response
          this.notify('server_event', {
            eventId: 'init-1',
            type: 'INIT_CONFIG',
            timestamp: Date.now(),
            data: { workspacePath: '/mock/workspace', sessionId: msg.params.sessionId },
          });
          this.respond(msg.id!, {
            history: [
              {
                eventId: 'm1',
                timestamp: Date.now(),
                type: 'AGENT_MESSAGE',
                data: {
                  content:
                    'Hello! I am a mock agent. You are running in ?mock mode. Try asking me to "run ls", "show diff", or "trigger file change".',
                },
              },
            ],
          });
        } else if (msg.method === 'START' || msg.method === 'RETRY') {
          const params = msg.params as unknown as StartParams;
          const prompt = (params.prompt || 'Retry last prompt').toLowerCase();
          this.respond(msg.id!, { status: 'started' });

          if (prompt.includes('error')) {
            this.simulateError();
          } else if (prompt.includes('file change') || prompt.includes('watch')) {
            this.simulateFileTreeChange();
          } else if (prompt.includes('ls') || prompt.includes('run')) {
            this.simulateToolExecution();
          } else if (prompt.includes('plan') || prompt.includes('todo')) {
            this.simulatePlanUpdate();
          } else if (prompt.includes('diff') || prompt.includes('ask')) {
            this.simulateAskWithDiff();
          } else if (prompt.includes('select') || prompt.includes('choice')) {
            this.simulateAskSelection();
          } else {
            this.simulateTypewriterResponse(
              'I received your message: "' + prompt + '". How can I help you today?',
            );
          }
        } else if (msg.method === 'CANCEL') {
          this.clearSimulations();
          useSystemStore.setState({ activeAskId: null });
          this.respond(msg.id!, { status: 'cancelled' });
          this.notify('server_event', {
            eventId: 'stop-1',
            type: 'AGENT_MESSAGE',
            data: { content: 'Execution cancelled by user.' },
          });
        } else if (msg.method === 'RESPOND_ASK') {
          useSystemStore.setState({ activeAskId: null });
          this.respond(msg.id!, { status: 'resumed' });
          this.simulateTypewriterResponse('Thank you for your decision. I will proceed now.');
        } else if (msg.method === 'LIST_FILES') {
          this.notify('server_event', {
            eventId: 'm4',
            type: 'FILE_TREE',
            data: {
              name: 'root',
              type: 'directory',
              path: '.',
              children: [
                { name: 'README.md', type: 'file', path: './README.md' },
                {
                  name: 'src',
                  type: 'directory',
                  path: './src',
                  children: [
                    { name: 'main.ts', type: 'file', path: './src/main.ts' },
                    { name: 'App.vue', type: 'file', path: './src/App.vue' },
                  ],
                },
                { name: 'package.json', type: 'file', path: './package.json' },
              ],
            },
          });
          this.respond(msg.id!, { status: 'ok' });
        } else if (msg.method === 'READ_FILE') {
          const params = msg.params as unknown as ReadFileParams;
          if (params.path === 'WORKSPACE_DIFF_VIRTUAL_PATH') {
            this.notify('server_event', {
              eventId: 'diff-1',
              type: 'FILE_CONTENT',
              data: {
                path: 'WORKSPACE_DIFF_VIRTUAL_PATH',
                content:
                  "--- a/src/main.ts\n+++ b/src/main.ts\n@@ -1,5 +1,6 @@\n import { createApp } from 'vue'\n+import { createPinia } from 'pinia'\n import App from './App.vue'\n \n createApp(App).mount('#app')\n",
                language: 'diff',
              },
            });
          } else {
            this.notify('server_event', {
              eventId: 'file-1',
              type: 'FILE_CONTENT',
              data: {
                path: params.path,
                content:
                  '// Content of ' +
                  params.path +
                  '\nconsole.log("Hello from Mock WebSocket!");\n\nfunction test() {\n  return true;\n}',
                language: params.path.endsWith('.vue') ? 'html' : 'typescript',
              },
            });
          }
          this.respond(msg.id!, { status: 'reading' });
        }
      }

      private simulateError() {
        this.notify('server_event', {
          eventId: 'err-' + Date.now(),
          type: 'SYSTEM_ERROR',
          data: {
            code: 'MOCK_ERROR',
            message: 'This is a simulated system error. You can click retry to recover.',
            canRetry: true,
          },
        });
      }

      private simulateFileTreeChange() {
        this.notify('server_event', {
          eventId: 't-file',
          type: 'THOUGHT',
          data: { content: 'Simulating a file system change (e.g. creating new_file.ts)...' },
        });

        setTimeout(() => {
          this.notify('server_event', {
            eventId: 'm4-changed',
            type: 'FILE_TREE',
            data: {
              name: 'root',
              type: 'directory',
              path: '.',
              children: [
                { name: 'README.md', type: 'file', path: './README.md' },
                { name: 'new_file.ts', type: 'file', path: './new_file.ts' },
                {
                  name: 'src',
                  type: 'directory',
                  path: './src',
                  children: [
                    { name: 'main.ts', type: 'file', path: './src/main.ts' },
                    { name: 'App.vue', type: 'file', path: './src/App.vue' },
                  ],
                },
                { name: 'package.json', type: 'file', path: './package.json' },
              ],
            },
          });
          this.simulateTypewriterResponse(
            'I have detected a file system change. The sidebar file tree has been updated automatically to show `new_file.ts`.',
          );
        }, 1500);
      }

      private simulateTypewriterResponse(text: string) {
        this.notify('server_event', {
          eventId: 't-' + Date.now(),
          type: 'THOUGHT',
          data: { content: 'Processing request...' },
        });

        let current = 0;
        const interval = setInterval(() => {
          if (current < text.length) {
            this.notify('server_event', {
              eventId: 'token-' + current + '-' + Date.now(),
              type: 'TOKEN',
              data: { content: text[current] },
            });
            current++;
          } else {
            clearInterval(interval);
            this.activeIntervals = this.activeIntervals.filter((id) => id !== interval);
            this.notify('server_event', {
              eventId: 'final-msg-' + Date.now(),
              type: 'AGENT_MESSAGE',
              data: {
                content:
                  text +
                  '\n\n```typescript\nfunction hello() {\n  console.log("This is a code block");\n}\n```',
              },
            });
          }
        }, 10);
        this.activeIntervals.push(interval);
      }

      private simulateToolExecution() {
        const toolCallId = 'tool-' + Date.now();
        console.log('[MockWS] Executing tool with ID:', toolCallId);
        this.notify('server_event', {
          eventId: 'ts-' + Date.now(),
          type: 'TOOL_START',
          data: { toolCallId, toolName: 'run_shell_command', command: 'ls -l' },
        });

        const lines = [
          'total 8',
          '-rw-r--r--  1 staff  123 Mar  7 17:00 README.md',
          'drwxr-xr-x  3 staff  102 Mar  7 17:05 src',
          '-rw-r--r--  1 staff  456 Mar  7 17:10 package.json',
        ];

        let lineIdx = 0;
        const interval = setInterval(() => {
          if (lineIdx < lines.length) {
            console.log('[MockWS] Sending TTY line for:', toolCallId);
            this.notify('tty_event', { toolCallId, text: lines[lineIdx] + '\n', isError: false });
            lineIdx++;
          } else {
            clearInterval(interval);
            this.activeIntervals = this.activeIntervals.filter((id) => id !== interval);
            this.notify('server_event', {
              eventId: 'tr-' + Date.now(),
              type: 'TOOL_RESULT',
              data: {
                toolCallId,
                exitCode: 0,
                summary: 'Found ' + (lines.length - 1) + ' files in current directory',
                fullOutput: lines.join('\n'),
                isError: false,
              },
            });
            this.simulateTypewriterResponse(
              'I have listed the files for you. You can click the "Logs" button in the tool card above to see the full output in the Inspector.',
            );
          }
        }, 200);
        this.activeIntervals.push(interval);
      }

      private simulateAskWithDiff() {
        this.notify('server_event', {
          eventId: 'ask-1',
          type: 'ASK_USER',
          data: {
            askId: 'ask-' + Date.now(),
            questions: [
              {
                question: 'I have prepared a diff for src/main.ts. Should I apply these changes?',
                header: 'Diff',
                type: 'choice',
                options: [
                  {
                    value: 'yes',
                    label: 'Yes, apply it',
                    description: 'Apply the changes to the file',
                  },
                  { value: 'no', label: 'No, cancel', description: 'Discard the changes' },
                ],
              },
            ],
            diffContext:
              "--- a/src/main.ts\n+++ b/src/main.ts\n@@ -1,5 +1,6 @@\n import { createApp } from 'vue'\n+import { createPinia } from 'pinia'\n import App from './App.vue'\n \n createApp(App).mount('#app')\n",
          },
        });
      }

      private simulateAskSelection() {
        this.notify('server_event', {
          eventId: 'ask-2',
          type: 'ASK_USER',
          data: {
            askId: 'ask-' + Date.now(),
            questions: [
              {
                question:
                  'I found multiple potential matches for your request. Please select the most relevant section from the documentation below or provide more details.',
                header: 'Selection',
                type: 'choice',
                options: [
                  {
                    value: 'arch',
                    label: 'Hexagonal Architecture',
                    description: 'Deep dive into Ports and Adapters implementation in Ganglia.',
                  },
                  {
                    value: 'reactive',
                    label: 'Reactive Patterns',
                    description: 'How Vert.x event loop handles non-blocking I/O operations.',
                  },
                  {
                    value: 'memory',
                    label: 'Memory System',
                    description: 'Context compression and long-term knowledge storage strategies.',
                  },
                  {
                    value: 'custom',
                    label: 'Custom Option',
                    description: 'I want to provide my own specific instructions instead.',
                  },
                ],
              },
            ],
          },
        });
      }

      private simulatePlanUpdate() {
        this.notify('server_event', {
          eventId: 't-plan',
          type: 'THOUGHT',
          data: { content: 'Creating a plan for your request...' },
        });

        const plan = {
          items: [
            {
              id: '1',
              description: 'Research codebase structure',
              status: 'DONE',
              result: 'Found core modules and documentation.',
            },
            { id: '2', description: 'Implement new feature', status: 'IN_PROGRESS' },
            { id: '3', description: 'Write unit tests', status: 'TODO' },
          ],
        };

        setTimeout(() => {
          this.notify('server_event', {
            eventId: 'plan-1',
            type: 'PLAN_UPDATED',
            data: { plan },
          });
          this.simulateTypewriterResponse(
            'I have created a plan for this task. You can see the current progress in the Plan Sidebar.',
          );
        }, 1000);
      }

      respond(id: string | number, result: unknown) {
        const response: JsonRpcResponse = { jsonrpc: '2.0', id, result };
        setTimeout(() => this.onmessage && this.onmessage({ data: JSON.stringify(response) }), 100);
      }
      notify(method: string, params: unknown) {
        const notification: JsonRpcNotification = { jsonrpc: '2.0', method, params };
        setTimeout(
          () => this.onmessage && this.onmessage({ data: JSON.stringify(notification) }),
          10,
        );
      }
      close() {
        this.clearSimulations();
      }
    };
  }

  connect() {
    if (
      this.ws &&
      (this.ws.readyState === WebSocket.CONNECTING || this.ws.readyState === WebSocket.OPEN)
    ) {
      console.log('WebSocket is already connected or connecting. Skipping duplicate connect.');
      return;
    }

    const systemStore = useSystemStore.getState();
    console.log(`Connecting to WebSocket at ${this.url}...`);

    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      systemStore.setStatus('CONNECTED');
      this.retryCount = 0;

      // Request history sync
      this.send('SYNC', {})
        .then((reply: unknown) => {
          const syncReply = reply as { history?: ServerEvent[] };
          if (syncReply && syncReply.history) {
            const logStore = useLogStore.getState();
            logStore.clear();
            const history = syncReply.history;
            history.forEach((event: ServerEvent) => logStore.addEvent(event));

            if (!logStore.fileTree) {
              this.send('LIST_FILES', {});
            }
          } else {
            // If sync fails or no history, at least get the file tree
            this.send('LIST_FILES', {});
          }
        })
        .catch((err) => {
          console.error('SYNC failed', err);
          this.send('LIST_FILES', {});
        });
    };

    this.ws.onmessage = (event) => {
      try {
        const json = JSON.parse(event.data);

        // Handle JSON-RPC responses
        if (json.id !== undefined && (json.result !== undefined || json.error !== undefined)) {
          const res = json as JsonRpcResponse;
          const pending = this.pendingRequests.get(res.id);
          if (pending) {
            this.pendingRequests.delete(res.id);
            if (res.error) pending.reject(res.error);
            else pending.resolve(res.result);
          }
          return;
        }

        // Handle JSON-RPC notifications (server -> client events)
        const notification = json as JsonRpcNotification;
        if (notification.method === 'server_event' && notification.params) {
          this.handleServerEvent(notification.params as ServerEvent);
        } else if (notification.method === 'tty_event' && notification.params) {
          this.handleTtyEvent(notification.params as TtyData);
        }
      } catch (err) {
        console.error('Failed to parse websocket message', err);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket disconnected');
      systemStore.setStatus('DISCONNECTED');
      if (!this.isManualClosed) {
        this.reconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error', error);
    };
  }

  private reconnect() {
    if (this.retryCount >= this.maxRetryCount) return;

    const systemStore = useSystemStore.getState();
    systemStore.setStatus('RECONNECTING');

    this.retryCount++;
    const delay = Math.min(1000 * Math.pow(2, this.retryCount), 30000);

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  send(action: 'SYNC', payload: SyncParams): Promise<unknown>;
  send(action: 'LIST_FILES', payload: ListFilesParams): Promise<unknown>;
  send(action: 'START', payload: StartParams): Promise<unknown>;
  send(action: 'RESPOND_ASK', payload: RespondAskParams): Promise<unknown>;
  send(action: 'READ_FILE', payload: ReadFileParams): Promise<unknown>;
  send(action: 'CANCEL' | 'RETRY', payload: Record<string, never>): Promise<unknown>;
  send(action: ClientAction, payload: unknown): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const systemStore = useSystemStore.getState();
      if (systemStore.status !== 'CONNECTED' || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
        reject(new Error('WebSocket not connected'));
        return;
      }

      this.requestCounter++;
      const id = this.requestCounter;

      // Merge sessionId into params
      const params = {
        ...(payload as Record<string, unknown>),
        sessionId: systemStore.sessionId,
      };

      const request: JsonRpcRequest<Record<string, unknown>> = {
        jsonrpc: '2.0',
        method: action,
        params,
        id,
      };

      this.pendingRequests.set(id, { resolve, reject });
      this.ws.send(JSON.stringify(request));
    });
  }

  private handleServerEvent(event: ServerEvent) {
    console.debug('[WebSocket] Received event:', event.type, event);
    const systemStore = useSystemStore.getState();
    const logStore = useLogStore.getState();

    if (event.type === 'INIT_CONFIG') {
      const data = event.data as InitConfigData;
      if (data.workspacePath) systemStore.setWorkspacePath(data.workspacePath);
      if (data.sessionId) systemStore.setSessionId(data.sessionId);
      if (data.mcpCount !== undefined) useSystemStore.setState({ mcpCount: data.mcpCount });
      return;
    }

    if (event.type === 'FILE_TREE') {
      useSystemStore.setState({ fileTreeUpdatedAt: Date.now() });
    }

    if (event.type === 'ASK_USER') {
      const askData = event.data as { askId: string };
      useSystemStore.setState({ activeAskId: askData.askId });
    }

    if (event.type === 'PLAN_UPDATED') {
      const planStore = usePlanStore.getState();
      const planData = event.data as { plan?: ToDoList };
      if (planData && planData.plan) {
        planStore.setPlan(planData.plan);
      }
    }

    logStore.addEvent(event);
  }

  private handleTtyEvent(event: TtyData) {
    const logStore = useLogStore.getState();
    if (event.toolCallId && event.text) {
      logStore.appendTty(event.toolCallId, event.text);
    }
  }

  close() {
    this.isManualClosed = true;
    if (this.ws) {
      this.ws.close();
    }
  }
}

export const eventBusService = new EventBusService();
