<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import {
  chatStream,
  clearConversation,
  createKnowledge,
  getKnowledge,
  getModels,
  getTraces,
  login,
  type AgentResult,
  type AgentTrace,
  type KnowledgeChunk,
  type LoginResult,
  type ModelOption
} from './api'

interface Message { role: 'user' | 'assistant'; content: string; meta?: AgentResult }
type ViewName = 'chat' | 'knowledge' | 'traces'

const session = ref<LoginResult | null>(null)
const username = ref('employee'), password = ref('employee123'), loginError = ref('')
const loginBusy = ref(false), input = ref(''), busy = ref(false), status = ref('')
const conversationId = ref('')
const view = ref<ViewName>('chat'), chatBody = ref<HTMLElement | null>(null)
const panelBusy = ref(false), panelError = ref(''), showKnowledgeForm = ref(false)
const knowledge = ref<KnowledgeChunk[]>([]), traces = ref<AgentTrace[]>([])
const models = ref<ModelOption[]>([]), selectedModel = ref('')
const visibleModels = computed(() => models.value.length ? models.value : fallbackModels(selectedModel.value || 'deepseek-chat'))
const newDocument = reactive({ documentId: '', title: '', content: '', scope: 'PUBLIC' as KnowledgeChunk['scope'], department: '' })
const messages = ref<Message[]>([{ role: 'assistant', content: '你好，我是人力知识助手。你可以询问个人档案、考勤、年假余额、审批状态和公司制度。' }])
const suggestions = ['查询我的员工档案', '我还有多少年假？', '我的审批进度', '研发部可以弹性上班吗？']
const header = computed(() => ({
  chat: ['SECURE ASSISTANT', '人力知识助手'],
  knowledge: ['PERMISSION-AWARE RAG', '知识库'],
  traces: ['OBSERVABILITY', '执行追踪']
}[view.value]))

async function submitLogin() {
  loginBusy.value = true
  loginError.value = ''
  try {
    session.value = await login(username.value, password.value)
    try { models.value = await getModels(session.value.token) }
    catch { models.value = fallbackModels() }
    selectedModel.value = models.value.find(model => model.active)?.id || models.value[0]?.id || ''
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loginBusy.value = false
  }
}

async function navigate(target: ViewName) {
  view.value = target
  panelError.value = ''
  if (!session.value || target === 'chat') return
  panelBusy.value = true
  try {
    if (target === 'knowledge') knowledge.value = await getKnowledge(session.value.token)
    if (target === 'traces') traces.value = await getTraces(session.value.token)
  } catch (error) {
    panelError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    panelBusy.value = false
  }
}

async function send(preset?: string) {
  const content = (preset ?? input.value).trim()
  if (!content || busy.value || !session.value) return
  messages.value.push({ role: 'user', content })
  input.value = ''
  busy.value = true
  status.value = '正在建立安全执行上下文'
  await scrollBottom()
  try {
    const result = await chatStream(
      session.value.token,
      content,
      conversationId.value || undefined,
      selectedModel.value || undefined,
      value => { status.value = value }
    )
    conversationId.value = result.conversationId
    rememberModel(result.model)
    messages.value.push({ role: 'assistant', content: result.answer, meta: result })
  } catch (error) {
    messages.value.push({ role: 'assistant', content: error instanceof Error ? error.message : '请求失败' })
  } finally {
    busy.value = false
    status.value = ''
    await scrollBottom()
  }
}

async function saveKnowledge() {
  if (!session.value) return
  panelBusy.value = true
  panelError.value = ''
  try {
    await createKnowledge(session.value.token, {
      documentId: newDocument.documentId,
      title: newDocument.title,
      content: newDocument.content,
      scope: newDocument.scope,
      departments: newDocument.department ? [newDocument.department] : []
    })
    Object.assign(newDocument, { documentId: '', title: '', content: '', scope: 'PUBLIC', department: '' })
    showKnowledgeForm.value = false
    knowledge.value = await getKnowledge(session.value.token)
  } catch (error) {
    panelError.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    panelBusy.value = false
  }
}

async function scrollBottom() {
  await nextTick()
  chatBody.value?.scrollTo({ top: chatBody.value.scrollHeight, behavior: 'smooth' })
}

