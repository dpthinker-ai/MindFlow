# Cloud AI Provider and Runtime Policy Design

## 背景

MindFlow 当前已经有两条 AI 路径：

- 端侧路径：`OnDeviceAiClient`、`OnDeviceAiTaskProvider`、`LocalKnowledgeMaintenancePlanner`
- 云侧路径：`AiServiceClient`、`CloudAiTaskProvider`、若干直接调用 `AiServiceClient` 的 planner

前一轮 `Gemma 4 Hybrid AI Routing Design` 已经建立了 `AiTaskRouter`，但云侧 provider 仍然偏“单一 OpenAI-compatible HTTP client”。DeepSeek 接入本身不难，因为 DeepSeek 官方接口兼容 OpenAI Chat Completions；真正需要解决的是：

1. 未来还会接入更多云侧 API，不能每次都在 `AiServiceClient` 里补一个 provider 特例。
2. 当前 `AiProviderPreset` 只保存 label、baseUrl、defaultModel，表达不了鉴权、请求字段、thinking 支持、vision/audio/json 能力等差异。
3. `AiExecutionMode` 放在 `OnDeviceModelSettings` 里，语义上混淆了“本地模型下载状态”和“AI 运行策略”。
4. 一些功能已经接入 `AiTaskRouter`，但 Daily Brief、Next Action、Weekly Review、Thread Execution、External Research、Stale Reconnect 等仍直接调用云侧 client。
5. 项目原则要求 local-first，但用户也接受后台自动任务使用云侧，前提是用户能够被知会，并且本地有可追踪记录。

因此，本设计不是单独“加 DeepSeek”，而是把云侧 provider、端云运行策略、payload 最小化、云侧使用审计、低频通知一起收成一个可扩展边界。

## 依据

截至 2026-05-24，DeepSeek 官方文档显示：

- OpenAI-compatible base URL 为 `https://api.deepseek.com`
- Chat Completions 路径为 `/chat/completions`
- 当前模型为 `deepseek-v4-flash`、`deepseek-v4-pro`
- `deepseek-chat`、`deepseek-reasoner` 将在 2026-07-24 废弃
- DeepSeek V4 支持 thinking、reasoning effort、JSON output、tool calls 等能力

实现时以官方文档为准，不把旧模型名作为默认值。

## 目标

1. 新增通用云侧 provider registry，使 DeepSeek、OpenAI、智谱、自定义 OpenAI-compatible provider 都通过同一个 spec 描述。
2. 将云侧调用从“硬编码 HTTP 请求”升级为 provider-aware request adapter。
3. 把 AI 运行策略从本地模型设置里拆出，形成独立的 `AiRuntimeSettings`。
4. 建立任务策略表，统一决定每类任务的 provider 顺序、fallback、后台云侧许可、payload 发送级别、告知方式。
5. 所有云侧调用都生成本地 `AiUsageEvent`，用户可以回看 provider、model、任务、触发方式、是否 fallback、token 数等信息。
6. 后台云侧使用允许发生，但必须低频合并通知；前台用户等待的交互任务要即时提示。
7. 对后台云侧调用加入敏感度、最小发送、成本预算三层保护。

## 非目标

1. 不实现插件式 provider 系统，不从远端动态下载 provider 配置。
2. 不在首版接入 Anthropic-native、Gemini-native 或其他非 Chat Completions 协议。
3. 不重写全部 prompt，也不一次性迁移所有 planner；先建立可逐步迁移的策略层。
4. 不把云侧变成长期记忆层；云侧仍然是 stateless inference。
5. 不记录用户正文、完整 prompt 或 API key 到使用记录。
6. 不改变 release APK 验证规则，不触碰端侧模型文件、用户数据或 emulator destructive reset 流程。

## 产品决策

### 1. 自动模式的新语义

`自动` 不再简单等同于“总是本地优先”。新的用户可理解语义是：

> MindFlow 会按任务选择本地或云端；凡是内容离开设备，都会提示并留下记录。

具体行为由任务策略表决定：

- 后台本地知识维护默认端侧优先。
- 长上下文回看聊天可以云侧优先。
- 端侧失败后的云侧 fallback 必须记录 fallback reason。
- 高敏感内容默认不允许后台云侧，除非用户显式触发该动作。

