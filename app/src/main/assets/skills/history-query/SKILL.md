---
name: history-query
description: Query, count, categorize, and summarize MindFlow historical notes.
---

# History Query

## Instructions

Use this skill when the user asks about MindFlow historical notes, including:

- counts
- lists
- categories
- timelines
- summaries over all records or a scoped date range

Call the JS runtime entry with:

- script name: `index.html`
- data: a JSON string with:
  - `intent`: one of `count`, `list`, `categories`, `timeline`, `analysis`
  - `query`: the original user question
  - `timeScope`: the requested scope or `all_time`
  - `needsCard`: whether a visual card is useful

Do not invent counts. Use native tool results for counts, date ranges, and record coverage.
Only include source links if the user explicitly asks for links or original records.
When returning a card, use the WebView output as a visual companion. The card should show coverage,
themes, timeline hints, and representative records, but the natural-language answer remains the source
of analysis and conclusions.
