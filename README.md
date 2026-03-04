<div align="center">
  <h1>PaperTpa</h1>
  <p><strong>Simple, powerful teleport requests for Minecraft servers</strong></p>
  
  [![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4+-brightgreen)](https://www.minecraft.net/)
  [![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  [![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.java.com/)
</div>

## Features

- **GUI-Driven TPA** — Accept/Deny/View-Info inventory GUI instead of plain chat buttons
- **Reputation System** — Trusted 🟢 / Neutral 🟡 / Suspicious 🔴 tiers based on ratings & trap %
- **Rating System** — 1–5 ⭐ ratings + trap-report toggle, prompted after every successful teleport
- **Trap Detection** — Automatic trap flag if the teleported player dies within the configurable window
- **Anti-Abuse** — Cooldowns, duplicate-request prevention, per-player request limits, rating spam protection
- **Resource Pack / ItemsAdder support** — Every GUI item is configurable with `material`, `custom-model-data`, and `itemsadder` namespace ID
- **PlaceholderAPI** — `%tpa_rating%`, `%tpa_trap_percent%`, `%tpa_reputation_tier%`, `%tpa_total_accepted%`
- **Async everywhere** — All SQLite calls run off the main thread via CompletableFuture

## Commands

| Command                    | Description                                          | Permission               |
|----------------------------|------------------------------------------------------|--------------------------|
| `/tpa <player>`            | Send a teleport request                              | `papertpa.tpa`           |
| `/tpaccept [player]`       | Accept a teleport request                            | `papertpa.tpaccept`      |
| `/tpdeny [player]`         | Deny a teleport request                              | `papertpa.tpdeny`        |
| `/tptoggle`                | Toggle teleport requests on/off                      | `papertpa.toggle`        |
| `/tpcancel`                | Cancel your pending request or teleport warmup       | `papertpa.tpa`           |
| `/tplist`                  | List all your pending teleport requests              | `papertpa.tpa`           |
| `/tpaview <player>`        | Open the request GUI for a pending request           | `papertpa.tpa`           |
| `/tpstats [player]`        | View TPA statistics GUI for yourself or another player | `papertpa.tpa`         |
| `/tpsettings`              | Open the settings GUI (toggle requests, auto-accept, notifications) | `papertpa.tpa` |
| `/tpnotify`                | Toggle post-teleport rating notifications on/off     | `papertpa.tpa`           |
| `/tpauto`                  | Toggle auto-accept mode for incoming requests        | `papertpa.auto`          |
| `/tprate`                  | Open the rating GUI after a teleport                 | `papertpa.tpa`           |
| `/papertpa reload`         | Reload the plugin configuration                      | `papertpa.admin`         |

## Permissions

| Node                          | Description                                  | Default |
|-------------------------------|----------------------------------------------|---------|
| `papertpa.tpa`                | Send/cancel/list/view teleport requests      | true    |
| `papertpa.tpaccept`           | Accept teleport requests                     | true    |
| `papertpa.tpdeny`             | Deny teleport requests                       | true    |
| `papertpa.toggle`             | Toggle teleport requests                     | true    |
| `papertpa.auto`               | Toggle auto-accept mode                      | true    |
| `papertpa.admin`              | Admin commands (reload)                      | op      |
| `papertpa.bypass`             | Bypass disabled requests                     | op      |
| `papertpa.cooldown.bypass`    | Bypass cooldown timers                       | op      |
| `papertpa.delay.bypass`       | Bypass teleport warmup delay                 | op      |
| `papertpa.stats.others`       | View another player's stats                  | op      |
| `papertpa.immunity.bypass`    | Bypass post-teleport immunity (can hit immune players) | op |
| `papertpa.*`                  | All PaperTpa permissions                     | op      |

## Configuration

Every GUI item section in `config.yml` supports three ways to define the icon:

```yaml
# 1. Plain vanilla item
material: LIME_STAINED_GLASS_PANE
custom-model-data: 0
itemsadder: ""

# 2. Resource-pack custom model data
material: PAPER
custom-model-data: 1001
itemsadder: ""

# 3. ItemsAdder namespace:id (requires ItemsAdder plugin)
material: PAPER          # fallback if ItemsAdder not installed
custom-model-data: 0
itemsadder: "papertpa:accept_button"
```

Full example config snippet:

```yaml
settings:
  request-timeout: 60
  cooldown: 10
  teleport-delay: 5
  rating-delay: 30
  trap-window: 20
  tpback-on-trap: true
  min-ratings-for-leaderboard: 5

gui:
  request:
    title: "&6Teleport Request"
    accept-item:
      material: LIME_STAINED_GLASS_PANE
      custom-model-data: 0
      itemsadder: ""
      name: "&a✔ Accept"
      lore:
        - "&7Click to accept the teleport request"
```

## Soft Dependencies

- **PlaceholderAPI** — optional; enables `%tpa_*%` placeholders
- **ItemsAdder** — optional; enables `itemsadder: namespace:id` item resolution in GUI config
- **LuckPerms** — optional; uses LuckPerms API for permission checks when present

## Building

```bash
./gradlew shadowJar
# Output jar: build/libs/PaperTpa-<version>.jar
```

## License

MIT — see [LICENSE](./LICENSE)

Made with ❤️ by [IndrajeethY](https://github.com/Praveenkumar801)