### 2. 后台允许云侧，但必须低频告知

用户已确认：后台自动任务可以使用云侧，但通知应合并成低频摘要。

规则：

1. 前台交互任务即时提示。
2. 后台自动任务进入通知聚合器。
3. 一批后台任务结束后，延迟 5 到 10 分钟合并通知。
4. 如果后台任务持续发生，最多 30 分钟 flush 一次。
5. 每天最多发送 3 条后台云侧通知。
6. 通知不暴露正文，只展示数量、任务类型和 provider。
7. 用户打开 App 时，如果存在未展示的后台云侧使用，可在设置页或使用记录入口显示轻量提示。
8. 如果系统通知权限未开启，后台云侧使用不强行请求权限；改为在 App 内保留未读提示和使用记录入口。

示例通知：

`MindFlow 已用 DeepSeek 完成 4 项后台整理：方向判断、外部线索、回看摘要。`

### 3. 所有云侧使用都审计

Toast 和系统通知不是事实源。事实源是本地 `AiUsageEvent`。

每次云侧调用，无论前台还是后台，都记录：

- eventId
- timestamp
- taskType
- triggerSurface
- triggerMode
- providerId
- providerLabel
- model
- executionMode
- providerSelectionReason
- fallbackOccurred
- fallbackReason
- dataSensitivity
- payloadPolicy
- tokenCount
- success
- failureReason
- notifiedAt
- notificationBatchId

不记录：

- API key
- 原始正文
- 完整 prompt
- 附件路径
- 本地文件绝对路径

保留策略：

- 默认保留最近 90 天 usage event。
- 若事件数量超过 1000 条，保留最新 1000 条。
- 聚合统计可以长期保留每日 totals，但不保留正文级信息。
- 清空云端 AI 设置时，不自动删除 usage event；用户可在使用记录页单独清空审计记录。

### 4. 最小发送原则

所有云侧调用都经过 `PromptPayloadPolicy`：

- 能发摘要就不发全文。
- 能发局部 snippet 就不发整条记录。
- 后台任务默认只发摘要、结构信息、selected snippets。
- 高敏感内容默认不后台上云。
- 用户显式触发全文分析时，才允许 `full_note_explicit`。

payload policy 需要写入使用记录，方便用户理解“这次发出去了什么级别的数据”，例如：

- `metadata_only`
- `summary_only`
- `selected_snippets`
- `single_note_excerpt`
- `full_note_explicit`
- `multi_note_compact_context`

## 架构设计

### 模块总览

新增或重构为以下边界：

1. `CloudAiProviderSpec`
   描述云侧 provider 的协议、鉴权、模型和能力。

2. `CloudAiProviderRegistry`
   集中列出内置 provider：DeepSeek、OpenAI、智谱、自定义 OpenAI-compatible。

3. `CloudAiRequestAdapter`
   根据 provider spec 构造 URL、headers、body，并解析响应。

4. `AiRuntimeSettings`
   保存全局运行策略、后台云侧许可、告知策略和预算开关。

5. `AiTaskPolicyRegistry`
   按任务声明 provider 顺序、fallback、敏感度、payload policy、告知方式。

6. `AiUsageEventRepository`
   本地持久化云侧使用事件。

7. `AiCloudUsageReporter`
   统一记录云侧使用，并分发给前台提示或后台通知聚合器。

8. `CloudUsageNotificationAggregator`
   合并后台云侧使用，低频发送系统通知。

9. `CloudUsageBudgetGuard`
   控制每日后台云侧请求数和 token 软上限。

### Provider Spec

建议模型：

```kotlin
data class CloudAiProviderSpec(
    val id: String,
    val label: String,
    val protocol: CloudAiProtocol,
    val baseUrl: String,
    val chatPath: String,
    val authScheme: CloudAiAuthScheme,
    val defaultModel: String,
    val selectableModels: List<CloudAiModelSpec>,
    val requestCapabilities: CloudAiRequestCapabilities,
    val deprecatedModelAliases: Map<String, String> = emptyMap(),
)
```

关键字段：

