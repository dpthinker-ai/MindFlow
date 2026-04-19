# 端侧本地知识层维护器设计

## 背景

当前项目已经有三类和端侧 AI 强相关的能力：

1. `LocalKnowledgeMaintenancePlanner`
   - 负责压出本地知识层的当前判断、最近吸收、开放问题等内容
2. `DirectionWikiCoordinator`
   - 负责方向 wiki、知识对象和概念图谱的结构层产物
3. `ReviewChatPlanner`
   - 负责基于历史记录和沉淀结构生成回看聊天回答

但这三条链还没有形成一个统一的 `端侧脑`：

- 端侧模型现在更多像一个被动 provider
- 聊天仍然偏临时检索 + 临时生成
- 图谱仍然偏结构层单独维护
- 新记录进入系统后，缺少一条统一的、持续增长的本地知识维护链

与此同时，用户希望更充分利用 `Gemma 4 E4B` 的端侧能力：

- 让聊天更像“我本地已经想过这件事”
- 让图谱更像“长期长出来的结构”
- 让端侧模型不只是调用一次返回一次，而是持续把原始记录加工成可复用的本地知识层

因此，这次不是再补一个单点聊天优化，而是要设计一套 `端侧本地知识层维护器`：

- 云侧继续承担 `stateless inference`
- 端侧升级为 `stateful local brain`
- 聊天和图谱共同消费这套端侧脑产物

## 目标

1. 把端侧 Gemma 4 E4B 从“被动 provider”升级成“主动维护本地知识层的引擎”。
2. 建立统一的三层本地知识系统：
   - `Raw Layer`
   - `Memory Layer`
   - `Structure Layer`
3. 让回看聊天优先消费 `Memory Layer + Structure Layer`，而不是每次从零拼接 snippet。
4. 让图谱逐步接入 `Memory Layer` 的增量结果，增强默认中心、主题线和稀疏图质量。
5. 建立一条增量维护链，使新记录进入后端侧脑持续增长，而不是只在用户进入页面时临时计算。
6. 保留端侧知识层可重建、可重压缩的能力，避免错误结构长期累积。

## 非目标

1. 不把云侧改造成有长期记忆的 provider。
2. 不做一个完全 autonomous、永远常驻的 agent 系统。
3. 首版不做全量历史持续重写或全量持续重建。
4. 不把图谱整体改写成完全 memory-first 的生成链。
5. 不做云端同步的长期记忆层。
6. 不让所有页面都实时依赖这套端侧脑。
7. 不在首版引入向量数据库或独立 embedding 检索栈。

## 产品决策汇总

后续实现必须遵守以下决策：

1. 云侧和端侧明确分层：
   - `云侧 = stateless inference`
   - `端侧 = stateful local brain`
2. 端侧脑的主目标不是“替代云侧”，而是最大化利用 Gemma 4 E4B 做本地知识维护。
3. 端侧脑优先同时服务：
   - `回看聊天`
   - `图谱`
4. 首版方案采用 `本地知识层维护器`，不是轻量索引器，也不是全 autonomous agent。
5. 端侧脑采用三层结构：
   - `Raw Layer`
   - `Memory Layer`
   - `Structure Layer`
6. 首版先落 `Memory Layer` 的 3 个核心对象：
   - `MemoryFragment`
   - `MemoryThread`
   - `MemoryDigest`
7. 后台维护采用 `增量维护链`，不是每次全量重建。
8. 聊天优先读 `Memory Layer`，图谱优先读 `Structure Layer + MemoryThread`。
9. 用户明确要求原文时，聊天必须支持按需回到 `Raw Layer` 展开全文。
10. 首版允许提供“立即刷新本地知识层”的手动入口，但不要求做成激进真常驻服务。

## 总体架构

### 1. Raw Layer

`Raw Layer` 是事实源头，基本沿用现有 note 体系：

- 正文
- 标题
- 标签
- 时间
- 状态
- 文件夹

这一层的职责非常单纯：

- 保留真实输入
- 不承担智能判断
- 为 `Memory Layer` 和 `Structure Layer` 提供可追溯源头

### 2. Memory Layer

`Memory Layer` 是这次新增的核心层，也是 Gemma 4 E4B 主要持续工作的地方。

它的职责不是直接渲染 UI，而是把原始记录压成更适合推理和续聊的中间记忆。

它主要面向：

- 问题演化
- 主题延续
- 时间线聚合
- 多条记录之间的连接判断

### 3. Structure Layer

`Structure Layer` 面向更稳定的长期沉淀，继续服务：

