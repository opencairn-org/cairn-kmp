# 1. Record architecture decisions

Date: 2026-10-28

## Status

Accepted

## Context

cairn-kmp is a client for cairn-core on four platforms with a long expected life. Decisions
about transport, storage and tooling are expensive to revisit and easy to forget.

## Decision

We record architecture decisions as short ADRs in `docs/adr/`, numbered, one decision each,
in the format of Michael Nygard's article. `docs/ARCHITECTURE.md` stays the one-page overview
and links to the ADRs that shaped it.

## Consequences

A change that contradicts an ADR starts with a new ADR that supersedes it.
