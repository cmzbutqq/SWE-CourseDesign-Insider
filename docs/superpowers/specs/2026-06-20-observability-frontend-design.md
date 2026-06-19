# Unified Observability Frontend Design

## Summary

Upgrade the current Vue portal from a light navigation shell into a dense observability workspace. The first version prioritizes visible completeness over custom charting: reuse Grafana, SkyWalking, and Prometheus views wherever they already solve the problem well, and use the local frontend for layout, navigation, summaries, drill-down context, and fallback handling.

This version does **not** introduce unified platform login. It assumes a coursework/demo environment where read-only embedding is acceptable. Mobile layout is out of scope. The target experience is desktop-first and panel-dense.

## Core Decisions

- Keep the product centered on a unified dashboard plus drill-down pages, not system-by-system navigation.
- Prefer third-party panels over reimplementing charts when Grafana, SkyWalking, or Prometheus already provide the needed view.
- Do not build a new metrics or tracing backend. The frontend aggregates existing platform APIs plus embedded third-party views.
- Allow high panel density. More useful panels are better than minimal whitespace, as long as sections stay grouped and labeled.
- Do not implement unified platform login or per-system login orchestration.

## Experience Architecture

### Top-level navigation

Replace the current three-link shell with five primary entries:

- `总览` at `/overview`
- `节点` at `/nodes`
- `服务` at `/services`
- `链路` at `/tracing`
- `监控调试` at `/debug`

Add one new detail route:

- `服务详情` at `/services/:id`

### Drill-down path

Users should be able to move through:

`总览 -> 节点详情 / 服务详情 -> 链路 / 原始监控`

The user should never need to open the Grafana, SkyWalking, or Prometheus home pages to continue diagnosis.

### Shared page structure

All major pages follow the same hierarchy:

1. Context header with title, object identity, refresh action, and desktop-only controls.
2. Local summary area rendered by the app.
3. Embedded observability area with grouped third-party panels.
4. Deep-dive area with related tables, links, or raw monitoring views.

## System Responsibilities

### Grafana

Grafana is the main charting layer.

Use it for:

- global infrastructure trends on the overview page
- node resource panels on node detail pages
- service runtime panels on service detail pages

Grafana embeds should use panel-level iframe URLs, not full dashboard home navigation. The frontend wraps each embed in local cards and section containers.

### SkyWalking

SkyWalking is the trace and topology layer.

Use it for:

- service topology
- trace list views
- slow request / high latency trace exploration
- service-level chain analysis

SkyWalking content appears mainly in the dedicated `链路` page and in the `调用链` tab on service detail pages. Node detail may expose limited chain context or links, but it should not become trace-heavy.

### Prometheus

Prometheus is the raw monitoring and validation layer.

Use it for:

- target health
- raw query/debug pages
- scrape validation
- job-level metric inspection

Prometheus should not dominate the overview page. It belongs mainly in the `监控调试` page and optional raw-monitoring tabs on node and service detail pages.

## Page Design

### Overview

The overview page becomes the main desktop dashboard.

#### Local summary block

Render locally:

- total nodes
- online nodes
- abnormal services
- unresolved alerts
- latest heartbeat risk summary
- top risky nodes and services

#### Embedded observability blocks

Group panels into:

- `基础资源`
- `应用健康`
- `链路观测`

Most panels in the first two groups come from Grafana. The chain group can mix SkyWalking embeds and local trace-entry cards.

#### Lower-page density

Allow a second screen of additional panels rather than keeping the page sparse. The goal is to look like a real operations cockpit, not a marketing landing page.

### Nodes

#### Node list

Upgrade the node list into an asset-and-health table:

- retain existing filters
- add recent load/health cues where available
- keep clear entry points into node detail

#### Node detail

Node detail should act like a single-host observability station.

Top section:

- node identity
- IP
- status
- last heartbeat
- recognized service count

Main content uses tabs:

- `资源监控`
- `服务清单`
- `链路关联`
- `原始监控`

`资源监控` is Grafana-heavy. `服务清单` stays local. `链路关联` surfaces service-to-trace paths. `原始监控` exposes Prometheus-target validation.

### Services

#### Service list

Keep grouping and filtering, but turn the page into a real service observability index instead of a static inventory list.

#### Service detail

Add a dedicated service detail page with:

- service name
- type
- owning node
- port
- process
- metrics path

Tabs:

