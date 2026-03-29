# MindFlow Agent Guide

## Mission
MindFlow is not just a note app. It is a personal AI action system for:

- capturing fleeting ideas quickly
- organizing thoughts into durable themes
- turning ideas into next actions
- improving work, health, and life outcomes over time

## Product Principles

1. Content first
- Title and content always stay visually primary.
- Metadata such as tags, folders, status, and timestamps should support the note, not overpower it.

2. Fast capture
- New input must be low friction.
- The shortest path to writing should stay obvious on mobile.

3. AI as augmentation
- AI should assist with topic extraction, tags, folder classification, polishing, and insight generation.
- AI should not silently override user edits.
- User intent always wins over automation.

4. Daily usefulness
- Every major feature should improve one of these loops:
- capture
- recall
- connect
- act

5. Calm but premium design
- The app should feel simple, direct, and polished.
- Avoid clutter, over-explaining UI, and decorative noise.

## Information Architecture

Core record fields:

- content
- topic
- tags
- status
- folder
- createdAt
- updatedAt
- archive state

Primary surfaces:

- Home: capture, overview, recent notes
- Search: filters, tags, folders, result-first retrieval
- Heatmap: contribution-style activity view
- Editor: preview/edit, AI helpers, save flow
- Settings: AI, cloud backup, import/export

## AI Boundaries

AI is allowed to:

- extract topic
- extract tags
- classify folder
- polish content
- generate brief insight later

AI is not allowed to:

- overwrite manual topic, tags, or folder choices without explicit user action
- hide failure states
- block note saving

## Current Folder Model

Fixed top-level folders:

- work
- life
- project
- health

Notes:

- fitness is merged into health
- uncategorized notes remain valid and should be recoverable

## Design Rules

- Keep status bar readable on all light backgrounds.
- Prefer folded controls over long filter walls.
- Swipe actions should reveal buttons first, never perform destructive actions immediately.
- Share visuals should stay elegant and minimal.

## Engineering Rules

- Keep local database as the runtime source of truth.
- Markdown remains the readable interchange format for export, import, and cloud backup.
- New automation should be low-frequency and battery-aware.
- Release builds matter more than debug-only polish.

## Near-Term Direction

MindFlow should evolve toward:

- daily AI brief
- weekly review
- idea-to-action extraction
- note linking and theme connection
- long-term personal innovation memory

Current status:

- daily AI brief: implemented
- weekly review: implemented
- idea-to-action extraction: implemented
- morning/evening reminder hooks: implemented
- faster capture entry points: first version implemented
- thread follow and curation: first version implemented
- voice capture in editor: first version implemented
- thread focus and lightweight promotion: first version implemented

Next strongest direction:

- external research enrichment
- notification quick capture
- follow-up prompts for stale notes
- thread-level weekly review
