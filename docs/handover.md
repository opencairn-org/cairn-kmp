# Handover to cairn-core: what the native client needs

From: cairn-kmp (Kotlin Multiplatform client: Android, iOS, desktop). Date: 2026-10-28.
Against: cairn-core v0.5.1. Status: request; nothing here is implemented in core.

The client uses the proto RPC API exclusively: no `/api/*` JSON handlers, no HTML routes. It
talks to the Connect-Go handlers with the Connect protocol over HTTP/2 (binary proto). This
document lists everything core has to provide, grouped by the client phase that needs it
(H1 first). Each group should be one or more core releases; `buf breaking` protects the contract.
Where a REST handler already exists, the RPC wraps the same use case; the REST handlers stay
(web UI, third parties). Decision 2026-10-28: both surfaces are kept; only binaries change.

Conventions for all new RPCs: package `cairn.v1`, request/response message per RPC, SI units,
UUIDv7 ids, `google.protobuf.Timestamp`, `PageRequest`/`PageResponse` with opaque cursors for
every list, `ErrorDetail` on errors, `idempotency_level = NO_SIDE_EFFECTS` on pure reads, a
comment on every field (buf lint `STANDARD` already applies).

## H0. Transport

1. **h2c.** DONE on core branch `feat/transport-instance-auth` (`Server.Protocols` with
   `UnencryptedHTTP2`, prior knowledge). Connect, gRPC and gRPC-Web keep working unchanged.
2. **Streaming interceptors.** DONE on core branch `feat/activity-service-parity`:
   `SessionInterceptor`, `ScopeInterceptor` and `RequireAdminInterceptor` now wrap every
   stream type (`internal/adapter/primary/connect/auth.go`, `serverInterceptor`), so
   server-streaming RPCs get the same auth context and scope checks. Note the scope rule is
   name-based on Connect (a method not starting with a read verb is a write) while REST keys
   on the HTTP method.
3. **Timeouts.** DONE: streaming procedures are exempt from the server-wide `WriteTimeout`
   (5 min default) via a per-request write deadline reset; `ReadHeaderTimeout` set. Connect-Go
   honors `Connect-Timeout-Ms`.
4. **Declared but unimplemented RPCs.** Decision 2026-10-28: nothing is deleted or deprecated.
   RPCs without backing code carry a "Planned, not implemented (returns Unimplemented)" comment
   (`StreamActivityEvents`, the source-preview trio, custom best efforts, `Complete2FA`);
   browser-only flows (`LoginPassword`, `StartOIDCLogin`, `StartOIDCLink`) point at their REST
   routes. The client treats `Unimplemented` on those as "not available", everything else gets
   implemented group by group.
5. **Read paths out of `package main`.** See "Prerequisite refactor (H0.5)" below; it is the
   enabling step for every RPC in H2 to H4.

## H1. Before client Phase 1 (login, instance info)

1. **`AuthService.GetInstanceInfo`** DONE (core `feat/transport-instance-auth`); `registration_open`
   is false (invite-only), `supported_scopes` lists the catalog.
   ```proto
   message GetInstanceInfoRequest {}
   message GetInstanceInfoResponse {
     string version = 1;          // release tag, e.g. "v0.6.0"
     string commit = 2;
     google.protobuf.Timestamp build_time = 3;
     bool federation_enabled = 4;
     bool oauth_enabled = 5;      // OAuth 2.1 AS available (CAIRN_AUTH_OAUTH_ENABLED)
     bool registration_open = 6;  // invites only vs open signup, for the login screen copy
     repeated string supported_scopes = 7;
   }
   ```
2. **OAuth 2.1 loopback redirects (RFC 8252 §7.3).** DONE: `http://127.0.0.1/…` and
   `http://[::1]/…` match any port; the code is sent to the requested port. `localhost` and
   custom schemes keep exact matching.
3. **Dynamic client registration hygiene.** Confirmed: registrations are never garbage-collected.
   `client_name` was already stored; `software_id` / `software_version` are stored and echoed
   since migration 00067 (DONE).
4. **Scope model, documented and complete.** DONE: `cairn-core/docs/api-scopes.md` + a
   `// Scope:` comment on every rpc. Every family has `:read`/`:write`; health is folded into
   `training:read`. Enforcement is per family on Connect (behaviour change for OAuth tokens,
   see core CHANGELOG); `GetCurrentUser`/`GetQuota` accept any valid token; session-bound RPCs
   refuse OAuth tokens.
5. **`Logout` RPC** DONE (cookie sessions; bearer callers get FailedPrecondition pointing at
   `/oauth/revoke`). `Register` (invite-based) and `GetQuota` landed with it.

## H2. Before client Phase 2 (activities)

(RPC-by-RPC specification follows in section "RPC specifications"; this list is the scope.)