- 图谱
- wiki
- 方向总结
- 知识对象

这层不等同于 `Memory Layer`。

区分原则是：

- `Memory Layer` 偏过程、脉络、时间演化
- `Structure Layer` 偏稳定结构、长期结论、可展示对象

如果两层混在一起：

- 聊天会变得过于僵硬
- 图谱会变得过于碎片化

## 数据模型

### 1. MemoryFragment

这是端侧脑的最小增量单位，用来承接单条或少量记录产生的局部记忆。

字段建议：

- `id`
- `sourceNoteIds`
- `createdAt`
- `updatedAt`
- `topicKey`
- `questionKey`
- `summary`
- `salience`
- `timeSpan`

典型语义：

- 这条记录在继续讨论某个旧问题
- 这条记录形成了一个新的局部判断
- 这条记录应该连接到某条已有主题线

### 2. MemoryThread

`MemoryThread` 用于把多个 fragment 归并成一条持续问题线或主题线。

字段建议：

- `id`
- `title`
- `type`（主题 / 问题 / 方向）
- `fragmentIds`
- `summary`
- `currentState`
- `openQuestions`
- `updatedAt`

这层最适合聊天消费，因为它最接近“我过去是怎样一路想过来的”。

### 3. MemoryDigest

`MemoryDigest` 面向时间窗口或特定视角的压缩。

字段建议：

- `id`
- `scopeType`（day / week / topic / question）
- `scopeKey`
- `summary`
- `highlights`
- `sourceFragmentIds`
- `updatedAt`

这层主要解决：

- 某一天发生了什么
- 某一周最重要的变化是什么
- 某个主题最近一个阶段如何变化

它对 `回忆和查找` 能力尤其重要。

### 4. Structure Layer 对象

首版不重做整套结构层 schema，继续沿用并增强现有对象：

- `ConceptNode`
- `ConceptEdge`
- `DirectionSummary`
- `KnowledgeObject`
  - 结论
  - 问题
  - 方法
  - 实验

重点不是发明新对象，而是让结构层开始消费 `MemoryThread` 的增量变化。

## 后台维护链

### 1. 记录接入任务

当 note 新建或更新后触发。

端侧最小加工内容：

- 主题候选
- 概念候选
- 与已有主题/方向的连接判断
- 初步 memory fragment 生成

目标是让新记录尽快进入端侧脑，而不是等到用户进入聊天或图谱时才临时计算。

### 2. 记忆归并任务

将新 fragment 和既有 `MemoryThread` 做归并。

典型结果：

- 把今天的新记录挂到一条老问题线上
- 把两条看似分散的记录压成同一主题线
- 刷新 thread 的 `currentState` 和 `openQuestions`

### 3. 结构刷新任务

把 `Memory Layer` 的变化同步进 `Structure Layer`：

- 刷新概念点
- 刷新概念关系
- 刷新方向总结
- 刷新知识对象

这一步主要提升图谱和 wiki 的长期质量。

### 4. 回看重压缩任务

这是低频任务，不要求每次写记录后都跑。

它负责：

- 重新整理旧 thread
- 合并重复概念
- 刷新长周期问题线
- 修正早期端侧判断偏差

这一步保证端侧脑不是只会积累，还能定期收口。

## 触发时机

首版建议拆成三档：

### 1. 即时增量

新记录写入后立即排队：

- 生成 fragment
- 尝试 thread 归并
- 更新相关 digest

### 2. 机会性维护

在合适时机补跑：

- 应用回到前台
- 停留在回看页
- 前台空闲片段

这一步主要补归并和结构刷新。

### 3. 低频整理

用于重压缩和整体质量刷新：

- 每天一次
- 或用户主动触发“立即刷新本地知识层”

这三档组合起来可以形成“后台脑感”，但仍然保持增量、可控、低打扰。

## 聊天消费链

回看聊天以后不应该只是“拿几条 snippet 给模型”，而应该走四段式取材：

### 1. 先查 MemoryDigest

适合问题：

- 某一天
- 某一周
- 某个阶段
- 某个主题近期变化

### 2. 再查 MemoryThread

适合问题：

- 某条长期分歧
- 某个持续问题线
- 某个方向为什么反复出现

### 3. 再补 Structure Layer

当需要更稳定的知识对象、方向总结或图谱关系时，补充结构层内容。

### 4. 最后按需回到 Raw Layer

只有用户明确要求以下内容时，才展开原始记录全文：

- 那天的完整内容
- 某条记录原文
- 更完整的原始上下文