- `protocol`
  首版支持 `OPENAI_CHAT_COMPLETIONS`。

- `authScheme`
  支持 `BEARER_ONLY`、`RAW_KEY_ONLY`、`RAW_KEY_THEN_BEARER`、`BEARER_THEN_RAW_KEY`。

- `requestCapabilities`
  控制是否允许发送 `thinking`、`reasoning_effort`、`response_format`、`tools`、`vision`、`audio` 字段。

DeepSeek spec：

```kotlin
CloudAiProviderSpec(
    id = "deepseek",
    label = "DeepSeek",
    protocol = OPENAI_CHAT_COMPLETIONS,
    baseUrl = "https://api.deepseek.com",
    chatPath = "/chat/completions",
    authScheme = BEARER_ONLY,
    defaultModel = "deepseek-v4-flash",
    selectableModels = listOf(
        CloudAiModelSpec("deepseek-v4-flash", "V4 Flash", defaultThinking = false),
        CloudAiModelSpec("deepseek-v4-pro", "V4 Pro", defaultThinking = true),
    ),
    requestCapabilities = CloudAiRequestCapabilities(
        supportsThinking = true,
        supportsReasoningEffort = true,
        supportsJsonObject = true,
        supportsToolCalls = true,
        supportsVision = false,
        supportsAudio = false,
    ),
    deprecatedModelAliases = mapOf(
        "deepseek-chat" to "deepseek-v4-flash",
        "deepseek-reasoner" to "deepseek-v4-flash",
    ),
)
```

### Settings 拆分

当前 `AiSettings` 和 `OnDeviceModelSettings` 承担了过多语义。建议拆成：

1. `CloudAiSettings`
   - providerId
   - baseUrl
   - model
   - apiKey
   - enabled
   - lastVerifiedAt
   - lastVerifiedSuccess
   - usage counters

2. `OnDeviceModelSettings`
   - modelLabel
   - downloadUrl
   - localModelPath
   - downloadedBytes
   - status
   - warm-up state

3. `AiRuntimeSettings`
   - executionMode
   - cloudAllowedForInteractive
   - cloudAllowedForBackground
   - notifyOnCloudUse
   - backgroundCloudNotificationMode
   - dailyBackgroundCloudRequestLimit
   - dailyBackgroundTokenSoftLimit

兼容迁移：

- 旧 `AiSettings` 迁移到 `CloudAiSettings`。
- 旧 `OnDeviceModelSettings.executionMode` 迁移到 `AiRuntimeSettings.executionMode`。
- `OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = null)` 应迁移为 `AUTOMATIC`，避免新安装误落到 `CLOUD_ONLY`。
- 保留旧 key 读取，但新保存写入新 DataStore。

### 任务策略表

`AiTaskPolicyRegistry` 是端云行为的唯一入口。业务层不再散落写 `PREFER_CLOUD`、`allowProviderFallback`。

首版策略：