- `ActivityService.GetOverview`, `ListHomeFeed`
- `ActivityService.ListActivities` extended to the feed's filter set and facets (or a new
  `SearchActivities`)
- `ActivityService.ListLaps`, `GetActivityZones`, `ListActivitySegmentEfforts`,
  `ListSimilarActivities`, `GetMergedStream`, `ListAttachments`, `ListKudos`, `ListComments`,
  `GetActivityManageInfo`, `GetParsedSource`, `GetExportOptions`, `ExportActivity`
- `ActivityService.GetActivityMapImage` or map image URLs on `Activity`
- Signed URLs for all binary content (see "Binary content")

## H3. Before client Phase 3 (analytics, segments, health)

- `ActivityService.GetStats`, `GetTrainingLoad`, `GetBestEffortHistory`, `GetHeatmap`
  (`ListRecords` DONE on core `feat/activity-service-parity`, activity_type + metrics filters;
  discipline/time_range are refused with InvalidArgument until the repo supports them)
- Mount `SegmentService` (`ListSegments`, `GetSegment`, `ListSegmentEfforts` at least)
- Mount `MetricsService` (`ListMetrics`, `GetUserContext`) and `SettingsService`

## H4. Before client Phase 4 (account, social, sync status)

- `NotificationService.CountUnread` (declared), `StreamNotifications` (declared; needs H0.2)
- Mount `ExternalAccountService`; add `GetSyncStatus`, `ListSyncHistory`, `ListQueueItems`
- New `SocialService`: `ListFollowingFeed`, `ListClubs`, `GetClub`, `ListClubFeed`,
  `ListClubMembers`, `GetProfile`, `GetProjectedActivity`, `GetSharedActivity` (public),
  `ListReviewQueue`
- `AuthService.GetQuota`

## H5. Later

- `ActivityService.StreamActivityEvents` for live list updates.
- An "authorized apps" page in the web UI (list + revoke OAuth clients/grants), promised in
  `cairn-docs/docs/integrations/oauth.mdx`.
- `buf push` of `buf.build/cairn/api` so clients can depend on the module instead of vendoring.

## Binary content (photos, static maps, exports)

Status 2026-10-28 (core branch `feat/transport-instance-auth`, second commit): items 1–6 DONE
with one decision: stored objects are presigned on `CAIRN_STORAGE_PUBLIC_ENDPOINT` when set,
otherwise (and always for content rendered on demand: course images, exports) the URL points at
core's signed `GET /content/{token}` route, so every deployment works without exposing the
bucket. Variants are JPEG + blurhash (pure Go, no WebP/cgo); exports render on GET (no bucket
lifecycle rule). `cairn attachments backfill-variants` backfills old photos. Item 7 (avatars) is
pending with `UserCard` (H2 SocialService). The REST byte routes stay for the web UI.

Rule as requested: **the API never proxies bytes.** Every RPC that references binary content returns a
`ContentUrl {url, expires_at}` pointing straight at the object in the blob store (S3 presigned
GET, 15 min to 1 h), the same mechanism `PresignActivityUpload` already uses. The client and the
web fetch it with a plain GET and no credentials; image loaders, `<img>` and the OS share sheet
cannot add headers. Core stops streaming attachment and map bytes through the Go process.

What core needs for that:

1. **Public blob endpoint for signing.** A presigned URL is bound to the host it was signed for.
   Add a config value (e.g. `CAIRN_BLOB_PUBLIC_ENDPOINT`) used only for presigning, separate
   from the cluster-internal MinIO address, and document that the bucket (or a path prefix) must
   be reachable from clients under that host. Optional fallback for instances that cannot expose
   the bucket: a core route `/content/{signed-token}` that streams the object; the RPCs return
   whichever URL the instance is configured for, clients do not care.
2. **Stable identity next to the URL.** Each `ContentUrl` sits beside a stable id and an
   `etag` or `updated_at` (`Attachment.id`, `Activity.map_image_etag`), because the URL changes
   on every call and caches must key on the object, not the URL.
3. **Static maps:** presign the cached objects `mapimg/v5/{id}/{full|trimmed}.png` directly;
   the owner/viewer decision (`viewerTrim`, `activity_map_image.go:248`) picks which object to
   sign. Render on demand if the object is missing, then sign.
4. **Photos: variants.** Generate `thumb` (~320 px) and `medium` (~1280 px) WebP/JPEG variants
   at upload time and store them next to the original; `Attachment` returns
   `ContentUrl original, medium, thumb`, `width`, `height`, `blurhash` (optional, for
   placeholders). Feeds and grids use `thumb`, the detail viewer `medium`, "save" the original.
   Backfill existing attachments with a one-off admin command.
5. **Exports:** `ExportActivity` renders to `exports/{user}/{activity}-{format}-{hash}` with a
   bucket lifecycle rule deleting after 24 h and returns the presigned URL + filename.
