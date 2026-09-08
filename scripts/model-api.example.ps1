# Copy this file to scripts/model-api.local.ps1, fill your keys, then run:
# . .\scripts\model-api.local.ps1
#
# Do not commit model-api.local.ps1 or real API keys.

# Models shown by the Java Backend / frontend.
$env:AGENT_MODELS = "deepseek-chat,qwen-plus,qwen-max,doubao-pro-32k,doubao-lite-32k,gpt-4o-mini,demo-rule-agent"

# Models accepted by the Python Agent.
$env:HR_AGENT_MODEL_OPTIONS = $env:AGENT_MODELS
$env:HR_AGENT_MODEL_NAME = "deepseek-chat"

# DeepSeek
$env:HR_AGENT_DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"
$env:HR_AGENT_DEEPSEEK_API_KEY = "replace-with-your-deepseek-key"

# Qwen / DashScope
$env:HR_AGENT_QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:HR_AGENT_QWEN_API_KEY = "replace-with-your-qwen-key"

# Doubao / Volcano Ark
$env:HR_AGENT_DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
$env:HR_AGENT_DOUBAO_API_KEY = "replace-with-your-doubao-key"

# Optional generic OpenAI-compatible provider.
$env:HR_AGENT_MODEL_BASE_URL = ""
$env:HR_AGENT_MODEL_API_KEY = ""

# Java Spring AI runtime. Used when AGENT_RUNTIME=spring-ai.
$env:CHAT_MODEL_BASE_URL = "https://api.deepseek.com"
$env:CHAT_MODEL_API_KEY = "replace-with-your-chat-key"
$env:CHAT_MODEL_NAME = "deepseek-chat"

# Embedding is separate from chat. Use an embedding-compatible endpoint/model.
$env:EMBEDDING_MODEL_BASE_URL = "https://api.openai.com"
$env:EMBEDDING_MODEL_API_KEY = "replace-with-your-embedding-key"
$env:EMBEDDING_MODEL_NAME = "text-embedding-3-small"
$env:EMBEDDING_DIMENSION = "1536"