| Task | Surface | Default order | Cloud fallback | Background cloud | Sensitivity | Payload policy | Notify |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `POLISH_CONTENT` | foreground | cloud -> on-device | yes | no | medium | single_note_excerpt | toast |
| `POLISH_TITLE` | foreground | cloud -> on-device | yes | no | medium | selected_snippets | toast |
| `REVIEW_CHAT_REPLY` | foreground | cloud -> on-device | yes | no | medium/high by query | multi_note_compact_context | inline + toast |
| `REVIEW_CHAT_QUERY_PLAN` | foreground/background helper | cloud -> rule | no | yes only when chat foreground | low | metadata_only | none if foreground hidden helper |
| `NOTE_INSIGHT` | background | on-device -> cloud | yes | yes | medium | summary_or_excerpt | aggregated |
| `EXTRACT_TOPIC` | background | on-device -> cloud | yes | yes | low | selected_snippets | aggregated |
| `EXTRACT_TAGS` | background | on-device -> cloud | yes | yes | low | selected_snippets | aggregated |
| `CLASSIFY_CATEGORY` | background | on-device -> rule/cloud | yes | yes | low | selected_snippets | aggregated |
| `GRAPH_EXTRACT_CONCEPTS` | background/manual | on-device -> cloud | yes | yes | medium | multi_note_compact_context | aggregated |
| `GRAPH_CANONICALIZE_CONCEPTS` | background/manual | on-device -> cloud | yes | yes | medium | metadata_only | aggregated |
| `GRAPH_GENERATE_RELATIONS` | background/manual | on-device -> cloud | yes | yes | medium | multi_note_compact_context | aggregated |
| `LOCAL_KNOWLEDGE_MAINTENANCE` | background/manual | on-device -> cloud | yes | yes | medium/high | compact_memory_context | aggregated |
| `DAILY_BRIEF` | background | rule/on-device -> cloud | yes | yes | medium | compact_summary | aggregated |
| `NEXT_ACTION` | background | rule/on-device -> cloud | yes | yes | medium | single_note_excerpt | aggregated |
| `WEEKLY_REVIEW` | background | rule/on-device -> cloud | yes | yes | medium | compact_summary | aggregated |
| `THREAD_EXECUTION` | background/foreground | rule/on-device -> cloud | yes | yes | medium | compact_thread_context | aggregated or inline |
| `EXTERNAL_RESEARCH` | background/foreground | rule/cloud | yes | yes | low/medium | metadata_and_summary | aggregated or inline |
| `TRANSCRIBE_AUDIO` | foreground | on-device only | no | no | high | local_file_only | inline |
| `UNDERSTAND_IMAGE` | foreground | on-device only | no | no | high | local_file_only | inline |

Notes:

- `TRANSCRIBE_AUDIO` 和 `UNDERSTAND_IMAGE` 首版不走云侧，除非未来单独做显式同意。
- `HIGH` sensitivity 在后台默认不允许云侧，即使 `cloudAllowedForBackground = true`。
- 表中 `medium/high` 表示任务需要运行敏感度分类器；若实际输入被判定为 `HIGH`，后台云侧路径必须被阻断。
- `rule -> cloud` 表示先用确定性规则生成可用结果，再用云侧增强；云侧失败不影响基础结果。
- 回看聊天的 hidden helper 可以使用云侧 query planning，但不单独通知；最终回答的云侧使用会覆盖这次交互。

### Provider Selection Reason

使用记录和 trace 中必须区分：

- `selected_by_policy`
  任务策略主动选择云侧。

- `fallback_from_on_device_empty`
  端侧空结果后回退云侧。

- `fallback_from_on_device_quality`
  端侧质量门未过后回退云侧。

- `fallback_from_on_device_unavailable`
  本地模型未就绪后回退云侧。

- `forced_by_user_cloud_only`
  用户选择仅云侧。

- `explicit_user_action`
  用户点了明确云侧操作。

这能避免用户把“云侧优先的交互任务”和“本地失败后被迫回退云侧”混在一起理解。

### 敏感内容分级

`AiDataSensitivityClassifier` 首版可以规则化，不引入复杂模型：

- `LOW`
  标题、标签、文件夹、非正文元数据、外部搜索词。

- `MEDIUM`
  普通笔记摘要、短摘录、多条记录压缩上下文。

- `HIGH`
  健康、财务、身份信息、账户凭据、联系人、完整原文、音频原文件、图片原文件、路径和附件。

行为：

- `LOW` 可后台云侧。
- `MEDIUM` 可后台云侧，但必须最小发送、记录、低频通知。
- `HIGH` 默认不后台云侧，只允许用户显式前台触发，并即时提示。

### 成本和频率预算

新增 `CloudUsageBudgetGuard`：

- 每日后台云侧请求默认软上限：30 次。
- 每日后台 token 默认软上限：可配置，首版可先按 token 统计但不强制中断，保守策略为超过后停止后台云侧增强。
- 前台用户显式触发不计入后台上限，但仍计入 usage event。
- 超限后自动降级到端侧或规则 fallback，并在使用记录中写入 `budget_blocked`。

## 数据流

### 前台交互

1. 用户触发操作，例如润色或回看聊天。
2. Feature 调用 `AiRuntimeOrchestrator.run(task, input, trigger = FOREGROUND_USER_ACTION)`。
3. Orchestrator 读取 `AiTaskPolicyRegistry` 和 runtime settings。
4. Payload policy 生成最小云侧 payload。
5. Provider adapter 执行云侧或端侧调用。
6. 若云侧被使用，`AiCloudUsageReporter.record(event)` 写入本地。
7. 前台 UI 收到 event，展示 Toast/snackbar 或 inline provider line。

