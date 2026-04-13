# MindFlow 信息图谱 v2

## 1. 目标

`信息图谱` 不是后台知识对象的可视化，也不是把所有笔记硬画成一张图。

它在前台只回答 3 个问题：

1. 我现在主要积累了哪些主题。
2. 哪些主题已经连成结构，哪些还是孤的。
3. 下一步最值得补哪条边。

它服务的不是“展示所有知识”，而是“帮助用户看清结构压力”。

## 2. 非目标

以下内容不属于 `信息图谱` 的职责：

- 展示所有 `结论 / 证据 / 方法 / 实验 / 问题`
- 把后台 wiki 文件树直接当前台
- 把没有证据的“感觉有关”画成边
- 用一张图替代 `今天 / 回看 / 查询`
- 追求复杂交互而牺牲可读性

## 3. 产品定位

MindFlow 的前台分工应该是：

- `记录`: 快速捕获
- `今天`: 现在先接住什么
- `回看`: 过去什么值得翻出来
- `图谱`: 整体结构现在长成什么样
- `设置`: 系统控制面

所以 `图谱` 页只保留两张卡：

- `记录热度`
- `信息图谱`

## 4. 核心原则

### 4.1 节点原则

前台节点只能是 `主题线程`，也就是 `direction-level thread`。

不允许当前台节点的对象：

- `结论`
- `证据`
- `问题`
- `方法`
- `实验`
- `概念`

这些对象可以参与图谱计算，但不直接露成节点。

### 4.2 边原则

边必须来自真实关系，不能来自主观美术排版需要。

允许的边依据：

- 共享概念
- 共享问题
- 共享方法
- 共享实验
- 多条记录长期共同出现
- 明确的依赖或承接关系

不允许的边依据：

- “看起来很像”
- “都挺重要”
- “都是最近写的”
- 只是同一天更新

### 4.3 AI 原则

AI 不负责“现画一张漂亮图”。

AI 负责 4 件事：

1. 从 wiki 和记录里抽结构候选。
2. 判断哪些关系值得保留。
3. 把正式结构压缩成移动端可读版本。
4. 给节点和边生成用户能理解的解释。

## 5. 系统分层

`信息图谱` 的正确链路应该是：

1. `raw notes`
2. `llm wiki objects`
3. `canonical graph snapshot`
4. `mobile presentation snapshot`
5. `ui layout`

其中：

- `llm wiki objects` 是后台知识层
- `canonical graph snapshot` 是正式结构层
- `mobile presentation snapshot` 是前台压缩层

当前系统的问题是：

- 本地 maintainer 主要产出摘要卡
- cloud graph planner 直接产出 UI 级 graph JSON
- UI 还有规则兜底图

三层都在解释图谱，语义不统一。

v2 要求只保留两层结构语义：

- 正式结构层
- 前台展示层

## 6. Canonical Graph Schema v2

### 6.1 Snapshot

```json
{
  "version": 2,
  "generatedAt": 0,
  "source": "llm+rule",
  "overview": {
    "summaryLine": "",
    "hubThreadKeys": [],
    "isolatedThreadKeys": [],
    "densifyingThreadKeys": [],
    "missingLinkCandidates": []
  },
  "nodes": [],
  "edges": []
}
```

### 6.2 Node

```json
{
  "threadKey": "folder:work",
  "label": "工作",
  "summaryLine": "这一组记录主要围绕执行、协作和推进节奏展开。",
  "gapLine": "还缺一条更稳定的协作判断。",
  "maturity": "forming",
  "recencyScore": 0.82,
  "densityScore": 0.76,
  "supportIds": ["concept:xxx", "question:yyy", "note:123"],
  "noteCount": 12,
  "updatedAt": 0
}
```

字段说明：

