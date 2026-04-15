# Concept Graph V1 Design

## 背景

当前 `flow/graph` 页展示的是“主题之间的关系图”，其数据和渲染链路分散在以下几处：

- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/KnowledgeGraphPlanner.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiModels.kt`
- `app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt`

旧实现同时维护 canonical graph、presentation graph、UI fallback 逻辑，最终导致三个问题：

1. 数据真相不唯一，难以判断问题出在生成、裁剪还是渲染。
2. 节点粒度过粗，以 thread 为中心，无法表达“知识点如何互相串联”。
3. 手机上的默认视图过于像一张全局拓扑图，可读性和可操作性都不稳定。

本次重做不继续修补旧 thread graph，而是改成知识点级别的 `concept graph v1`。

## 目标

1. 在 `flow/graph` 页面展示“概念 / 知识点”之间的串联网络，而不是主题之间的关系。
2. 支持 AI 对知识点之间的语义关系做激进推断，让图谱具备“主动串起来”的能力。
3. 进入页面时，以最近活跃知识点为中心，只展示一层直接关联，避免手机端信息过载。
4. 点击任意节点后，该节点立刻成为新的中心点，并展示关系词和一句解释。
5. 用单一 snapshot 作为 source of truth，前端只渲染，不再补边、补点、重选子图。

## 非目标

1. 不做 thread 之间的阶段依赖图。
2. 不做全局一次性展示全部知识点的大网图。
3. 不在 v1 中展示证据列表、关联笔记列表或原始片段引用。
4. 不追求纯规则生成；语义关系允许 AI 推断。
5. 不在本轮重做中发布到应用市场，先做到本地稳定。

## 用户体验

### 页面入口

- 入口继续保留在 `MindFlowDestinations.FLOW_GRAPH`
- 底部导航仍显示“图谱”
- 页面标题统一改为“知识图谱”

### 默认视图

1. 页面打开时，自动选择“最近活跃、最近被写入或最近被抽取”的知识点作为中心点。
2. 首屏只展示该中心点的一层直接关联节点，默认最多展示 6 个邻接节点。
3. 默认不自动展示第二层关系，避免页面变成噪音网图。
4. 页面需要有明确的“继续展开”动作，每次继续补充最多 6 个邻接节点。

### 节点交互

1. 点击任意节点后，该节点切换为新的中心点。
2. 切换中心点时，图中的一层邻接节点重新计算并刷新。
3. 页面底部显示当前中心点的简短说明，以及与上一个中心点之间的关系说明。
4. 关系说明格式为：`关系词 + 一句 AI 解释`。

### 空状态

如果当前中心点没有任何有效关系：

- 不显示看起来坏掉的空网图。
- 明确显示“这个知识点还没串起来”一类的状态文案。
- 允许用户切换到其他候选中心点，或继续等待更多记录沉淀。

## 信息架构

`flow/graph` 页拆成三个逻辑区：

1. 顶部：页面标题和当前中心点摘要。
2. 中部：中心展开式知识网络图。
3. 底部：当前选中关系的解释区，以及“继续展开”动作。

页面不再混合“记录热度 + 图谱”双主角结构。`graph v1` 应该是一张单目标页面，中心是知识点网络本身。

## 数据模型

### 设计原则

新的图谱模型只有一个 snapshot 真相源，不再拆 canonical graph 和 presentation graph。

### 新模型草案

新增或替换为以下模型族：

- `ConceptGraphSnapshot`
- `ConceptGraphNode`
- `ConceptGraphEdge`

#### `ConceptGraphNode`

字段目标：

- `id`: 稳定主键
- `canonicalLabel`: 标准化后的知识点名称
- `aliases`: 被归并进来的同义或近义表达
- `summaryLine`: 该知识点的简短说明
- `sourceIds`: 支撑该节点的知识对象或最近抽取项 id
- `lastActivatedAt`: 最近一次被记录、抽取或更新的时间
- `heatScore`: 用于选择默认中心点和排序

#### `ConceptGraphEdge`

字段目标：

- `id`: 稳定主键
- `fromNodeId`
- `toNodeId`
- `relationLabel`: 如“支撑”“递进”“并列”“引用”“对照”
- `reasonLine`: 一句 AI 解释
- `strengthScore`: 关系强度
- `confidenceScore`: AI 置信度或内部排序分
- `sourceIds`: 推断关系时参考的节点来源 id
- `lastGeneratedAt`

#### `ConceptGraphSnapshot`

字段目标：

- `version`
- `nodes`
- `edges`
- `defaultCenterNodeId`
- `generatedAt`
- `source`

### 旧模型迁移

以下旧模型应退出生产路径：

- `DirectionWikiGraphOverview`
- `DirectionWikiGraphNode`
- `DirectionWikiGraphEdge`
- `DirectionWikiGraphPresentationNode`
- `DirectionWikiGraphPresentationEdge`
- `DirectionWikiGraphPresentationFocus`
- `DirectionWikiGraphPresentationSnapshot`
- `DirectionWikiGraphSnapshot`

它们可以在迁移过程中暂存，但新页面和新 planner 不再依赖它们。
迁移完成后，生产路径中不再保留它们的读写依赖。

## 节点来源

`concept graph v1` 的节点来源由两部分组成：

1. 已经沉淀下来的概念 / 知识点。
2. 最近笔记里新抽出的概念。

实现上，这意味着 coordinator 在构建图谱输入时需要合并“长期知识对象”和“近期抽取概念”，而不是只看某一侧。

## 节点归并

节点归并策略为“标准化合并”：

1. 字面完全一致的表达直接合并。
2. 同义、近义、不同表述允许合并成一个标准节点。
3. 合并后的节点保留 `aliases`，供后续解释和调试使用。

归并步骤可以由 AI 辅助，但产出的结果必须落地到稳定的 snapshot 中，不能只存在于 UI 临时态。

## 关系生成

### 关系类型

v1 支持以下关系语义：

- 支撑
- 递进
- 并列
- 引用
- 对照

v1 仅允许这 5 个关系词，先不扩张。

### 生成原则

1. 允许 AI 激进推断语义关系。
2. AI 输出的关系必须绑定到已存在节点对，不能生成未定义节点。
3. 每条边必须带一句可读解释。
4. 关系一旦生成并写入 snapshot，前端不得再自行二次推断。

### 约束

1. 单个中心点的一层邻接节点数量必须有限，避免首屏爆炸。
2. 第二层展开必须是显式动作，而不是默认展开。
3. 如果 AI 无法对某个节点生成任何可接受关系，该节点可以保留为孤立节点，但 UI 需明确展示空状态。

## 生成链路

新的生成链路分 4 步：

1. 抽取候选知识点
2. 做标准化归并
3. 对候选节点对生成关系和解释
4. 生成单一 `ConceptGraphSnapshot`

### Step 1: 抽取候选知识点

责任位置：`DirectionWikiCoordinator`

输入：

- 已沉淀知识对象
- 最近笔记中抽出的概念

输出：

- 候选知识点列表

### Step 2: 标准化归并

责任位置：新的 `ConceptGraphPlanner`

输入：

- 候选知识点列表

输出：

- 标准节点列表
- alias 映射

### Step 3: 关系推断

责任位置：新的 concept graph planner

输入：

- 标准节点列表
- 节点来源摘要

输出：

- 知识点之间的边
- 每条边的关系词和一句解释

### Step 4: 快照落盘

责任位置：`DirectionWikiCoordinator`

输入：

- 节点
- 边
- 默认中心点

输出：

- 单一 `ConceptGraphSnapshot`

## 默认中心点

默认中心点由最近活跃度决定，优先考虑：

1. 最近被写入或更新的知识点
2. 最近从笔记中被抽取出来的知识点
3. 与其他节点连接更丰富的知识点

如果多个节点接近，优先最近活跃者。

## UI 渲染边界

前端只负责以下职责：

1. 读取 snapshot。
2. 根据当前中心点取一层邻接节点。
3. 处理“点击节点切中心”。
4. 处理“继续展开”。
5. 展示关系词和一句解释。

前端不得负责：

1. 补边
2. 补点
3. 重新猜测中心点
4. 在本地重建另一份 graph truth

## 旧代码切除范围

重做时需要重点处理的旧实现：

- `app/src/main/java/com/mindflow/app/data/wiki/KnowledgeGraphPlanner.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiModels.kt`
- `app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt`

目标不是继续给旧 thread graph 打补丁，而是让这些文件只保留 `concept graph v1` 所需内容。

## 测试策略

### 单元测试

1. 候选知识点合并时，能把同义和近义表达归并成单个标准节点。
2. 关系推断结果只引用已存在节点，不会生成脏引用。
3. 默认中心点选择遵守“最近活跃优先”。
4. 一层邻接选择逻辑稳定，不受 UI fallback 干扰。

### UI / Instrumented 测试

1. 打开页面时，能看到默认中心点和一层邻接点。
2. 点击某个节点后，该节点成为新的中心点。
3. 关系解释区会展示关系词和一句解释。
4. 中心点没有关系时，会展示明确空状态，而不是空白图。

## 风险

1. AI 激进推断会带来误连线，需要靠排序和阈值控制噪音。
2. 节点归并如果过强，可能会错误合并不同概念。
3. 旧页面当前混合了“记录热度”和“图谱”，拆分页面结构时要注意不要引入新的导航混乱。

## 发布策略

本轮先完成本地重构和验证，不直接发应用市场。只有在以下条件同时满足后才考虑发布：

1. 新 snapshot 结构稳定。
2. 本地单元测试和 UI 测试覆盖核心路径。
3. 真实数据下能稳定展示出中心点、一层邻接和关系解释。