### 后台自动任务

1. Flow、ReminderWorker 或本地维护链触发后台任务。
2. Orchestrator 判断是否允许后台云侧。
3. Sensitivity classifier 和 budget guard 通过后才允许云侧。
4. 云侧使用写入 `AiUsageEventRepository`。
5. `CloudUsageNotificationAggregator` 合并事件。
6. 聚合窗口到期或任务批次完成后发送低频通知。

### 使用记录

设置页增加入口：

- 今日云侧次数
- 今日后台云侧次数
- 今日 fallback 次数
- 今日 token
- 最近 20 条 usage event

记录页只显示元信息，不显示正文和 prompt。

## UI 文案

### 云端 AI 配置页

保存云端配置后，如果开启自动模式和后台云侧许可，显示一次性说明：

`自动模式下，MindFlow 可能在后台使用云端 AI；所有云侧使用会低频通知并记录。`

### 前台 Toast

云侧被策略选中：

`已使用 DeepSeek 处理这次请求。`

本地失败后回退：

`本地模型未完成，已改用 DeepSeek 处理这次请求。`

仅端侧模式失败：

`当前为仅端侧模式，云端不会接手这次请求。`

### 后台通知

低频摘要：

`MindFlow 已用 DeepSeek 完成 4 项后台整理：方向判断、外部线索、回看摘要。`

预算降级不主动通知，只在使用记录中显示。

系统通知权限未开启：

`AI 使用记录` 入口显示未读标记，例如 `今天有 4 次后台云端处理`。

## 迁移策略

### 第一阶段：Provider Registry 和 DeepSeek

1. 新建 `CloudAiProviderSpec`、`CloudAiProviderRegistry`。
2. 将现有智谱、OpenAI、自定义迁入 registry。
3. 新增 DeepSeek provider。
4. `AiServiceClient` 改为读取 provider spec 生成请求。
5. 修正 auth candidate 顺序和 unsupported request fields。
6. 为 DeepSeek、新 OpenAI-compatible custom 添加 unit tests。

### 第二阶段：Runtime Settings

1. 新增 `AiRuntimeSettings` 和 repository。
2. 从 `OnDeviceModelSettings` 迁移 `executionMode`。
3. 修正空历史默认值为 `AUTOMATIC`。
4. 设置页拆分展示：
   - 本地模型状态
   - 云端 provider 配置
   - 运行策略和后台云侧许可
   - AI 使用记录

### 第三阶段：Task Policy 和 Orchestrator

1. 新增 `AiTaskPolicyRegistry`。
2. 将 `AiTaskRouter` 的 provider 顺序改为由 policy 决定。
3. 将 `ContentPolishPlanner`、`NoteInsightPlanner`、topic/tag/folder、concept graph 迁到 policy。
4. 将 Daily Brief、Next Action、Weekly Review、Thread Execution、External Research、Stale Reconnect 逐步迁出直接 `AiServiceClient` 调用。

### 第四阶段：Usage Audit 和低频通知

1. 新增 `AiUsageEventRepository`。
2. 新增 `AiCloudUsageReporter`。
3. 新增 `CloudUsageNotificationAggregator`。
4. 前台 UI 接入 Toast/snackbar。
5. 后台 Worker 和 Flow refresh 接入聚合通知。
6. 设置页增加 `AI 使用记录`。

### 第五阶段：Payload Policy 和 Budget Guard

1. 新增 `PromptPayloadPolicy`。
2. 新增 `AiDataSensitivityClassifier`。
3. 新增 `CloudUsageBudgetGuard`。
4. 对高敏感后台任务阻断云侧。
5. 对后台 token 和请求次数做软限制。

## 测试策略

### Provider tests

