# Dominion — Changelog

All notable changes to this mod are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).  
Versioning follows **MAJOR.MINOR.PATCH** — patch bumps on every build.

---

## [1.2.2] — 2026-02-25

### Fixed

- **FTBTeams syncToAll** — `addMember` / `removeMember` now call `markDirty()` +
  `syncToAll(team)` via reflection, so every client is notified of membership
  changes in real time (was missing in 1.2.1).
- **FTBChunksHelper** — fixed `getManager()` call (was passing a `LevelAccessor`
  arg that doesn't exist) and fixed chunk-team UUID lookup chain
  (`chunk.getTeamData().getTeam().getId()` — `getTeamId()` never existed on
  `ClaimedChunk`).
- **Bot chunk claiming** — replaced fragile `ftbchunks admin claim_as` command
  with a new `FTBChunksProxy` that uses the FTBChunks Java API directly.

### Added

- **Immersive diplomacy system**
  - Proposing a relation change now creates a *pending request* instead of
    applying instantly.  The target nation's online members receive a
    notification and see the request in the Diplomacy tab.
  - Bot nations respond instantly using `BotNationAI.evaluateIncomingRequest()`
    with personality-aware probabilities (aggressive bots reject more, diplomatic
    bots accept more).
  - `DiplomacyRequest` data class persisted to `nationsforge_nations.dat` so
    requests survive server restarts.
  - Three new network packets: `C2SDiplomacyRequestPacket`,
    `C2SDiplomacyResponsePacket`, `S2CDiplomacyNotifyPacket`.

- **Redesigned Diplomacy panel**
  - Two-column layout: nation list on the left, selected-nation info + propose
    controls on the right.
  - Optional message field for every proposal (max 256 chars).
  - "Incoming Requests" strip at the bottom shows pending requests with
    **Accept** / **Decline** buttons.
  - Outgoing awaiting-response indicator shown in the right panel.

- **Full-screen UI** — `NationsScreen` now scales to up to 820 × 500 pixels,
  dynamically capped to `screenW − 40` × `screenH − 40`.

---

## [1.2.1] — 2026-02-26

### Fixed

- **FTB Teams integration** — team member add/remove now uses Java reflection
  (`AbstractTeam.addMember` / `removeMember`) instead of non-existent console
  commands, so players are correctly added to/removed from their nation's team.
- **FTBTeamsReflectionHelper** — fixed `getManager(MinecraftServer)` call to the
  correct no-arg `getManager()`, resolving server-team UUID lookups for FTBChunks.

### Added

- **Bot nation capitals** — each bot nation now stores `capitalX`/`capitalZ`
  coordinates (persisted in NBT). Capitals are spread across ±4000 blocks with
  a minimum 400-block spacing between any two nations.
- **Bot FTB Teams + chunk claiming** — `WorldBotGenerator` now creates a FTB
  server team (`dominion_TAG`) and claims territory around each capital via
  `ftbchunks admin claim_as` so bot nations visibly own land from day one.
- **More bot nations** — world generation now spawns 15–24 bot nations (up from 8–14).
- **Browse panel — nation flag** — clicking a nation in Browse now shows its
  32×32 banner flag rendered via 2× PoseStack scaling.
- **Browse panel — full stats** — detail panel now shows Members, Territory,
  Treasury, Score, Recruitment, relation counts (⚔ Allies Trade Rivals),
  capital coordinates for bots, and description. List rows also display
  territory chunk count.
- **Browse panel — bot badge** — bot nations are marked `[BOT NATION]` in the
  detail panel and `[AI]` in the list.

---

## [1.2.0] — 2026-02-25

### Added

- **Nation Coins** — new craftable currency (`nationsforge:nation_coin`)
  - Craft: 1 Gold Ingot → 9 Nation Coins (and 9 coins back to 1 ingot)
  - Uses gold-nugget texture as placeholder; model at `assets/nationsforge/models/item/nation_coin.json`
  - `/nation deposit <amount>` — player transfers coins from inventory into national treasury
  - `/nation withdraw <amount>` — Sovereign/Chancellor withdraws coins from treasury to inventory

- **Passive treasury income** (every ~5-minute server tick cycle)
  - `+50 coins` per online member
  - `+10 coins` per claimed chunk (territory)
  - `+100 coins` per active trade pact
  - `+25 coins` per active alliance
  - Bot nations earn separately via `BotNationAI`

- **Bot Nations — World Ecosystem**
  - On first server start for a new world, `WorldBotGenerator` creates **8–14 AI-controlled nations** seeded from the world RNG
  - Each bot gets: random name, tag, description, colour, and procedurally generated banner flag (1–3 random layers)
  - Starting territory (12–48 chunks) and treasury (2500–5500 coins)
  - Initial diplomacy seeded between all bots: alliances, trade pacts, rivalries and wars
  - Generation announced in global chat with a list of notable nations
  - One-time per world — `worldBotGenerated` flag in `NationSavedData` prevents repetition

- **Bot Nation AI** (`BotNationAI`) — driven every 5-minute tick cycle
  - Passive income: `+10 coins/chunk` + `+30 coins/member-equivalent` + trade-pact bonus
  - Random events (per cycle): territorial expansion, population growth, diplomacy changes
  - World-event chat messages: expansion, war declarations, peace treaties, alliances, trade pacts, prosperity
  - All 5 relation types used dynamically (ALLIANCE, TRADE_PACT, NEUTRAL, RIVALRY, WAR)

- **Bot personality types** (`BotPersonality` enum): AGGRESSIVE, ECONOMIC, NEUTRAL, ISOLATIONIST (groundwork for future personality-weighted AI)

- **`NationSavedData` additions**: `worldBotGenerated` flag, `createBotNation()`, `getBotNations()`, `getPlayerNations()`

- **`Nation.isBot` field** — persisted to NBT, used to skip FTB Chunks reflection for AI nations

### Fixed

- **Overview flag rendering** — removed broken 2× PoseStack scaling that caused the flag to overlap the Tier label and render incorrectly; flag now drawn as a clean 16×16 GUI item at correct position

- **FTB Teams commands** — `createNationTeam` / `addPlayerToTeam` / `removePlayerFromTeam` / `deleteNationTeam` now try the modern `ftbteams server create/join/kick/delete` syntax first, falling back to legacy `create_team`/`join_player`/`kick_player`/`delete_team` variants; `runCmd` now returns a `boolean` indicating success

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
