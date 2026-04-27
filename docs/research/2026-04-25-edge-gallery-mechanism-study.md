# Edge Gallery 机制研究

研究日期：2026-04-25  
研究对象：`google-ai-edge/gallery` `main` 分支，提交 `3b368f4`

## 1. 研究目的

这次研究不是为了照搬 `Google AI Edge Gallery`，而是为了回答 4 个具体问题：

1. 它的 `skill` 机制到底怎么工作。
2. 它所谓的 “RAG” 本质上在做什么。
3. 它为什么用 `JS + WebView` 来承载 skill。
4. 对 MindFlow 来说，哪些点值得深度借鉴，哪些不该照搬。

## 2. 结论先说

Edge Gallery 的核心不是“一个聊天页”，而是一套：

- `skill 定义`
- `模型路由`
- `tool 执行`
- `JS / native 执行器`
- `统一结果协议`
- `聊天流承载结果`

的能力平台。

它的“RAG”不是传统向量库式 RAG，而更接近：

**tool-backed retrieval / skill-backed retrieval**

也就是：

1. 模型先判断该用哪个 skill
2. 再加载 skill 指令
3. skill 去外部或本地执行检索 / 工具动作
4. 再把结果回给模型和 UI

这套架构对 MindFlow 最值得学的地方不是某个 prompt，而是：

**把“能力”从页面逻辑里抽出来，做成 skill / tool。**

## 3. 它的整体架构

### 3.1 Agent Chat 入口

入口在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModule.kt`

这里定义了 `Agent Skills` 任务，并给模型一个非常强的 system prompt：

- 先找最相关 skill
- 再调用 `load_skill`
- 再按 skill 指令执行
- 不允许跳过步骤

这说明它的主设计不是“模型自由发挥”，而是：

**模型先做 skill routing，再做任务执行。**

### 3.2 Skill 的定义方式

skill 说明文档在：

- `skills/README.md`

每个 skill 至少有：

- `SKILL.md`

JS skill 还会带：

- `scripts/index.html`

`SKILL.md` 包含：

- `name`
- `description`
- 具体 Instructions

其中 `name + description` 用于让模型判断 skill 是否相关。真正执行前，再用 `load_skill` 读取完整说明。

这比把所有 skill 说明一次性塞进 system prompt 更轻，也更容易扩展。

### 3.3 Skill 的来源

Skill 管理在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/SkillManagerViewModel.kt`

支持三类来源：

- 内置 asset skill
- 本地导入 skill
- URL skill

`convertSkillMdToProto(...)` 负责把 `SKILL.md` 解析成内部 skill 对象。

这说明它把 skill 当成真正的内容包，而不是代码里写死的 case。

## 4. Skill 是怎么执行的

### 4.1 模型侧接入

LiteRT-LM 的接入在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`

关键点：

- `tools = listOf(tool(agentTools))`
- `enableConversationConstrainedDecoding = true`

也就是它不是在应用层手动 parse 文本，而是把 `ToolSet` 直接注入给 LiteRT-LM。

这比我们现在应用层自己编排更接近真正的 agent tool calling。

### 4.2 ToolSet 的实现

核心在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentTools.kt`

暴露了 3 个关键工具：

- `loadSkill`
- `runJs`
- `runIntent`

职责非常清楚：

- `loadSkill`：返回 skill 的完整说明
- `runJs`：运行 JS skill
- `runIntent`：调用系统原生 intent

这说明 Edge Gallery 把模型可调用能力限制在一个非常窄、非常清晰的边界里。

### 4.3 JS Skill 的执行方式

JS 执行不是“直接 eval 一段字符串”，而是完整的 WebView runtime。