6. **Privacy:** signed URLs are bearer credentials: short TTL, never logged (strip query strings
   in request logs), `Cache-Control: private`, no sharing of a signed URL across users (sign per
   request, not per object).
7. **Avatars** follow the same rule (`UserCard.avatar_url` is a signed or public URL).

The REST handlers stay (they serve the web UI and third parties); they apply the same rule:
`/api/activities/{id}/attachments` and the feed items return the signed URLs, and the
byte-streaming routes (`…/attachments/{aid}/raw`, `…/map.png`, `…/export.*`) are replaced by
redirects to the signed URL or removed once the web uses the URLs.

## Pagination and filters

- Every list RPC: `PageRequest{cursor, limit}` in, `PageResponse{next_cursor, total}` out.
  Cursors are opaque; the client never constructs one. `total = -1` when unknown.
- The activity filter is one message reused by `ListActivities`/`SearchActivities` and
  `GetHeatmap` (same filter bar in the client), see RPC specifications.

## RPC specifications

Derived from the REST handlers in `cmd/server/*.go` (file references below). Field names follow
the JSON the web UI consumes today so the web can migrate later with minimal change. Types are
proto3 with the existing `cairn.v1` conventions. Where an existing message is close, extend it
rather than adding a parallel one. Every list carries `PageRequest page` / `PageResponse page`.

Service placement (proposal; core may regroup): per-activity reads stay in `ActivityService`;
aggregates go to a new `StatsService`; social reads to a new `SocialService`; health and athlete
profile to `MetricsService`; sync status to `ExternalAccountService`; instance info and quota to
`AuthService`. **Read RPC names must start with Get/List/Search/Count/Export/Stream** because
`isMutatingProcedure` (`internal/adapter/primary/connect/auth.go:178`) decides by prefix.

### Prerequisite refactor (H0.5)

The read paths are implemented in `package main` (`projectActivityJSON`, `visibilityFor`,
`parseActivityFilter`, `zonesResponse`, `mergedStreamJSON`, `userCardJSON`, `workerPoolsJSON`,
the week/overview arithmetic) against concrete Postgres repos on `App`. A Connect handler in
`internal/adapter/primary/connect` cannot reach them. Move each into a usecase (or a shared
`internal/query` package) once, then let both the REST handler and the RPC call it. This is the
bulk of the work and the reason the RPCs are cheap afterwards.

### Shared messages

```proto
// Shared by SearchActivities and GetHeatmap; mirrors parseActivityFilter (activities_feed.go:110).
message ActivityFilter {
  repeated ActivityType types = 1;          // empty = any
  repeated string disciplines = 2;
  TimeRange time_range = 3;                 // [from, to)
  repeated string gear_ids = 4;             // max 50
  enum Flag { FLAG_UNSPECIFIED = 0; FLAG_ONLY = 1; FLAG_EXCLUDE = 2; }   // tri-state
  Flag virtual = 5; Flag ebike = 6; Flag commute = 7; Flag race = 8;
  optional double distance_min_m = 9;  optional double distance_max_m = 10;
  optional double duration_min_s = 11; optional double duration_max_s = 12;   // elapsed
  optional double elevation_min_m = 13; optional double elevation_max_m = 14;
  optional double speed_min_mps = 15;  optional double speed_max_mps = 16;    // average
  optional double hr_min_bpm = 17;     optional double hr_max_bpm = 18;       // average
  optional double power_min_w = 19;    optional double power_max_w = 20;      // average
}
enum ActivitySort { ACTIVITY_SORT_UNSPECIFIED = 0; DATE_DESC = 1; DATE_ASC = 2;
  DISTANCE_DESC = 3; DURATION_DESC = 4; ELEVATION_DESC = 5; SPEED_DESC = 6; }
message Facet { string value = 1; int32 count = 2; }
message ActivityFacets { repeated Facet types = 1; repeated Facet disciplines = 2; repeated Facet years = 3; }
message Totals { int32 count = 1; double distance_m = 2; double moving_s = 3; double elevation_gain_m = 4; }
message UserCard { string id = 1; string username = 2; string display_name = 3; string avatar_url = 4; // signed or public URL
  bool remote = 5; string actor = 6; }   // remote: ActivityPub author, username is @user@host
message ContentUrl { string url = 1; google.protobuf.Timestamp expires_at = 2; }
// Visibility-projected activity for non-owners (projectActivityJSON, social_read.go:92).
message ProjectedActivity {
  string id = 1; string title = 2; ActivityType type = 3; string discipline = 4;
  google.protobuf.Timestamp start_time = 5; string timezone = 6;
  int64 elapsed_duration_s = 7; int64 moving_duration_s = 8;
  optional double distance_m = 9; optional double elevation_gain_m = 10;
  // category "summary"
  string description = 11; optional double tss = 12; optional int32 calories = 13;
  // category "location"
  string start_place = 14; optional GeoPoint start = 15; bool start_location_redacted = 16;
  // categories "hr", "power", "pace_speed"
  optional int32 avg_hr = 17; optional int32 max_hr = 18;
  optional int32 avg_power = 19; optional int32 max_power = 20; optional int32 normalized_power = 21;
  optional double avg_speed_mps = 22; optional double max_speed_mps = 23;
  // categories "map", "photos"
  ContentUrl map_image = 24; bool has_photos = 25;
  repeated string visible_categories = 26;   // domain.AllCategories order
}
```