- `threadKey`: 唯一主题线程键，前台节点唯一身份
- `label`: 用户可读主题名，2 到 8 字
- `summaryLine`: 这条主题现在主要在讲什么
- `gapLine`: 这条主题当前最缺什么
- `maturity`: `forming | strengthening | stable`
- `recencyScore`: 最近活跃度，0 到 1
- `densityScore`: 结构密度，0 到 1
- `supportIds`: 支撑这条节点存在的对象或记录
- `noteCount`: 当前覆盖记录数
- `updatedAt`: 最近更新时间

### 6.3 Edge

```json
{
  "fromThreadKey": "folder:work",
  "toThreadKey": "folder:health",
  "relationType": "shared_method",
  "strength": 4,
  "reasonLine": "这两条主题都反复回到节律和恢复机制。",
  "supportIds": ["method:abc", "note:123", "note:456"],
  "firstSeenAt": 0,
  "lastSeenAt": 0,
  "confidence": 0.78
}
```

字段说明：

- `relationType`: 关系类型
- `strength`: 1 到 5
- `reasonLine`: 用户能读懂的关系解释
- `supportIds`: 支撑这条边的对象或记录
- `firstSeenAt / lastSeenAt`: 关系形成和最近活跃时间
- `confidence`: 0 到 1

### 6.4 Relation Types

第一版只允许这 6 类：

- `shared_concept`
- `shared_question`
- `shared_method`
- `shared_experiment`
- `co_occurrence`
- `dependency`

不允许自由生成新的 relation type。

## 7. Mobile Presentation Snapshot

前台卡片不直接消费 canonical graph 全量结构，而消费一份压缩后的 `presentation snapshot`。

```json
{
  "title": "信息图谱",
  "headline": "6 个主题 · 3 条关系",
  "summaryLine": "几条稳定主题已经开始连起来了。",
  "nodes": [],
  "edges": [],
  "focus": {
    "threadKey": "folder:work",
    "label": "工作",
    "summaryLine": "",
    "gapLine": "",
    "relatedThreadKey": "folder:health",
    "relatedReasonLine": ""
  }
}
```

压缩规则：

- 节点数量：`4-6`
- 边数量：`0-4`
- 文案长度：每个字段最多一行
- 节点 label：最多 8 字
- 如果关系不够硬，宁可 `0` 条边

## 8. 三段 Prompt 设计

### 8.1 Prompt A: `graph_extract`

职责：

- 从 direction summaries 和知识对象里抽候选节点与候选边

输入：

- `direction summaries`
- `concepts`
- `questions`
- `methods`
- `experiments`
- `evidence`
- `recent note slices`

输出：

- 候选 `nodes`
- 候选 `edges`
- 每条边的 `supportIds`

输出要求：

- JSON only
- 不允许创建不存在的 `threadKey`
- `supportIds` 不能为空数组，至少 1 个
- 没有支撑就不要生成边

草案：

```text
你在维护 MindFlow 的正式信息图谱结构层。
你的任务不是生成移动端展示，而是从现有 direction 和知识对象中抽取候选主题节点与候选关系。

输出 JSON。
不要输出 Markdown，不要解释。

节点只能来自提供的 direction threadKey。
边只能来自以下依据之一：
1. shared_concept
2. shared_question
3. shared_method
4. shared_experiment
5. co_occurrence
6. dependency

每条边必须附带 supportIds。
如果没有足够支撑，就不要生成边。
```

### 8.2 Prompt B: `graph_adjudicate`

职责：

- 对候选边做保留/丢弃判断

输入：

- 候选边
- 每条边的 support objects
- 方向摘要

输出：

- 正式边列表
- `strength`
- `confidence`
- `reasonLine`

判断标准：

- 对用户是否真的有解释价值
- 是否足够稳定，不是偶然共现
- 是否会帮助用户理解结构，而不是制造噪音

草案：