相关文件：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/GalleryWebView.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatScreen.kt`

执行流程：

1. `runJs` 找到 skill 对应的 URL
2. 聊天页收到 `CallJsAgentAction`
3. 用隐藏 WebView `loadUrl(action.url)`
4. 等页面加载完成
5. 再执行：
   - `ai_edge_gallery_get_result(data, secret)`
6. 通过 `AiEdgeGallery.onResultReady(result)` 回传结果
7. 超时 60 秒自动失败

它要求 skill 页面显式暴露：

- `window['ai_edge_gallery_get_result']`

这让 JS skill 的执行边界非常明确。

### 4.4 本地文件与 WebView 资源映射

`GalleryWebView.kt` 里用了：

- `WebViewAssetLoader`
- `LOCAL_URL_BASE = https://appassets.androidplatform.net`

作用是：

- 允许内置 asset skill 加载本地文件
- 允许导入 skill 从 app 私有目录加载 HTML / CSS / JS / asset

这一步很关键。它不是把 HTML 当字符串塞进去，而是给 skill 一个真正稳定的本地 URL 运行环境。

## 5. 它的“RAG”到底是什么

以 `query-wikipedia` 为例：

- `skills/built-in/query-wikipedia/SKILL.md`
- `skills/built-in/query-wikipedia/scripts/index.html`

这不是向量检索，而是：

1. 模型先把用户问题抽成干净的 `topic`
2. 再通过 skill 脚本去 Wikipedia API 查询
3. 返回摘要和 infobox
4. 再由模型基于结果回答

也就是说，它的“RAG”本质是：

- skill 先做 retrieval
- 模型后做 grounding 和回答

这套方法尤其适合：

- 外部百科
- 地图
- 第三方 API
- Web 数据

不适合直接等同于：

- 个人历史知识库
- 本地精确统计
- 图谱批量构建

## 6. 结果协议

结果定义在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/Types.kt`

JS skill 返回 JSON，可包含：

- `result`
- `error`
- `image`
- `webview`

这说明 skill 的输出不是“只能回一句话”，而是多模态结果对象。

这个设计非常值得借，因为它天然支持：

- 文字说明
- 图片卡片
- 内嵌 WebView 视图

## 7. 聊天界面的交互方式

### 7.1 空状态是启动器，不是空白页

在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatScreen.kt`

空状态会直接显示：

- 这个能力是什么
- Skill 是什么
- 一排 `Try out` prompt chips

这解决了 agent 产品最难的冷启动问题：用户不需要先理解 skill，再自己构造 prompt。

### 7.2 Skill 管理是二级入口

Skill 管理不放主流程里，而是：

- 底部 sheet
- 点击 `Skills` 后打开

这点很重要。它把平台复杂度藏起来了，没有让聊天界面变成开发者控制台。

### 7.3 执行过程是可见的

它会通过 `SkillProgressAgentAction` 把这些过程暴露给用户：

- loading skill
- calling js
- executing intent

UI 里会展示成可折叠 progress panel，而不是一句模糊的“生成中”。

这点尤其值得借。平台化之后，如果执行链不可见，用户会完全不知道系统在做什么。

### 7.4 结果直接回到消息流

Agent 结果不会强迫跳页。

它支持在消息流里直接插入：

- 文字消息
- 图片消息
- WebView 消息

这使得聊天页不是单纯对话框，而是一个“skill 执行与结果承载容器”。

### 7.5 输入框不是单纯文本框