### H1

**`AuthService.GetInstanceInfo`** – see H1.1. Wraps `buildInfo()` (`version.go:47`) and
`cfg.Federation.Enabled` (`instance_features.go:16`). Public.

### H2: ActivityService and StatsService

**`StatsService.GetOverview`** – replaces `GET /api/overview` (`overview.go:17`).
```proto
message GetOverviewRequest {}
message GetOverviewResponse {
  Totals totals = 1; repeated Facet by_sport = 2;
  repeated Activity recent = 3;            // 6 newest, full Activity message
  Totals this_week = 4; Totals last_week = 5;   // UTC, Monday start
  TimeRange this_week_range = 6; TimeRange last_week_range = 7;   // so the client can link into the list
}
```

**`SocialService.ListHomeFeed`** – replaces `GET /api/feed/home` (`home_feed.go:24`).
```proto
message ListHomeFeedRequest { PageRequest page = 1; }   // limit default 30, max 100
message FeedItem {
  enum Source { SOURCE_UNSPECIFIED = 0; SELF = 1; FOLLOWING = 2; FEDERATED = 3; }
  Source source = 1; UserCard owner = 2;
  oneof item { Activity own = 3; ProjectedActivity projected = 4; FederatedActivity federated = 5; }
}
message FederatedActivity { string id = 1; string title = 2; string sport = 3; google.protobuf.Timestamp start_time = 4;
  string summary = 5; string url = 6; ContentUrl map_image = 7; optional double distance_m = 8;
  optional double elevation_gain_m = 9; optional int64 elapsed_duration_s = 10; }
message ListHomeFeedResponse { repeated FeedItem items = 1; PageResponse page = 2; }
```
Note: the REST handler merges three sources newest-first and has no stable paging; define the
cursor as (start_time, id) of the last item so paging is stable.

**`ActivityService.SearchActivities`** – replaces `GET /api/activities/feed`
(`activities_feed.go:188`). Either extend `ListActivitiesRequest` or add this RPC and deprecate
the old filter fields; the client only needs one of them.
```proto
message SearchActivitiesRequest { ActivityFilter filter = 1; ActivitySort sort = 2; PageRequest page = 3; }  // limit default 50, max 200
message SearchActivitiesResponse {
  repeated Activity activities = 1;        // Activity already carries gear_id/gear_set_by; add `bool gear_unset`
  ActivityFacets facets = 2;               // each facet ignores its own dimension; years unfiltered
  int32 total = 3; int32 matched = 4; PageResponse page = 5;
}
```
Also add `ContentUrl map_image` to `Activity` (today `/api/activities/{id}/map.png`).

**`ActivityService.ListLaps`** – replaces `/laps` (`laps.go:21`). Owner only.
```proto
message ListLapsRequest { string activity_id = 1; }
message ListLapsResponse { repeated ActivityLap laps = 1; string source_id = 2; }
```
Extend `ActivityLap` (activity.proto:364) with `int64 start_offset_s`, `int64 moving_s`,
`string label`; the REST picks the source with the most laps.

**`ActivityService.GetActivityZones`** – replaces `/zones` (`zones.go:243`). Owner only.
```proto
message GetActivityZonesRequest { string activity_id = 1; }
message ZoneBand { string label = 1; double low_pct = 2; double high_pct = 3; int32 seconds = 4; }
message ZoneSummary { string basis = 1; /* lthr|max|ftp */ double reference = 2; int32 total_seconds = 3; repeated ZoneBand zones = 4; }
message Decoupling { double pct = 1; string basis = 2; /* power|pace */ bool coupled = 3; }
message GetActivityZonesResponse { optional ZoneSummary hr = 1; optional ZoneSummary power = 2; optional Decoupling decoupling = 3; }
```

**`ActivityService.ListActivitySegmentEfforts`** – replaces `/segment-efforts`
(`segment_efforts.go:30`). Owner only.
```proto
message ListActivitySegmentEffortsRequest { string activity_id = 1; }
message ActivitySegmentEffort {
  string segment_id = 1; string segment_name = 2; double segment_distance_m = 3; optional ClimbCategory climb_category = 4;
  double elapsed_s = 5; double moving_s = 6; optional int32 avg_heart_rate = 7; optional int32 avg_power = 8;
  bool is_personal_record = 9; int32 personal_rank = 10;
  optional double start_offset_s = 11; optional double end_offset_s = 12;
  optional double start_distance_m = 13; optional double end_distance_m = 14;
}
message ListActivitySegmentEffortsResponse { repeated ActivitySegmentEffort efforts = 1; }
```

