# Review Chat Regression QA

## Goal

Keep review chat usable before every release. This QA loop protects the failure modes users already hit:

- wrong all-history counts
- date scopes leaking records from other days
- abstract questions saying "not found" when related records exist
- category answers missing categories or merging multiple categories into one line
- unsolicited evidence, next-step sections, or record links
- external questions answered with "material is insufficient"
- on-device formatting losing newlines

## Test Matrix

Run each question on both cloud and on-device modes when possible.

| ID | Question | Expected Result | Must Not Happen |
| --- | --- | --- | --- |
| RC-01 | 我总共有多少条记录 | Returns the exact total from local records. | Links, sample records, or "材料不足". |
| RC-02 | 我只看今天的 | Only today's records are included. | Yesterday or older records appear. |
| RC-03 | 看一下本周末记录了哪些信息，都有哪些类别 | Uses only this weekend's records and groups them into categories. | Adds unrelated days or "下一步". |
| RC-04 | 我3月份一共记了多少条？ | Counts only March records. | Counts all history or another month. |
| RC-05 | 帮我分析所有的记录，都有哪些分类？ | Covers the full corpus and returns distinct category items. | Treats "分类/类别" as a category or returns only 1-2 records. |
| RC-06 | 我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。 | Recalls semantically related records and answers in 2-4 sentences. | Says no records found when related notes exist, or adds evidence/next-step sections. |
| RC-07 | 把 4 月 10 号那天的完整内容发给我 | Returns matched full records for that date. | Adds links unless asked. |
| RC-08 | 把 4 月 10 号那条记录的原始链接发给我 | Returns the matched record link/reference. | Returns links for unrelated records. |
| RC-09 | 今天天气怎么样 | Answers as a general non-realtime assistant or says realtime data is unavailable. | Uses personal history as material or says history material is insufficient. |
| RC-10 | 帮我总结一下我的待办或未完成事项 | Uses actual task/status evidence only. | Invents todo state from ordinary records. |

## Automated Gate

Run this before manual QA:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatCoreRegressionTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatPromptRegressionTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatStructuredAnswerTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatAnswerFormatterTest
```

Pass criteria:

- All tests pass.
- No prompt regression that drops `SkillResult` coverage for history questions.
- No structured answer regression that merges category items into one line.
- No formatter regression that breaks dates like `2026-03-30`.

## Release Build Gate

Install a signed release build on the real device:

```bash
~/.codex/skills/android-release-install/scripts/install_release.sh \
  --project "$PWD" \
  --package com.mindflow.app
```

Pass criteria:

- Release APK builds and installs.
- App launches with a live process.
- Crash log has no fresh app crash.
- The fixed QA questions above produce readable answers.

## Manual Verification Notes

For each answer, record:

- mode: cloud or on-device
- screenshot path
- pass/fail
- failure layer: retrieval, prompt, model, structured parsing, display formatting, UI

Use this failure classification:

- Retrieval: expected records are missing before the prompt.
- Prompt: the prompt includes the right records but gives wrong output rules.
- Model: prompt is right, model still ignores or misunderstands it.
- Structured parsing: raw model output is acceptable but sections/items are parsed wrong.
- Display formatting: structured answer is correct but rendered text loses breaks.
- UI: rendered text is correct but screen interaction, copy, input, or scroll is broken.

## Done Definition

The review chat QA loop is considered stable when:

- All automated review chat tests pass.
- RC-01 through RC-09 pass on both cloud and on-device release builds for two consecutive runs.
- RC-10 does not invent incomplete tasks from ordinary notes.
- Zero high-priority failures remain: wrong total count, wrong date scope, false "not found", external questions using history material, unsolicited links, or unreadable formatting.
- Any medium-priority issues are documented with the failure layer and next fix target.
