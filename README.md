# Dominion

A Minecraft Forge 1.20.1 mod adding a full **nations, diplomacy & war** system designed for war-themed modpacks. Integrates with FTB Teams for party coordination and FTB Chunks for territory claiming.

---

## Features

### Nations

- Create a named nation with a **2–5 letter TAG** and a **custom colour**
- Open or invite-only recruitment
- Persistent across server restarts (stored in world `data/nationsforge_nations.dat`)
- Optional description text

### Hierarchy

| Rank | Responsibilities |
|---|---|
| **Sovereign** | Founder. Full control. Can transfer leadership. |
| **Chancellor** | Deputy. All administrative powers except disbanding. |
| **General** | Military: declare war, propose peace, invite/kick members. |
| **Diplomat** | Diplomacy: set alliances, trade pacts, rivalries. |
| **Citizen** | Basic member. No governance powers. |

### Diplomacy

| Relation | Effect |
|---|---|
| **Alliance** | Full military alliance, shared goals |
| **Trade Pact** | Friendly cooperation |
| **Neutral** | Default, no commitment |
| **Rivalry** | Public hostility declared |
| **War** | Active state of war |

### GUI (press **[N]** in-game)

- **Overview** — Nation stats, treasury, score, war/alliance summary, leaderboard
- **Members** — Full member list with ranks; promote/demote/kick from UI
- **Diplomacy** — See and change relations with all known nations
- **Browse** — Browse all nations, accept pending invites, join open nations
- **Settings** — Edit nation name/tag/colour/description (leaders only)

### HUD Overlay

A compact badge in the top-right corner shows your nation, rank, active wars and ally count at a glance.

### Admin Commands (`/nation`)

```
/nation list
/nation info <name>
/nation create <name> <TAG> <player>     (op only)
/nation disband <name>                   (op only)
/nation join <player> <nation>           (op only)
/nation kick <player>                    (op only)
/nation setrank <player> <RANK>          (op only)
/nation setrelation <A> <B> <TYPE>       (op only)
/nation score <nation> <amount>          (op only)
/nation reload                           (op only)
```

### FTB Teams Integration (optional)

When FTB Teams is loaded, nation membership can be synced to FTB party teams. To activate full API integration, add the FTB Teams dependency into `build.gradle` and implement `FTBTeamsProxy`.

---

## Building

```bash
./gradlew build
```

The compiled jar will be in `build/libs/`.

---

## Installation

1. Place the compiled jar in your `mods/` folder.
2. Start the server — nation data is saved automatically per world.

---

## Compatibility

- Minecraft **1.20.1**
- Forge **47.2.0+**
- FTB Teams (optional, soft-dependency)

---

*Made for the Verko modpack war simulation.*