**`ActivityService.GetMergedStream`** – replaces `/merged-stream` (`merged_stream.go:24`).
```proto
message GetMergedStreamRequest { string activity_id = 1; optional double max_resolution_hz = 2; }
message StreamChannel { string name = 1; repeated double values = 2; repeated bool present = 3; string source_id = 4; }  // present[i]=false = gap
message GetMergedStreamResponse { repeated int64 grid_unix_s = 1; repeated StreamChannel channels = 2; }
```
Channel names as in `domain/stream.go:74-96`.

**`ActivityService.ListSimilarActivities`** – replaces `/similar` (`activity_similar.go:38`).
```proto
message ListSimilarActivitiesRequest { string activity_id = 1; }
message SimilarActivity { string id = 1; bool is_current = 2; string title = 3; ActivityType type = 4;
  google.protobuf.Timestamp start_time = 5; optional double distance_m = 6; int64 moving_s = 7; int64 elapsed_s = 8;
  optional int32 avg_heart_rate = 9; optional int32 avg_power = 10; optional double pace_s_per_km = 11;
  int32 shared_segments = 12; double overlap_pct = 13; }
message SimilarSummary { int32 count = 1; int64 best_moving_s = 2; int64 current_moving_s = 3; int32 current_rank = 4; bool is_best = 5; }
message ListSimilarActivitiesResponse { int32 target_segments = 1; repeated SimilarActivity activities = 2; SimilarSummary summary = 3; }
```

**`ActivityService.ListAttachments`** – replaces `/attachments` (`attachments.go:119`).
Optional auth, same gate (`authView`, :74).
```proto
message ListAttachmentsRequest { string activity_id = 1; }
message Attachment { string id = 1; ContentUrl content = 2; string caption = 3; string content_type = 4; int32 width = 5; int32 height = 6; }
message ListAttachmentsResponse { repeated Attachment attachments = 1; }
```

**`SocialService.ListKudos`**, **`SocialService.ListComments`** – replace `/kudos`,
`/comments` (`engagement.go:95,147`); `socialGate` (:71) semantics, `PermissionDenied` instead
of 403 hides the block in the client.
```proto
message ListKudosRequest { string activity_id = 1; }
message ListKudosResponse { int32 count = 1; bool has_kudos = 2; repeated UserCard kudosers = 3; }
message ListCommentsRequest { string activity_id = 1; PageRequest page = 2; }
message Comment { string id = 1; string body = 2; google.protobuf.Timestamp created_at = 3; UserCard author = 4; }
message ListCommentsResponse { repeated Comment comments = 1; PageResponse page = 2; }   // oldest first
```

**`ActivityService.GetActivityManageInfo`** – replaces `/manage` (`activity_manage.go:89`).
Owner only. `ListActivitySources` is implemented on core `feat/activity-service-parity` with the
plain `ActivitySource` rows (detached ones included); add the insight fields to it instead of a
new message:
```proto
message GetActivityManageInfoRequest { string activity_id = 1; }
message SourceInsight {
  ActivitySource source = 1;               // existing message
  string worker_release = 2; string parser_version = 3; string dataset_version = 4; string reparse_with = 5;
  string upgrade = 6; /* ""|new_parser|new_dataset */ string worker_package = 7; bool reparse_eligible = 8;
  bool has_blob = 9; string raw_content_type = 10; int64 raw_size_bytes = 11; bool has_stream = 12; int32 lap_count = 13;
  string status_reason = 14; string reimport_reason = 15; optional google.protobuf.Timestamp last_reimported_at = 16;
  bool is_primary = 17; repeated string won_field_groups = 18;
}
message FieldProvenance { string field = 1; string field_label = 2; string source_id = 3; string provider = 4;
  string decided_by = 5; /* rule|manual */ google.protobuf.Timestamp synced_at = 6; }
message GetActivityManageInfoResponse { google.protobuf.Timestamp merged_at = 1; string primary_source_id = 2;
  repeated SourceInsight sources = 3; repeated FieldProvenance provenance = 4; }
```

**`ActivityService.GetParsedSource`** – replaces `/sources/{sid}/parsed`
(`activity_sources.go:100`). Return `ActivitySourcePayload` (decode `payloadJSON` → domain →
proto); the client shows it as a tree, not raw JSON.
```proto
message GetParsedSourceRequest { string activity_id = 1; string source_id = 2; }
message GetParsedSourceResponse { ActivitySourcePayload payload = 1; }
```