```text
你在做信息图谱的关系裁决。
目标不是多画边，而是只保留真正能帮助用户理解结构的边。

输出 JSON。

保留边的条件：
- 有真实支撑
- 关系足够稳定
- 对用户有解释价值

丢弃边的条件：
- 只是近期偶然共现
- 只是文本相似
- 没有清晰的关系语义

每条保留边都要输出：
- relationType
- strength(1-5)
- confidence(0-1)
- reasonLine
- supportIds
```

### 8.3 Prompt C: `graph_present`

职责：

- 把 canonical graph 压成移动端 `信息图谱` 卡片

输入：

- canonical graph snapshot

输出：

- `headline`
- `summaryLine`
- `4-6` 个前台节点
- `0-4` 条前台边
- 一个默认 `focus`

草案：

```text
你正在为移动端生成一张“信息图谱”卡片。
目标不是展示所有结构，而是让用户在一屏内看懂当前最重要的主题关系。

输出 JSON。

规则：
- 节点 4 到 6 个
- 边 0 到 4 条
- 节点文案必须是用户能看懂的主题名
- 不要使用维护、证据对象、结论对象等后台术语
- 如果关系不够硬，宁可不画边
- 默认 focus 必须是当前最值得继续看的主题
```

## 9. UI 规则

### 9.1 卡片结构

`信息图谱` 卡片内部只保留三层：

- `headline`
- `graph canvas`
- `focus panel`

不保留：

- 长说明
- 后台术语
- 解释性大段文案

### 9.2 图布局

第一版布局要求：

- 稳定布局，不随机跳
- `hub` 靠中
- 孤点靠外
- 线尽量少
- 不追求“完整”，只追求“可懂”

布局输入应该只基于：

- `strength`
- `densityScore`
- `relation count`

而不是凭文案热度决定。

### 9.3 节点视觉

- 节点文字只保留 `label`
- 大小反映 `densityScore`
- 视觉强调反映 `maturity + recencyScore`
- 不在节点内部塞 `summaryLine / gapLine`

这些解释应该进 `focus panel`

## 10. 第一版实现边界

v2 第一版只做：

1. 建立 canonical graph schema
2. 拆出 `graph_extract / graph_adjudicate / graph_present`
3. 让 UI 只消费 `presentation snapshot`
4. 删除 UI 自己兜底造边的逻辑

v2 第一版不做：

- 全量可缩放图编辑器
- 任意拖拽
- 全知识对象展开
- 多层 graph 导航
- 自动生成复杂社区层级

## 11. 验收标准

如果以下 4 条成立，就说明 v2 方向是对的：

1. 用户能一眼看懂节点在说什么主题。
2. 用户不会再问“这条边为什么会存在”。
3. 删掉 1 条边时，图不会更清楚，说明边已经足够少。
4. 点开 focus 后，用户知道“这条主题是什么、和谁有关、缺什么”。

## 12. 当前代码改造建议

按顺序做，不要并发乱改：

1. 扩展 [DirectionWikiModels.kt](/home/dpthinker/MindFlow/app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiModels.kt) 的 graph schema
2. 重写 [KnowledgeGraphPlanner.kt](/home/dpthinker/MindFlow/app/src/main/java/com/mindflow/app/data/wiki/KnowledgeGraphPlanner.kt) 的输出链路，拆成 extract/adjudicate/present
3. 让 [KnowledgeGraphScreen.kt](/home/dpthinker/MindFlow/app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt) 只消费 presentation snapshot
4. 删除 UI 里的 fallback edge 生成逻辑

## 13. 设计判断

最终判断很明确：

- `图谱` 页应该保留
- 但它必须是 `LLM wiki -> 正式结构层 -> 前台压缩层`
- 不能再是 `摘要卡 + 即时图谱 JSON + UI 规则兜底` 的混合体

AI 在这里最值钱的地方，不是帮我们“画一张图”，而是帮我们做：

- 抽结构
- 判关系
- 压前台
- 给解释

这才是 MindFlow 里 `信息图谱` 的正确位置。