输入区在：

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/MessageInputText.kt`

它集成了：

- 文本
- 图片
- 音频
- 历史输入
- 停止生成
- skill 入口

这意味着它把聊天输入框做成了“任务发起器”，不是一个只发文本的框。

## 8. 它为什么选 JS

根因不是性能，而是扩展性。

JS skill 的优势：

- skill 编写门槛低
- 容易加载外部 API
- 容易做 HTML 卡片和交互
- 容易通过 URL 分享和导入
- Web 生态工具多

所以 Edge Gallery 的目标更像：

**通用 agent skill 平台**

而不是一个只服务单一产品逻辑的本地知识系统。

## 9. 对 MindFlow 的深度借鉴点

### 9.1 最值得借的

#### A. Skill 化能力单元

不要让聊天、图谱、今日卡片各自藏着一套逻辑。

应该把这些能力抽成 skill / tool：

- 历史查询
- 分类归纳
- 时间线
- 图谱节点抽取
- 图谱关系构建
- 今日卡片选材

#### B. 把执行和表达分开

Edge Gallery 非常明确：

- tool 负责取数据 / 执行动作
- 模型负责路由和表达

这正是 MindFlow 当前最缺的分工。

#### C. 统一结果协议

文本、卡片、图谱视图都应该走统一 skill result 协议，而不是每个页面自定义。

#### D. 聊天流承载卡片和图谱结果

这点尤其适合 MindFlow。以后图谱结果、统计卡片、今日洞察卡片，都应该能嵌回流里。

### 9.2 不该照搬的

#### A. 不该照搬它的“通用技能市场”

MindFlow 当前还不需要：

- 社区 skill 市场
- 大规模第三方 skill 分发
- URL skill 任意导入

如果一开始就做这套，会把复杂度拉太高。

#### B. 不该把核心本地查询全搬去 JS

Edge Gallery 的 skill 很多是在查外部世界。  
MindFlow 的核心是：

- 本地历史
- 精确统计
- 图谱
- 今日卡片

这类核心执行更适合 native 保底。

所以对 MindFlow 更合适的是：

**JS-first for orchestration and visualization, native-first for data execution.**

## 10. 对 MindFlow 的建议架构

### 10.1 方向

不是继续在现有 `Planner` 上补规则，而是做一套：

- `SkillRegistry`
- `SkillExecutor`
- `NativeToolBridge`
- `SkillResult`
- `SkillChat/Card/Graph host`

### 10.2 当前落地边界

MindFlow 现阶段应该坚持这个边界：

- Native 只做宿主：聊天流、输入框、进度面板、WebView 容器、打开记录、持久化。
- JS/HTML 负责业务卡片：统计卡、历史检索卡、图谱卡、今日洞察卡、后续自定义可视化。
- Skill 返回统一协议：`result` 给模型和普通文本流，`webview.url` 给 UI 宿主渲染卡片。
- 新增卡片时优先新增 `app/src/main/assets/skills/<skill-id>/assets/*.html`，不要在 Compose 里新增业务卡片布局。

这个边界的目的不是“为了用 JS 而用 JS”，而是让后续卡片可以像 Edge Gallery 一样被 Skill 独立生成、独立演进，不让聊天页、图谱页、今日页各自堆一套 Native UI。

### 10.3 最合理的形态

#### JS 负责

- 卡片渲染
- 图谱渲染
- skill 前端逻辑
- HTML/Canvas/SVG 可视化
- 技能编排

#### Native 负责

- Room 查询
- 精确统计
- 时间线
- 本地数据过滤
- 打开记录 / 跳转 / 持久化
- 模型调用

#### 模型负责

- 选 skill
- 解释 skill 结果
- 做归纳和表达

## 11. 推荐给 MindFlow 的实施顺序

### 第一阶段：搭平台骨架

1. 定义 `SkillManifest`
2. 定义 `SkillResult` 协议
3. 定义 `NativeToolBridge`
4. 做 `JsSkillExecutor`

### 第二阶段：做两个示范 skill

1. `history-query-card`
2. `concept-graph-view`

### 第三阶段：迁移业务场景

1. 今日卡片迁 skill
2. 图谱迁 skill
3. 回看聊天接 skill 编排

## 12. 最终判断

Edge Gallery 值得我们深度借鉴的，不是某个 prompt，也不是某个 UI 皮肤，而是：

**用 skill 把能力做成独立单元，用 JS 做灵活可视化，用 tool 保持执行边界清楚。**

对 MindFlow 来说，最合理的借法不是原样照搬，而是：

**借它的 skill 架构与 JS runtime 思路，做一套以本地数据桥为核心的 skill 平台。**

这样后面：

- 聊天
- 图谱
- 今日卡片
- 新的可视化卡片

都能跑在同一套能力系统上，而不是每个页面各做一套。
