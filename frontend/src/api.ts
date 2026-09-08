export interface LoginResult {
  token: string
  displayName: string
  employeeId: string
  role: string
}

export interface AgentResult {
  requestId: string
  answer: string
  intent: string
  tools: string[]
  citations: string[]
  traceId: string
  conversationId: string
  model: string
}

export interface ModelOption {
  id: string
  label: string
  active: boolean
}

interface AgentStreamError {
  code?: string
  message?: string
  traceId?: string
}

export interface KnowledgeChunk {
  documentId: string
  version: number
  chunkNumber: number
  title: string
  content: string
  scope: 'PUBLIC' | 'DEPARTMENT' | 'PERSONAL' | 'HR_ONLY'
  departments: string[]
  employeeIds: string[]
}

export interface IngestionJob {
  jobId: string
  documentId: string
  version: number
  status: 'PENDING' | 'PARSING' | 'EMBEDDING' | 'INDEXED' | 'FAILED'
  chunkCount: number
  errorCode?: string
}

export interface TraceNode {
  name: string
  startedAt: string
  endedAt: string
  durationMs: number
  summary: string
}

export interface AgentTrace {
  traceId: string
  username: string
  intent: string
  tools: string[]
  citations: string[]
  durationMs: number
  createdAt: string
  requestId: string
  runtime: string
  model: string
  status: string
  nodes: TraceNode[]
  retries: number
  errorCode?: string
}

export interface RunEvent {
  sequence: number
  event: string
  data: Record<string, unknown>
  createdAt: string
}

export interface RunCheckpoint {
  stage: string
  conversationId?: string
  intent?: string
  traceId?: string
  errorCode?: string
  updatedAt?: string
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export async function login(username: string, password: string): Promise<LoginResult> {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!response.ok) throw new Error('账号或密码错误')
  return response.json()
}

export async function chat(token: string, message: string, model?: string): Promise<AgentResult> {
  const response = await fetch(`${baseUrl}/api/agent/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ message, model })
  })
  if (response.status === 401 || response.status === 403) throw new Error('登录已失效，请重新登录')
  if (!response.ok) throw new Error('Agent 暂时无法回答，请稍后重试')
  return response.json()
}

export async function chatStream(
  token: string,
  message: string,
  conversationId: string | undefined,
  model: string | undefined,
  onStatus: (status: string) => void
): Promise<AgentResult> {
  const requestId = crypto.randomUUID()
  const response = await fetch(`${baseUrl}/api/agent/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ requestId, message, conversationId, model })
  })
  if (!response.ok || !response.body) throw new Error('Agent 流式服务暂时不可用')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let result: AgentResult | undefined
  let agentError: AgentStreamError | undefined

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const event = block.split(/\r?\n/).find(line => line.startsWith('event:'))?.slice(6).trim()
      const data = block.split(/\r?\n/).filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trim()).join('\n')
      if (!data) continue
      const parsed = JSON.parse(data)
      if (event === 'status') onStatus(parsed.message)
      if (event === 'tool_start') onStatus(`正在调用工具：${parsed.name}`)
      if (event === 'tool_result') onStatus(parsed.status === 'SUCCESS' ? `工具 ${parsed.name} 执行完成` : `工具 ${parsed.name} 执行失败`)
      if (event === 'answer') result = parsed as AgentResult
      if (event === 'error') agentError = parsed as AgentStreamError
    }
    if (done) break
  }
  if (!result && agentError) {
    const trace = agentError.traceId ? `，Trace ${agentError.traceId.slice(0, 8)}` : ''
    const code = agentError.code ? `${agentError.code}: ` : ''
    throw new Error(`${code}${agentError.message ?? 'Agent 执行失败'}${trace}`)
  }
  if (!result) throw new Error('Agent 未返回最终结果')
  return result
}

async function authorized<T>(token: string, path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...init?.headers }
  })
  if (response.status === 403) throw new Error('当前身份没有执行该操作的权限')
  if (!response.ok) throw new Error('请求失败，请稍后重试')
  return response.json()
}

export const getKnowledge = (token: string) => authorized<KnowledgeChunk[]>(token, '/api/knowledge')
export const getTraces = (token: string) => authorized<AgentTrace[]>(token, '/api/traces')
export const getModels = (token: string) => authorized<ModelOption[]>(token, '/api/agent/models')
export const getRunEvents = (token: string, runId: string, after = 0) =>
  authorized<RunEvent[]>(token, `/api/agent/runs/${encodeURIComponent(runId)}/events?after=${after}`)
export const getRunCheckpoint = (token: string, runId: string) =>
  authorized<RunCheckpoint>(token, `/api/agent/runs/${encodeURIComponent(runId)}/checkpoint`)

export async function uploadKnowledgeDocument(token: string, file: File, metadata: {
  title: string; scope: KnowledgeChunk['scope']; departments?: string[]; employeeIds?: string[]
}): Promise<IngestionJob> {
  const form = new FormData()
  form.append('file', file)
  form.append('title', metadata.title)
  form.append('scope', metadata.scope)
  for (const department of metadata.departments ?? []) form.append('departments', department)
  for (const employeeId of metadata.employeeIds ?? []) form.append('employeeIds', employeeId)
  const response = await fetch(`${baseUrl}/api/knowledge/documents`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form
  })
  if (!response.ok) throw new Error('文档上传失败')
  return response.json()
}

export const getIngestionJob = (token: string, jobId: string) =>
  authorized<IngestionJob>(token, `/api/knowledge/jobs/${encodeURIComponent(jobId)}`)

export const reindexDocument = (token: string, documentId: string) =>
  authorized<IngestionJob>(token, `/api/knowledge/documents/${encodeURIComponent(documentId)}/reindex`, { method: 'POST' })

export async function deleteDocument(token: string, documentId: string): Promise<void> {
  const response = await fetch(`${baseUrl}/api/knowledge/documents/${encodeURIComponent(documentId)}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
  })
  if (!response.ok) throw new Error('删除文档失败')
}

export async function clearConversation(token: string, conversationId: string): Promise<void> {
  const response = await fetch(`${baseUrl}/api/agent/conversations/${encodeURIComponent(conversationId)}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
  })
  if (!response.ok) throw new Error('无法清除当前会话')
}

export const createKnowledge = (token: string, input: {
  documentId: string; title: string; content: string; scope: KnowledgeChunk['scope']; departments: string[]
}) => authorized<KnowledgeChunk>(token, '/api/knowledge', { method: 'POST', body: JSON.stringify(input) })
