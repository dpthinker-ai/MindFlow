# MindFlow Plans

## Product direction

MindFlow should become a local-first mobile `idea incubator`.

The core outcome is not "better note management".

The core outcome is:

- capture weak signals quickly
- preserve them long enough to matter
- surface recurring patterns the user would miss alone
- help turn fragments into stronger ideas, methods, and decisions

## What already exists

The product already has strong building blocks:

- fast capture and editing
- voice capture
- share-to-capture
- widget and launcher shortcuts
- reminders
- search
- thread structure
- local knowledge maintenance
- local Gemma 4 runtime via `LiteRT-LM`
- cloud model configuration

This means the next phase is not "add AI".

It is to align the product around the right thesis.

## Current phase: product identity reset

Goal:

- make the whole app tell one story

Immediate work:

1. unify product language across docs and app shell
2. stop describing MindFlow as an `AI brief / direction dashboard`
3. make `图谱` a first-class surface
4. keep model controls in settings, not in the main workflow
5. make local/cloud trust rules explicit

## P0: Lock the incubator thesis

This phase should produce:

- one consistent product definition across `README.md`, `PROJECT_GUIDE.md`, `PLANS.md`, and `DESIGN.md`
- bottom navigation aligned to `记录 / Flow / 查询 / 图谱 / 设置`
- explicit local-first and cloud-escalation rules

This phase should not yet do a full Flow redesign.

## P1: Flow rewrite

Goal:

- turn Flow into the true home of the incubator

Target sections:

1. `别丢这颗火花`
2. `你其实一直在想这个`
3. `把这两个点撞一下`
4. `已经长成了`
5. `再喂什么`

Success condition:

- opening Flow creates one obvious next move

Failure condition:

- opening Flow still feels like reading a system summary

## P2: Query as directed thinking

Goal:

- make query intentional, not generic chat

Target user verbs:

- `继续养`
- `撞一下`
- `反驳它`
- `帮我抽象`
- `帮我拉成方案`
- `帮我找证据`

Requirements:

- valuable outputs are promotable
- valuable outputs file back into local memory

## P3: Graph as shape, not spectacle

Goal:

- make graph useful for incubation decisions

The graph should help the user see:

- hubs
- isolated ideas
- dense clusters
- missing edges

Not just note topology.

## P4: Trust and provenance

Goal:

- make the local/cloud boundary trustworthy

Rules to implement:

- local by default
- no silent cloud use in background maintenance
- explicit or clearly implied cloud escalation
- provenance on cloud-derived results
- file-back into local memory

## Longer-term direction

If the thesis holds, MindFlow should grow toward:

- stronger Spark triage
- better recurring Thread detection
- higher-quality Collision generation
- durable Asset shaping
- smarter Gap recommendations

The long-term win is not a bigger note system.

It is a product that helps the user repeatedly turn weird half-formed thoughts into something real.
