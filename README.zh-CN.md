# MindFlow

[English](README.md)

MindFlow 是一个 local-first 的 Android 想法孵化器。

它帮助你抓住还很脆弱的想法，让它们在时间里持续生长，识别反复出现的线索，制造有价值的碰撞，并把成熟的模式沉淀成可复用的个人资产。

它不是一个加了 AI 聊天框的通用笔记应用。

## 为什么存在

大多数笔记应用优化的是存储。MindFlow 优化的是孵化：

1. 在想法消失前捕捉一个 Spark。
2. 让本地知识层吸收并连接它。
3. 浮现值得继续处理的 Thread、Collision、Asset 或 Gap。
4. 用本地模型，或由用户明确触发的云侧能力，继续深化材料。
5. 把有价值的输出重新写回本地长期记忆。

产品气质应该像一本带长期记忆的口袋实验笔记，而不是 AI 仪表盘或模型游乐场。

## 产品界面

| 界面 | 作用 |
| --- | --- |
| `记录` | 快速捕捉文本、语音、链接、截图和摘录。 |
| `今天` | 当前正在孵化的 Spark、Thread、Collision、Asset 和 Gap。 |
| `回看` | 围绕已维护记忆进行有方向的思考。 |
| `图谱` | 查看中心节点、孤岛、加密的簇和缺失的连接。 |
| `设置` | 模型控制、云侧配置、同步/导出和诊断。 |

部分内部路由仍可能保留 `flow/*` 一类兼容名称，但新的产品表达应使用 `今天` 和 `回看`。

## 当前能力

- 原生记录与编辑
- 语音转文字捕捉
- 系统分享进入记录
- 启动器快捷方式和快速记录小组件
- 搜索、筛选、提醒和相关记录结构
- 本地知识层维护
- 基于 LiteRT-LM 的端侧 Gemma 4 E4B 支持
- 用户明确配置和触发的云侧模型能力
- 用于理解记忆上下文的图谱与回看界面

## Local-First AI

MindFlow 中的 AI 不是一组零散功能，而是一个统一的思考角色：辅助捕捉、维护记忆、识别线索、生成碰撞、塑造资产。

默认信任边界是 local-first：

- 原始记录默认留在本地
- 后台维护默认留在本地
- 云侧调用必须由用户明确触发，或由用户选择的动作清晰暗示
- 云侧生成结果应带有来源说明
- 关闭云侧能力后，应用仍应可用

## 项目结构

- `app/src/main/java/com/mindflow/app/data/`：仓库、AI 路由、本地模型、知识维护、备份/导出和图谱规划
- `app/src/main/java/com/mindflow/app/ui/`：Compose 页面、导航和 UI 组件
- `app/src/main/assets/`：图谱和 skill runtime 资源
- `docs/superpowers/specs/`：设计说明
- `docs/superpowers/plans/`：历史实现计划
- `PROJECT_GUIDE.md`：维护者指南、验证规则和数据保护约束

## 构建

要求：

- Android Studio 或 Android SDK 命令行工具
- JDK 21
- 在 `local.properties` 中配置 `sdk.dir`

```properties
sdk.dir=/path/to/android/sdk
```

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleRelease
```

Release APK 路径：

```text
app/build/outputs/apk/release/app-release.apk
```

## 密钥与本地文件

不要提交本地凭据、签名文件、验证数据或个人导出。以下路径默认被忽略：

- `local.properties`
- `signing.properties`
- `keystore/`
- `.env` 文件
- `data/`
- `recovery/`
- `tmp/`
- `docs/product/`

## Release 验证安全

面向用户可见的验证必须保留已有应用数据和本地模型文件。

在模拟器或真机验证时：

- 安装 release APK，不安装 debug APK
- 使用 `adb install -r app/build/outputs/apk/release/app-release.apk`
- 不运行 `adb uninstall`
- 不运行 `pm clear`
- 不清空模拟器或 AVD 数据
- 不删除已下载的本地模型文件
- 如果签名不匹配，停止并报告，不强行重装

修改 release 验证、签名、本地模型或恢复流程前，请先阅读 `PROJECT_GUIDE.md`。
