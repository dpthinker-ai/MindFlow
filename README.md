# MindFlow

MindFlow 是一个原生 Android 的轻量笔记应用，专门用于快速记录想法、后续查找和推进状态。它围绕 3 件事展开：

- 快速写下灵感或待做事项
- 自动生成主题并记录时间
- 通过搜索、筛选、归档和状态历史持续回看

## 当前能力

- 记录流首页，按最近更新时间展示未归档记录
- 快速新建记录，保存后自动记录时间
- 主题自动提取
  - 优先走 AI
  - AI 不可用时回退本地规则
- 记录详情页支持：
  - 编辑内容
  - 手动修改主题
  - 主动重新提取主题
  - 更新状态为 `想法 / 进行中 / 已实现`
  - 归档 / 取消归档
  - 查看状态历史
- 搜索页支持：
  - 关键词搜索
  - 状态筛选
  - 时间范围筛选
  - 包含归档记录
- 统计页支持：
  - 状态分布
  - 完成率
  - 最近新增和最近活跃情况
- 设置页支持：
  - 应用内保存 AI Base URL / Model / API Key
  - 导入 MindFlow 导出的 Markdown 文件
- 导出为单个 Markdown 文件

## 本地运行

1. 确保本机有 Android SDK，并在 `local.properties` 中配置：

```properties
sdk.dir=/your/android/sdk
```

2. 构建 Debug 包：

```bash
./gradlew assembleDebug
```

3. 运行单元测试：

```bash
./gradlew testDebugUnitTest
```

## AI 主题提取配置

AI 提取是增强能力，不配置也能正常使用，本地规则会兜底。

可通过 `local.properties` 或环境变量配置：

```properties
mindflow.ai.apiKey=YOUR_API_KEY
mindflow.ai.baseUrl=https://api.openai.com/v1
mindflow.ai.model=YOUR_MODEL_NAME
```

对应环境变量：

- `MINDFLOW_AI_API_KEY`
- `MINDFLOW_AI_BASE_URL`
- `MINDFLOW_AI_MODEL`

如果 `apiKey`、`baseUrl` 或 `model` 缺失，应用会直接回退本地规则提取。

## 导入恢复

- 通过应用内“设置 -> 导入恢复”选择 Markdown 文件
- 当前支持导入由 MindFlow 自己导出的 Markdown 格式
- 导入会把记录和状态历史追加到当前数据库中，不做去重
