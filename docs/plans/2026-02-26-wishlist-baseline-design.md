# Wishlist Baseline Design

## Goal

Create a fast-capture, unranked wishlist for future project ideas while preserving clear scope boundaries between Companion and Head Unit work.

## Selected Mode

- Wishlist style: unranked idea pool
- Item detail level: short cards
- Scope policy: mixed ideas allowed (Companion + Head Unit + Cross-App), but each item must declare scope.

## Wishlist Structure

Use one shared wishlist document as an idea inbox with short-card entries.

Each item includes:
- `Idea`
- `Value`
- `Dependencies`
- `Scope` (`Companion` | `Head Unit` | `Cross-App`)

## Workflow Rules

- Wishlist is capture-only and does not authorize implementation.
- Items must be promoted into roadmap/plans before work begins.
- Promotion rules:
- `Scope: Companion` -> can proceed in this repository through normal planning.
- `Scope: Head Unit` or `Scope: Cross-App` -> must create or update a `Blocked by Head Unit` entry in `docs/project-vision.md` before Companion work proceeds past dependency boundary.

## Boundary Consistency

- This design preserves the canonical policy in `docs/project-vision.md`.
- Wishlist supports ideation speed without weakening companion/head-unit separation.

## Approved

Approved during baseline-goal definition on 2026-02-26.