function logout() {
  session.value = null
  view.value = 'chat'
  models.value = []
  selectedModel.value = ''
  messages.value = messages.value.slice(0, 1)
}

async function startNewConversation() {
  if (!session.value || busy.value) return
  if (conversationId.value) await clearConversation(session.value.token, conversationId.value)
  conversationId.value = ''
  messages.value = messages.value.slice(0, 1)
}

function fallbackModels(active = 'deepseek-chat'): ModelOption[] {
  const configured = (import.meta.env.VITE_AGENT_MODELS || active)
    .split(',')
    .map((item: string) => item.trim())
    .filter(Boolean)
  const values = configured.includes(active) ? configured : [active, ...configured]
  return Array.from(new Set<string>(values)).map(id => ({ id, label: id, active: id === active }))
}

function rememberModel(model: string) {
  if (!model) return
  if (!models.value.some(item => item.id === model)) {
    models.value = [{ id: model, label: model, active: true }, ...models.value]
  }
  selectedModel.value ||= model
}

const scopeText = (scope: KnowledgeChunk['scope']) => ({
  PUBLIC: '全员公开',
  DEPARTMENT: '部门可见',
  PERSONAL: '个人可见',
  HR_ONLY: '仅 HR'
}[scope])
</script>

<template>
  <main v-if="!session" class="login-page">
    <section class="login-hero">
      <div class="brand-mark">HR</div>
      <p class="eyebrow">LNXX INTELLIGENCE</p>
      <h1>让人力制度与业务数据<br><span>真正回答员工问题</span></h1>
      <p class="hero-copy">权限感知检索、业务工具调用与全链路追踪，构成可信赖的人力知识 Agent。</p>
      <div class="hero-points"><span>权限隔离</span><span>知识引用</span><span>执行可追踪</span></div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submitLogin">
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录工作台</h2>
        <p class="muted">使用员工身份体验权限感知问答</p>
        <label>账号<input v-model="username" autocomplete="username" /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
        <p v-if="loginError" class="error">{{ loginError }}</p>
        <button class="primary" :disabled="loginBusy">{{ loginBusy ? '正在验证...' : '进入 Agent' }}</button>
        <p class="demo-tip">演示员工：employee / employee123<br>演示 HR：hr / hr123456</p>
      </form>
    </section>
  </main>

  <main v-else class="workspace">
    <aside class="sidebar">
      <div>
        <div class="logo"><b>HR</b><span>人力知识 Agent</span></div>
        <nav>
          <button :class="{ active: view === 'chat' }" @click="navigate('chat')">◆ 智能问答</button>
          <button :class="{ active: view === 'knowledge' }" @click="navigate('knowledge')">▣ 知识库</button>
          <button :class="{ active: view === 'traces' }" @click="navigate('traces')">⌁ 执行追踪</button>
        </nav>
      </div>
      <div class="user-card">
        <div class="avatar">{{ session.displayName.slice(0, 1) }}</div>
        <div><b>{{ session.displayName }}</b><small>{{ session.employeeId }} · {{ session.role }}</small></div>
        <button @click="logout">退出</button>
      </div>
    </aside>

    <section class="chat-shell">
      <header>
        <div><p class="eyebrow">{{ header[0] }}</p><h2>{{ header[1] }}</h2></div>
        <div class="header-actions">
          <label v-if="view === 'chat' && visibleModels.length" class="model-select">
            <span>LLM</span>
            <select v-model="selectedModel" :disabled="busy">
              <option v-for="model in visibleModels" :key="model.id" :value="model.id">{{ model.label }}</option>
            </select>
          </label>
          <button v-if="view === 'chat'" class="ghost" :disabled="busy" @click="startNewConversation">新会话</button>
          <span class="online"><i></i>服务正常</span>
        </div>
      </header>

      <template v-if="view === 'chat'">
        <div ref="chatBody" class="chat-body">
          <div class="day-label">今天</div>
          <article v-for="(message, index) in messages" :key="index" :class="['message', message.role]">
            <div v-if="message.role === 'assistant'" class="bot-avatar">AI</div>
            <div class="bubble">
              <p>{{ message.content }}</p>
              <div v-if="message.meta" class="meta">
                <span>意图 · {{ message.meta.intent }}</span>
                <span v-for="tool in message.meta.tools" :key="tool">工具 · {{ tool }}</span>
                <span v-for="citation in message.meta.citations" :key="citation" class="citation">{{ citation }}</span>
                <small>{{ message.meta.model }}</small>
                <small>Trace {{ message.meta.traceId.slice(0, 8) }}</small>
              </div>
            </div>
          </article>
          <div v-if="busy" class="thinking"><span></span><span></span><span></span>{{ status }}</div>
        </div>
        <footer>
          <div class="suggestions"><button v-for="item in suggestions" :key="item" @click="send(item)">{{ item }}</button></div>
          <form class="composer" @submit.prevent="send()">
            <textarea v-model="input" rows="1" maxlength="500" placeholder="询问人事制度、考勤、假期或审批..." @keydown.enter.exact.prevent="send()"></textarea>
            <button :disabled="busy || !input.trim()">发送</button>
          </form>
          <p>Agent 的回答可能存在偏差，重要人事事项请联系 HR 复核。</p>
        </footer>
      </template>

      <div v-else-if="view === 'knowledge'" class="panel-body">
        <div class="panel-toolbar">
          <div><b>我可访问的知识</b><p>列表已按当前用户的数据权限过滤</p></div>
          <button v-if="session.role === 'HR' || session.role === 'ADMIN'" class="action" @click="showKnowledgeForm = !showKnowledgeForm">{{ showKnowledgeForm ? '取消' : '+ 新增知识' }}</button>
        </div>
        <form v-if="showKnowledgeForm" class="knowledge-form" @submit.prevent="saveKnowledge">
          <input v-model="newDocument.documentId" required pattern="[A-Z0-9_-]{3,64}" placeholder="文档 ID，如 POLICY-001" />
          <input v-model="newDocument.title" required placeholder="文档标题" />
          <select v-model="newDocument.scope"><option value="PUBLIC">全员公开</option><option value="DEPARTMENT">部门可见</option><option value="HR_ONLY">仅 HR</option></select>
          <input v-if="newDocument.scope === 'DEPARTMENT'" v-model="newDocument.department" required placeholder="可见部门" />
          <textarea v-model="newDocument.content" required maxlength="4000" rows="4" placeholder="知识内容"></textarea>
          <button class="action" :disabled="panelBusy">保存并入库</button>
        </form>
        <p v-if="panelError" class="error panel-error">{{ panelError }}</p>
        <div v-if="panelBusy" class="empty">正在加载...</div>
        <div v-else class="knowledge-grid">
          <article v-for="item in knowledge" :key="`${item.documentId}-${item.chunkNumber}`">
            <div class="doc-head"><span>{{ item.documentId }} · {{ item.chunkNumber }}</span><em>{{ scopeText(item.scope) }}</em></div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
            <small v-if="item.departments.length">部门：{{ item.departments.join('、') }}</small>
          </article>
        </div>
      </div>

      <div v-else class="panel-body">
        <div class="panel-toolbar"><div><b>最近 20 条执行记录</b><p>记录路由、工具、引用、模型和耗时</p></div><button class="ghost" @click="navigate('traces')">刷新</button></div>
        <p v-if="panelError" class="error panel-error">{{ panelError }}</p>
        <div v-if="panelBusy" class="empty">正在加载...</div>
        <div v-else-if="!traces.length" class="empty">暂无执行记录，请先与 Agent 对话。</div>
        <div v-else class="trace-list">
          <article v-for="trace in traces" :key="trace.traceId">
            <div class="trace-icon">✓</div>
            <div>
              <div class="trace-title"><b>{{ trace.intent }}</b><span>{{ new Date(trace.createdAt).toLocaleString() }}</span></div>
              <p><span v-if="trace.tools.length">工具：{{ trace.tools.join('、') }}</span><span>模型：{{ trace.model || 'unknown' }}</span><span>耗时：{{ trace.durationMs }} ms</span></p>
              <small>{{ trace.traceId }}</small>
            </div>
          </article>
        </div>
      </div>
    </section>
  </main>
</template>