- DeepSeek URL 拼接为 `https://api.deepseek.com/chat/completions`。
- DeepSeek 使用 `Authorization: Bearer ...`。
- DeepSeek 默认模型为 `deepseek-v4-flash`。
- DeepSeek 可选 `deepseek-v4-pro`。
- DeepSeek 可发送 `thinking` 和 `reasoning_effort`。
- OpenAI 不发送 DeepSeek-only 字段。
- Custom provider 按用户配置发送，默认不带 thinking。
- 旧 `deepseek-chat`、`deepseek-reasoner` 在 UI 中标记为 deprecated 或迁移提示。

### Runtime tests

- 空历史 execution mode 解码为 `AUTOMATIC`。
- legacy `preferOnDevice = false` 迁移为 `CLOUD_ONLY`。
- explicit stored mode 优先于 legacy flag。
- `cloudAllowedForBackground = false` 时后台任务不调用云侧。
- `ON_DEVICE_ONLY` 时不调用云侧，并返回可解释失败。

### Policy tests

- 前台 polish 云侧优先。
- 后台 note insight 端侧优先，可按策略回退云侧。
- HIGH sensitivity 后台任务阻断云侧。
- `TRANSCRIBE_AUDIO` 和 `UNDERSTAND_IMAGE` 首版不走云侧。
- cloud fallback 记录正确 reason。

### Audit and notification tests

- 每次云侧调用产生 `AiUsageEvent`。
- usage event 不包含正文、prompt、API key。
- 前台云侧事件触发即时提示。
- 后台事件在 5 到 10 分钟窗口内合并。
- 持续后台事件最多 30 分钟 flush。
- 每天最多 3 条后台云侧通知。
- 已通知事件写入 `notifiedAt` 和 `notificationBatchId`。

### Budget tests

- 后台请求数超限后降级到本地或规则。
- token 软上限触发后停止后台云侧增强。
- 前台显式操作不被后台 budget 阻断。

## 验证方式

实现完成后需要验证：

1. Unit tests:
   - provider registry
   - request adapter
   - runtime settings migration
   - task policy
   - usage event repository
   - notification aggregator
   - budget guard

2. Manual release validation:
   - 使用 release APK `app/build/outputs/apk/release/app-release.apk`
   - 不安装 debug APK
   - 不清数据、不卸载、不删除本地模型
   - 检查已有记录和本地模型仍存在

3. UI smoke:
   - 设置页可选 DeepSeek
   - 可测试 DeepSeek 连接
   - 前台云侧调用出现即时提示
   - 后台云侧调用低频合并通知
   - AI 使用记录能看到 provider/model/task/fallback/token

## 风险和缓解

### 风险 1：抽象过大

缓解：

- 首版只支持 OpenAI Chat Completions 协议。
- provider registry 写在 Kotlin 代码里，不做动态插件系统。
- 先接 DeepSeek，同时保留智谱、OpenAI、自定义。

### 风险 2：通知打扰

缓解：

- 后台通知低频合并。
- 每天最多 3 条。
- 前台打开 App 时优先用设置页入口承接，不强制系统通知。

### 风险 3：用户不清楚到底发送了什么

缓解：

- 使用记录展示 payload policy。
- 不展示正文，但解释发送级别。
- 首次开启后台云侧许可时显示一次性说明。

### 风险 4：后台成本失控

缓解：

- budget guard 限制后台请求和 token。
- 超限自动降级本地或规则。
- 使用记录展示今日请求数、后台次数和 token。

### 风险 5：端云策略继续分散

缓解：

- `AiTaskPolicyRegistry` 成为唯一 provider 顺序入口。
- 逐步迁移仍直接依赖 `AiServiceClient` 的 planner。
- 新 AI 功能必须先登记 task policy。

## 成功标准

1. 用户能在设置里选择 DeepSeek，并成功测试连接。
2. DeepSeek 接入不影响智谱、OpenAI、自定义 provider。
3. 新安装默认 `AUTOMATIC`，不会误落到 `CLOUD_ONLY`。
4. 每次云侧调用都有本地 usage event。
5. 后台云侧调用会低频合并通知，不逐条打扰。
6. 高敏感后台内容默认不上云。
7. 后台云侧调用受预算限制。
8. 任务策略表能解释任意 AI 任务为什么用了端侧或云侧。
9. release APK 验证不破坏用户记录和本地 Gemma 模型。