这条设计直接解决当前 review chat 的两个缺口：

1. 只能给摘要，不能展开完整内容
2. 原始记录在回答链中只是弱 snippet，不是可按需展开的源头

## 图谱消费链

首版不要求图谱彻底改写为 memory-first，但必须开始接入 `MemoryThread`。

优先接入点：

1. 默认中心选择
   - 优先选更稳定、更活跃的 thread 相关节点
2. 稀疏图补充
   - 当直接边不足时，用 thread 衍生出的弱连接增强探索路径
3. 主题关系增强
   - 将 thread 的变化同步到 concept / edge / direction

图谱的主要消费顺序是：

- `Structure Layer`
- `MemoryThread`
- `Raw Layer`（只用于补来源）

这样图谱会逐渐从“临时关系图”转向“长期长出来的结构图”。

## 云侧与端侧分工

### 云侧

云侧继续承担：

- 高质量推理
- 更强归纳和综合能力
- 临时调用型推理

它不承担：

- 长期本地记忆
- 持续知识维护
- 本地知识层存储

### 端侧

端侧承担：

- 本地知识层持续增长
- fragment / thread / digest 维护
- 结构层增量刷新
- 图谱和聊天的本地记忆底座

因此端侧不只是“另一个 provider”，而是本地知识运行时。

## 首版范围

首版严格收在以下内容：

1. 新增 `Memory Layer`
   - `MemoryFragment`
   - `MemoryThread`
   - `MemoryDigest`
2. note 更新后的端侧增量维护链
3. 回看聊天优先消费 `Memory Layer`
4. 用户明确要原文时，支持按需展开 `Raw Layer`
5. 图谱先部分接入 `MemoryThread`
   - 默认中心
   - 稀疏图补充
   - 主题关系增强
6. 提供一个手动“立即刷新本地知识层”的入口

## 非首版范围

这些明确不做：

1. 完全 autonomous 的常驻 agent 脑
2. 全量历史持续重写
3. 多层复杂 memory ranking 系统
4. 云端记忆同步
5. 图谱全链改写成 memory-first
6. 所有页面都实时依赖本地知识层
7. 向量库 / embedding 检索基础设施

## 风险与约束

### 1. 端侧负载风险

Gemma 4 E4B 很强，但手机不是服务器。

因此端侧脑必须满足：

- 增量
- 分阶段
- 可中断

不能把“持续学习”实现成“每次都全量重建”。

### 2. Memory Layer 脏数据风险

如果 fragment/thread 归并策略太激进，错误结构会长期积累。

因此首版必须保留：

- 可重建能力
- 低频重压缩能力

### 3. 消费口径分叉风险

如果聊天和图谱各自再做一套私有中间层，端侧脑会失效。

因此必须要求：

- 聊天和图谱共用同一套 `Memory Layer`
- 图谱不私有维护第二套 thread-like 中间结果

## 测试策略

### 1. 单测

覆盖：

- note -> fragment 生成
- fragment -> thread 归并
- digest 刷新
- 聊天取材顺序
- 原文按需展开逻辑

### 2. Planner / Repository 测试

覆盖：

- 增量维护链的输入输出
- 重压缩后的对象一致性
- 聊天对 `Memory Layer` 的消费正确性
- 图谱对 `MemoryThread` 的消费正确性

### 3. 集成测试

覆盖：

- 新记录写入后端侧脑增量更新
- 回看聊天对同一条历史线的追问更稳定
- 图谱默认中心和稀疏图增强生效

### 4. 运行态观察

至少记录：

- 维护队列长度
- 单次维护耗时
- fragment / thread / digest 生成成功率
- 结构刷新成功率

目的是确认端侧脑真的在增长，而不是只在设计上存在。

## 实现顺序

实现必须按以下顺序推进：

1. `Memory Layer schema + repository`
2. `note -> fragment/thread/digest` 增量链
3. `review chat` 接入 `Memory Layer`
4. `原文展开 + 记录入口`
5. `图谱` 接入 `MemoryThread` 增强
6. `低频重压缩任务`

这样可以先把端侧脑的最小闭环跑通，再逐步扩大收益面。

## 预期效果

这套设计落下去之后，端侧 Gemma 4 E4B 不再只是“被调用一次，回答一次”，而会持续把原始记录加工成可复用的本地知识层。

最终效果应当是：

- 回看聊天更像“这是你过去一路想过来的脉络”
- 图谱更像“这些脉络长成了哪些稳定结构”
- 端侧 AI 能力被系统性地吃满，而不是停留在临时推理阶段