- `运行指标`
- `调用链`
- `基础信息`
- `Prometheus 调试`

The `运行指标` tab is mostly Grafana. `调用链` is mostly SkyWalking. `基础信息` stays local. `Prometheus 调试` is raw validation.

Services missing `metricsPath` must show a visible warning in the first screen, not only inside tables.

### Tracing

Create a dedicated `链路` page anchored around SkyWalking views:

- topology
- trace list
- slow request analysis

This page should feel like a first-class route, not an external-tool shortcut page.

### Debug

Create a `监控调试` page centered on Prometheus:

- targets
- common query entry points
- scrape verification
- raw metric exploration

This page is allowed to be less polished than the overview page. Its value is operational verification, not visual elegance.

## Embedding Strategy

### Frontend wrapper components

Add shared components for:

- `ObservabilityPanel`: local title, source label, iframe body, failure state, fallback link
- `ObservabilityGrid`: grouped panel layout and sizing
- `ContextHeader`: shared detail-page header
- `RiskSummary`: shared risk/anomaly summary cards or lists

These components own the app shell. Third-party systems own the heavy visual content.

### URL strategy

Use frontend-safe embed URLs rather than exposing users to third-party home pages.

- Grafana should be served from a sub-path and embedded through iframe panel URLs.
- Prometheus should be served from a sub-path and embedded for raw/debug views.
- SkyWalking may be embedded through a direct absolute URL if sub-path hosting is not verified during implementation; the app still wraps it inside local sections.

### Failure handling

Every embedded panel must have a non-blank failure state showing:

- panel title
- source system
- load failure message
- fallback open action

The page must remain useful even if one embedded system is down.

## Engineering Changes

### Frontend

- update the sidebar and router for the new information architecture
- add `ServicesDetailPage.vue`, `TracingPage.vue`, and `DebugPage.vue`
- refactor overview and node detail into grouped sections and tabs
- introduce shared observability wrapper components
- centralize panel URL generation so pages do not hardcode third-party links inline

### Backend

Add one minimal new endpoint:

- `GET /api/services/{id}`

This avoids client-side list searching and gives service detail a stable data source.

Keep current overview, nodes, node detail, services, and trends APIs. Do not add custom chart APIs for views already covered by embedded Grafana/SkyWalking/Prometheus content.

### Grafana

Expand dashboard provisioning from a single overview dashboard into at least:

- `platform-overview`
- `node-detail`
- `service-detail`

These dashboards should support route context through dashboard variables such as node/job/service selectors where possible.

### Container and runtime config

Allow config changes needed for embedding:

- Grafana: enable iframe embedding and anonymous viewer access; configure root URL and sub-path serving when proxied
- Prometheus: configure external URL / route prefix when served from a frontend proxy sub-path
- SkyWalking: keep current direct UI serving unless sub-path support is explicitly verified during implementation
- Frontend dev server: extend proxy support beyond `/api` to cover embedded observability paths where applicable

## Acceptance Criteria

- The frontend home page visibly contains local summaries plus multiple third-party observability panels.
- Node detail pages show real embedded resource monitoring views, not only static summary bars.
- Service detail pages exist and expose both runtime and chain-analysis views.
- The user can reach Grafana-, SkyWalking-, and Prometheus-derived content without manually navigating to each tool home page.
- The app still shows useful local summaries and fallback messages if one embedded system is unavailable.
- Desktop presentation is dense and intentional; mobile adaptation is not required.

## Test Plan

- Frontend route tests cover the new top-level pages and service detail route.
- Component tests cover observability panel success, failure, and fallback rendering.
- Page tests verify that overview, node detail, and service detail render grouped panel sections.
- Manual verification uses the full observability and nodes profiles to confirm:
  - iframe panels load
  - page drill-down paths work
  - Grafana, SkyWalking, and Prometheus content all appear inside the frontend

## Non-Goals

- unified platform login
- mobile-specific layout work
- replacing third-party charting with custom local chart implementations
- building a new tracing or metrics query backend
- polishing visual consistency between all embedded third-party UIs

## Assumptions

- This is a coursework/demo environment, so read-only embedding is acceptable.
- High panel density is desirable.
- If Grafana, SkyWalking, or Prometheus already solve a visual problem well, reuse them instead of rebuilding the view locally.
- The frontend shell, summaries, navigation, and drill-down context are what make the product feel unified.
