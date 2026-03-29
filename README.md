# MindFlow

MindFlow 是一个原生 Android 的个人 AI 灵感与行动系统。它不只负责记录，还会帮你：

- 快速写下零散想法
- 用 AI 提取主题、标签、文件夹和润色内容
- 每天给出推进方向、探索方向和下一步动作
- 通过搜索、热力图、文件夹和回看机制持续复用历史记录

## 当前能力

- 首页：
  - 总览、完成进度、新建记录
  - 最近记录流
  - 轻量入口，不堆叠洞察信息
- Flow：
  - `今天`
    - 积极推进
    - 探索方向
    - 下一步动作
  - `本周回看`
    - 主线 / 推进 / 重启 / 串联
    - 轻量周进展统计
  - `方向` 聚合
  - 主题线程与线程详情页
  - 可关注的长期方向
- 记录：
  - 快速新建与编辑
  - Markdown 预览与原文编辑切换
  - 自动记录创建/更新时间
  - 状态流转：`想法 / 进行中 / 已实现`
  - 归档、删除、状态历史
- AI：
  - 主题提取
  - 标签提取
  - 文件夹分类
  - 内容润色
  - 每日 brief
  - 下一步动作
  - 每周回看
- Markdown：
  - 正文预览支持标题、列表、引用、加粗、斜体、行内代码、代码块基础展示
- 搜索与组织：
  - 关键词搜索
  - 状态、标签、文件夹、时间范围筛选
  - 折叠式筛选区
  - 固定一级文件夹：`工作 / 生活 / 项目 / 健康`
- 统计：
  - 热力图活动视图
  - 点击具体日期查看当天记录
- 分享：
  - 生成卡片式分享图
- 快速入口：
  - 长按应用图标快捷进入 `新建记录 / 查找 / Flow`
  - 桌面 `快速记录` 小组件
  - 系统分享文本到 MindFlow 直接生成预填记录
  - 编辑页支持语音转文字，直接补进正文
- 备份与恢复：
  - 本地 Markdown 导入导出
  - WebDAV 云备份与恢复
- 提醒：
  - 晨间 brief 通知
  - 晚间 review 通知
  - 通知内可直接 `记一条` 或 `打开 Flow`

## 本地运行

1. 确保本机有 Android SDK，并在 `local.properties` 中配置：

```properties
sdk.dir=/your/android/sdk
```

2. 构建 Release 包：

```bash
./gradlew --no-daemon assembleRelease
```

## AI 主题提取配置

AI 能力是增强层，不配置也能正常使用，本地规则会兜底。

可通过 `local.properties` 或环境变量配置：

```properties
mindflow.ai.apiKey=YOUR_API_KEY
mindflow.ai.baseUrl=https://open.bigmodel.cn/api/paas/v4
mindflow.ai.model=glm-4.7
```

对应环境变量：

- `MINDFLOW_AI_API_KEY`
- `MINDFLOW_AI_BASE_URL`
- `MINDFLOW_AI_MODEL`

如果 `apiKey`、`baseUrl` 或 `model` 缺失，应用会直接回退本地规则提取。

## 每日提醒

- 设置路径：`设置 -> 每日提醒`
- 当前支持：
  - `晨间 brief`：每天 08:30
  - `晚间 review`：每天 21:30
- Android 13 及以上会在你开启提醒时请求通知权限

## 导入恢复

- 通过应用内“设置 -> 本地备份”选择 Markdown 文件
- 当前支持导入由 MindFlow 自己导出的 Markdown 格式
- 导入会把记录和状态历史追加到当前数据库中，不做去重
