# 2. Clean architecture, offline first

Date: 2026-10-28

## Status

Accepted

## Context

The app shows data that lives on a cairn-core instance, often behind a slow home uplink, on
devices that are frequently offline. The same screens run on Android, iOS and desktop.

## Decision

- Layers: `app → feature → core/data → core/domain`; `core/domain` depends on nothing but
  coroutines and datetime. Features never import each other; navigation goes through typed
  keys in `app/shared`.
- The SQLDelight database is the single source of truth. Repositories expose `Flow`s from the
  database and refresh them from the server with per-repository freshness rules; screens render
  cached data first and show a staleness indicator.
- One database file per `(server, user)`; switching accounts swaps a scoped Metro graph.
- Screens are `ViewModel` + `UiState` + composables that take state and callbacks only.

## Consequences

Every read path is written twice (local query + remote refresh) and mapped once. In exchange
the app opens instantly, works offline, and tests of ViewModels need no network.