**`ActivityService.GetExportOptions`**, **`ActivityService.ExportActivity`** – replace
`/export/options` and `/export.{gpx,tcx,fit}` (`activity_export.go:33,105`).
```proto
message GetExportOptionsRequest { string activity_id = 1; }
message GetExportOptionsResponse { map<string, bool> available = 1; }  // gps, altitude, distance, speed, heart_rate, power, cadence, temperature, title
enum ExportFormat { EXPORT_FORMAT_UNSPECIFIED = 0; GPX = 1; TCX = 2; FIT = 3; }
message ExportActivityRequest { string activity_id = 1; ExportFormat format = 2; repeated string exclude = 3; }
message ExportActivityResponse { ContentUrl file = 1; string filename = 2; string content_type = 3; }
```
`ExportActivity` renders to the blob store (or a short-lived cache) and returns a signed URL;
this is the one RPC in the list with a side effect, so it does **not** get `NO_SIDE_EFFECTS`,
but it must be allowed for read-only tokens (prefix `Export` is already in the read list).

**`ActivityService.GetActivityMapImage`** – optional if `Activity.map_image` (signed URL) is
added; otherwise `{ activity_id } → { ContentUrl image }` with the owner/viewer trimming of
`activity_map_image.go:166`.

### H3: StatsService, SegmentService, MetricsService

**`StatsService.GetStats`** – replaces `GET /api/stats` (`stats.go:15`).
```proto
message GetStatsRequest {}
message YearStat { int32 year = 1; int32 count = 2; double distance_m = 3; double moving_s = 4; double elevation_gain_m = 5; }
message GetStatsResponse { Totals totals = 1; repeated Facet by_sport = 2; repeated Facet by_discipline = 3;
  repeated YearStat by_year = 4; optional Activity longest_distance = 5; optional Activity longest_duration = 6; }
```

**`StatsService.GetTrainingLoad`** – replaces `GET /api/analysis` (`analysis.go:19`).
```proto
message GetTrainingLoadRequest { int32 days = 1; }   // default 180, max 3650
message TrainingLoadDay { string date = 1; /* YYYY-MM-DD, UTC */ double ctl = 2; double atl = 3; double tsb = 4; double tss = 5; }
message GetTrainingLoadResponse { repeated TrainingLoadDay days = 1; double current_ctl = 2; double current_atl = 3; double current_tsb = 4; }
```

**`ActivityService.ListRecords`** – declared, implement it over
`BestEfforts.ListPersonalRecords` (`records.go:12`); `UserRecord` (activity.proto:529) fits.

**`StatsService.GetBestEffortHistory`** – replaces `GET /api/best-efforts/history`
(`best_effort_history.go:21`).
```proto
message GetBestEffortHistoryRequest { ActivityType type = 1; BestEffortMetric metric = 2; BestEffortWindowKind window_kind = 3; int32 window_value = 4; }
message BestEffortHistoryItem { string activity_id = 1; string title = 2; google.protobuf.Timestamp start_time = 3; double achieved_value = 4; bool is_best = 5; }
message GetBestEffortHistoryResponse { double best = 1; int32 count = 2; repeated BestEffortHistoryItem items = 3; }
```

**`StatsService.GetHeatmap`** – replaces `GET /api/activities/heatmap`
(`activities_heatmap.go:44`). Polylines instead of GeoJSON (10x smaller on mobile).
```proto
message GetHeatmapRequest { ActivityFilter filter = 1; enum Resolution { RESOLUTION_UNSPECIFIED = 0; SEC_30 = 1; SEC_5 = 2; } Resolution resolution = 2; }
message HeatmapTrack { string activity_id = 1; ActivityType type = 2; string discipline = 3; EncodedPolyline polyline = 4; }
message GetHeatmapResponse { int32 matched = 1; int32 with_track = 2; bool truncated = 3; ActivityFacets facets = 4; repeated HeatmapTrack tracks = 5; }
```

**`SegmentService`** – mount it. Needed: `ListSegments` with the user-centric row
(`segments_list.go:32`), `GetSegment` + `ListMySegmentEfforts` (`segment_detail.go:32`).
```proto
message ListSegmentsRequest { optional ActivityType type = 1; PageRequest page = 2; }   // limit default 50, max 200
message UserSegmentStats { int32 segments = 1; int32 efforts = 2; int32 prs = 3; int32 crs = 4; int32 external = 5; int32 native = 6; }
message UserSegmentRow { Segment segment = 1; int32 effort_count = 2; double best_elapsed_s = 3; google.protobuf.Timestamp last_effort_at = 4; bool has_pr = 5; bool has_cr = 6; }
message ListSegmentsResponse { UserSegmentStats stats = 1; /* first page only */ repeated UserSegmentRow segments = 2; PageResponse page = 3; }
```
`Segment` (segment.proto:39) needs `string polyline` + `int32 polyline_precision` (or
`EncodedPolyline`), `optional double max_grade`, `optional ClimbCategory climb_category` if not
present. `SegmentEffort` gets `int32 personal_rank`, `bool is_personal_record`.

