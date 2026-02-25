# Dominion — Changelog

All notable changes to this mod are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).  
Versioning follows **MAJOR.MINOR.PATCH** — patch bumps on every build.

---

## [1.1.2] — 2026-02-25

### Added
- **Nation Flags** — every nation can now have a custom banner-style flag
  - `NationFlag` — data model: base colour (16 dye colours) + up to 6 overlay pattern layers (24 pattern types)
  - `FlagEditorScreen` — dedicated GUI: live 3× banner preview, 4×4 base-colour picker, per-layer pattern cycle button + colour swatch, add/remove layers
  - `C2SUpdateFlagPacket` — sends flag changes from client to server
  - `SettingsPanel` — new “⚑ Edit Flag” button opens the flag editor (requires Sovereign or Chancellor rank)
  - `OverviewPanel` — nation flag rendered as a 2× banner icon in the top-right corner of the overview header
  - Flag persisted in world NBT and synced to all clients on every mutation

---

## [1.1.1] — 2026-02-25

### Fixed

- `OverviewPanel` — removed duplicate `class` declaration that caused compile failure
- `FTBTeamsProxy` — removed orphaned old class body using wrong commands (`create/join/leave`)
- `OverviewPanel` — `screen.minecraft()` → `screen.getMinecraft()` (correct method name)

### Added

- `NationPowerCalculator` — multi-factor power formula: members × 100, territory × 50, alliances × 200, trade pacts × 75, treasury / 5000, age days × 5, minus wars × 100
- `NationPowerCalculator.Tier` enum — Tribe / Village / Town / City-State / Kingdom / Empire / Superpower with display colours
- `FTBChunksHelper` — reflection-based claimed-chunk counter per nation (no hard dep on FTB Chunks)
- `FTBTeamsReflectionHelper` — reflection helper to resolve FTB Teams server-team UUID by name
- `NationTickHandler` — server-tick periodic recalculation of territory + power (every 6 000 ticks / ~5 min)
- `NationCreatedEvent`, `NationDisbandedEvent`, `PlayerJoinedNationEvent`, `PlayerLeftNationEvent`, `NationRelationChangedEvent` — custom Forge events (KubeJS compatible)
- `NationsScreen` — public `switchTab(int)` and `getMinecraft()` helpers for sub-panels
- `NationHudOverlay` — now shows power tier badge (e.g. `✦ Kingdom`) and territory chunk count
- `CreateNationScreen` — live preview banner (coloured `[TAG] Name` + description), character counters, colour swatch, improved layout

### Changed

- `Nation` — added `territory` (long) and `power` (long) fields; both serialised to NBT and synced to client
- `NationManager` — fires all custom Forge events at every lifecycle point (create, disband, join, leave, kick, diplomacy); calls `NationPowerCalculator.recalculate()` on membership changes
- `OverviewPanel` — redesigned: no-nation state shows welcome panel with Browse / Found buttons instead of leaderboard; nation state shows colour swatch, tier badge, stat strip (Members / Chunks / Power), treasury, rank, description, wars/allies count, leaderboard in right column

---

## [1.1.0] — 2026-02-24

### Added

- Renamed mod display name from **NationsForge** to **Dominion** (mod ID unchanged: `nationsforge`)
- `FTBTeamsHelper` — `onNationCreated`, `onNationDisbanded`, `onPlayerJoinedNation`, `onPlayerLeftNation` wired to `NationManager`
- `FTBTeamsProxy` — rewrote with correct FTB Teams 2001.x commands: `create_team`, `join_player`, `kick_player`, `delete_team`
- Admin commands `/nation disband` and `/nation kick` now sync with FTB Teams

### Fixed

- FTB Teams party not being created on nation founding (wrong command names in old proxy)

---

## [1.0.1] — 2026-02-23

### Fixed

- `NationsScreen.rebuildWidgets()` — access modifier compile error
- `NationSavedData` — replaced 1.20.4-only `SavedData.Factory<>` with `computeIfAbsent` 3-arg call
- `NationSavedData` — replaced `NbtAccounter.unlimitedHeap()` with `buf.readNbt()` for 1.20.1 compatibility
- Removed stale imports that caused compile failures

---

## [1.0.0] — 2026-02-22

### Added

- Initial release — nations / diplomacy / war system
- Nations: create, disband, join, leave, kick, invite
- Ranks: Sovereign, Chancellor, General, Citizen
- Diplomacy: Alliance, Trade Pact, Rivalry, War, Neutral
- Treasury system with deposit/withdraw commands
- FTB Teams integration stub
- Tabbed GUI (Overview, Members, Diplomacy, Browse, Settings)
- HUD overlay badge
- Server-side saved data (`NationSavedData`) with full NBT persistence
- Network packets for client sync
- Keybind `[N]` to open the nations GUI