**`MetricsService`** – mount it; `QueryMetricSeries`/`ListMetrics` for training-load series are
optional once `GetTrainingLoad` exists. Add:
```proto
// replaces GET /api/health (health.go:17)
message ListHealthSeriesRequest { string type = 1; /* Steps|HRV|RestingHR|Sleep|Weight|WaterIntake */ int32 days = 2; }  // default 90, max 3650
message HealthSample { string day = 1; double value = 2; string unit = 3; string provider = 4; }
message ListHealthSeriesResponse { string type = 1; repeated HealthSample series = 2; }
// replaces GET /api/athlete/metrics (athlete.go:44)
message ListAthleteMetricsRequest {}
message AthleteMetricSpec { string key = 1; string label = 2; string unit = 3; double min = 4; double max = 5; }
message AthleteMetricEntry { string id = 1; string key = 2; string effective_date = 3; double value = 4; }
message ListAthleteMetricsResponse { repeated AthleteMetricSpec specs = 1; repeated AthleteMetricEntry entries = 2; }
```

### H4: NotificationService, ExternalAccountService, SocialService, AuthService

**`NotificationService.CountUnread`** – declared; implement. **`StreamNotifications`** –
declared; implement after H0.2 (server stream, one `NotificationEvent` per message, heartbeat
every 30 s).

**`ExternalAccountService`** – mount; `ListExternalAccounts` returns the REST `accounts` row
(`sync_handlers.go:54`): extend `ExternalAccount` (auth.proto:459) with `int32 imported`,
`int32 provider_total`, `optional google.protobuf.Timestamp watermark`, `bool discovering`,
`int32 poll_interval_seconds`, `bool auto_import_enabled`, `QueueCounts queue`,
`string assigned_worker`, `repeated WorkerPool worker_pools`, `int32 out_of_date`,
`optional string connection_id`, and a `RateLimitBudget {Window short; Window daily}` with
`Window {int32 used; int32 limit; google.protobuf.Timestamp resets_at}`. Response also carries
`int32 default_poll_interval_seconds`.
```proto
message WorkerPool { string name = 1; string package = 2; string parser_version = 3; string release = 4; bool online = 5; bool compatible = 6; }
message QueueCounts { int32 pending = 1; int32 in_progress = 2; int32 done = 3; int32 failed = 4; int32 skipped = 5; }
// replaces GET /api/connections (oauth_connect.go:121)
message ListConnectionsRequest {}
message Connection { string id = 1; string provider = 2; string display_name = 3; string label = 4; string client_id = 5; bool has_secret = 6; bool oauth = 7; }
message ProviderInfo { string provider = 1; string display_name = 2; bool oauth = 3; repeated WorkerPool worker_pools = 4; }
message ListConnectionsResponse { repeated Connection connections = 1; repeated ProviderInfo providers = 2; }
// replaces GET /api/accounts/{id}/history (sync_handlers.go:469)
message ListSyncHistoryRequest { string account_id = 1; PageRequest page = 2; }   // 50
message SyncEvent { string kind = 1; int32 count = 2; string detail = 3; string external_id = 4; google.protobuf.Timestamp occurred_at = 5;
  string activity_id = 6; string external_url = 7; string status = 8; }
message ListSyncHistoryResponse { repeated SyncEvent events = 1; PageResponse page = 2; }
// replaces GET /api/accounts/{id}/queue/items (sync_handlers.go:548)
message ListQueueItemsRequest { string account_id = 1; repeated string statuses = 2; /* default pending,in_progress,failed */ PageRequest page = 3; }   // cap limit at 500
message QueueItem { string id = 1; string item_type = 2; string external_id = 3; string status = 4; int32 priority = 5; int32 attempts = 6; string last_error = 7;
  google.protobuf.Timestamp created_at = 8; optional google.protobuf.Timestamp item_time = 9; optional google.protobuf.Timestamp started_at = 10;
  optional google.protobuf.Timestamp completed_at = 11; string external_url = 12; }
message ListQueueItemsResponse { repeated QueueItem items = 1; PageResponse page = 2; }
```
`/api/webhook-endpoints` is admin/setup information; not needed by the client.

**`SocialService`** – new service, optional-auth reads mirror the REST gates
(`social.go`, `clubs.go`, `profiles.go`, `share.go`).
```proto
message ListFollowingFeedRequest { PageRequest page = 1; }   // 30, max 100
message ListFollowingFeedResponse { repeated FeedItem items = 1; PageResponse page = 2; }   // source=FOLLOWING
message ClubCard { string id = 1; string slug = 2; string name = 3; bool is_public = 4; int32 member_count = 5; google.protobuf.Timestamp created_at = 6; }
message ListClubsRequest { PageRequest page = 1; }
message ListClubsResponse { repeated ClubCard clubs = 1; PageResponse page = 2; }
message GetClubRequest { string slug = 1; }
message GetClubResponse { ClubCard club = 1; string description = 2; bool is_member = 3; string my_role = 4; }
message ListClubMembersRequest { string slug = 1; PageRequest page = 2; }
message ClubMember { UserCard user = 1; string role = 2; }
message ListClubMembersResponse { repeated ClubMember members = 1; PageResponse page = 2; }
message ListClubFeedRequest { string slug = 1; PageRequest page = 2; }
message ListClubFeedResponse { repeated FeedItem items = 1; PageResponse page = 2; }
message GetProfileRequest { string username = 1; }
message GetProfileResponse { UserCard user = 1; bool is_public = 2; bool is_self = 3; bool is_following = 4;
  int32 followers_count = 5; int32 following_count = 6; repeated ProjectedActivity activities = 7; }
message GetProjectedActivityRequest { string activity_id = 1; }
message GetProjectedActivityResponse { ProjectedActivity activity = 1; UserCard owner = 2; bool is_owner = 3; }
message GetSharedActivityRequest { string token = 1; }   // public; token is the authorization
message GetSharedActivityResponse { ProjectedActivity activity = 1; UserCard owner = 2; }
message ListReviewQueueRequest {}
message ReviewItem { string id = 1; string title = 2; ActivityType type = 3; google.protobuf.Timestamp start_time = 4; string match_confidence = 5; }
message ListReviewQueueResponse { repeated ReviewItem items = 1; }
```
Errors: private club / hidden activity → `PermissionDenied`; blocked or unknown → `NotFound`
(same information hiding as the REST handlers).

**`AuthService.GetQuota`** – replaces `GET /api/quota` (`quota.go:57`):
`{} → { int32 activities_used; int32 activities_limit; bool unlimited; bool over_limit }`.

### Scope table to publish (proposal)

| Scope | RPCs |
|---|---|
| none (public) | GetLoginMethods, GetInstanceInfo, GetSharedActivity |
| any valid token | GetCurrentUser, GetQuota |
| activities:read | ActivityService reads, StatsService, GetHeatmap, ListRecords, GetBestEffortHistory |
| segments:read | SegmentService reads, ListActivitySegmentEfforts |
| training:read | GetTrainingLoad, GetActivityZones, MetricsService (health, athlete metrics) |
| social:read | SocialService reads, ListKudos, ListComments, ListAttachments of others |
| profile:read | GetProfile (self), ListGear*, ListGearDefaults |
| notifications:read / notifications:write (new) | ListNotifications, CountUnread, StreamNotifications / MarkNotificationRead, MarkAllNotificationsRead |
| connections:read (new) | ExternalAccountService reads, ListConnections |
| profile:write (new) | UpdateUserPreferences |

Today no read RPC checks a specific scope (any token passes `ScopeInterceptor` for reads);
enforcing the table is a behavior change for existing OAuth clients and MCP, so ship the table
as documentation first and enforcement in a minor release with a CHANGELOG note.

## Checklist for core (copy into the tracking issue)

- [x] H0.1 h2c (core `feat/transport-instance-auth`)
- [x] H0.2 streaming interceptors (core `feat/activity-service-parity`; also implemented there:
      `ListActivitySources`, `DeleteActivity`, `DetachSource`, `LinkSourceToActivity`,
      `ReimportSource`, `ListRecords`, `PresignActivityUpload`, `UploadActivity`, plus
      `DELETE /api/activities/{id}` for REST parity)
- [x] H0.4 every Unimplemented RPC is commented as planned or browser-only (decision: none deleted)
- [ ] H0.5 read paths moved out of `package main`
- [x] H1 GetInstanceInfo, loopback redirect ports, DCR metadata, scope docs + new scopes, Logout, Register, GetQuota (core `feat/transport-instance-auth`)
- [ ] H2 ActivityService reads + SearchActivities + StatsService.GetOverview + ListHomeFeed (DONE: signed content incl. public blob endpoint, map images, photo variants, exports; ListAttachments, GetActivityMapImage, GetExportOptions, ExportActivity, Activity.map_image)
- [ ] H3 GetStats, GetTrainingLoad, ListRecords, GetBestEffortHistory, GetHeatmap, SegmentService, MetricsService
- [ ] H4 CountUnread, StreamNotifications, ExternalAccountService, SocialService, GetQuota
- [ ] H5 StreamActivityEvents, authorized-apps page, buf push
- [ ] Each group: CHANGELOG entry, `buf breaking` green, docs page for the scope table, release tag the client README can pin
