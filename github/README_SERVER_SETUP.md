#  SMP Server Mod Setup & Distribution Guide

This custom Fabric mod (`smprules`) enforces all of your Minecraft server rules automatically.

---

## 🛠️ How to Compile the `.jar` File

There are two super easy ways to build `smprules-1.0.0.jar`:

### Option A: Using any IDE (IntelliJ IDEA / VS Code / Eclipse)
1. Open the project folder `fabric-smp-rules` in IntelliJ IDEA (or VS Code).
2. Open the Gradle tab on the right side.
3. Double-click **`build`** (or run `gradle build` in terminal).
4. Your finished mod JAR will be located at: `build/libs/smprules-1.0.0.jar`!

### Option B: Automatic Free GitHub Build (.github/workflows/build.yml)
* We added a **GitHub Actions automated build workflow**! If you push this folder to GitHub, GitHub will compile `smprules-1.0.0.jar` automatically in 30 seconds and let you download the `.jar` under **Actions -> Artifacts**.

---

##  World Border Management Commands (`/smpadmin border`)

* `/smpadmin border set <blocks>` — Set World Border size centered on World Spawn.
* `/smpadmin border expand <blocks>` — Expand World Border by specified block count.
* `/smpadmin border status` — View current border size and bounds.

---

##  Anti-Elytra Combat Fleeing System

* Allowed for normal survival travel.
* Flight & Firework Rockets hard-blocked during 2-minute PvP Combat Tag.
* Does **NOT** trigger when fighting mobs (Zombies, Skeletons, boss fights).

---

##  Player PvP Trophy Head Drops

* Custom skin head drops on PvP elimination.
* Custom Display Name: `💀 <VictimName>'s Trophy Head`
* Lore: `⚔ Slain by: <KillerName>`, `📅 Date: YYYY-MM-DD`, `⏰ Time: HH:mm:ss`.

---

##  End Dimension Lock System

| Command | Action |
| :--- | :--- |
| `/smpadmin endunlock` | Officially **UNLOCKS** the End dimension for everyone and broadcasts a server alert! |
| `/smpadmin endlock` | Locks the End dimension. |
| `/smpadmin endstatus` | Check if the End is currently locked or unlocked. |

---

##  Admin Dashboard: Activity, PvP Kill Feed & Playtime (`/smpdashboard`)

| Command | Action |
| :--- | :--- |
| `/smpdashboard` | View recent security flags & dashboard overview. |
| `/smpdashboard kills` | View timestamped **PvP Kill Feed** ("Who killed who"). |
| `/smpdashboard deaths` | View **Deaths & Kills Leaderboard** for all players. |
| `/smpdashboard playtime` | View **Total Playtime** for all players. |
| `/smpdashboard activity` | View recent **Session Join/Leave logs** & durations. |

---

##  Manual Admin Ban & Unban Override Commands

| Command | Action |
| :--- | :--- |
| `/smpban <player> [reason]` | Manually ban a player with a custom reason. |
| `/smpunban <player_name>` | Manually unban a player and reset their AntiCheat strikes to 0. |

---

##  Full Admin Commands (`/smpteam`, `/smpadmin`, `/smpdashboard`, `/smpban`) *(Requires OP)*

| Command | Action |
| :--- | :--- |
| `/smpadmin border set <size>` | Set World Border centered on World Spawn. |
| `/smpadmin border expand <amount>` | Expand World Border size by specified blocks. |
| `/smpadmin border status` | View current World Border size and center bounds. |
| `/smpadmin endunlock` | Unlock the End dimension for all players. |
| `/smpadmin endlock` | Lock the End dimension. |
| `/smpban <player> [reason]` | Manually ban a player with a custom reason. |
| `/smpunban <player_name>` | Manually unban a player and reset their strikes to 0. |
| `/smpdashboard` | View timestamped anti-cheat security log dashboard. |
| `/staffmode` or `/smpstaff` | Toggle between **Survival Profile** and **Staff Admin Profile**. |
| `/smpadmin spawntoggle` | Toggle Spawn Protection **ON or OFF** for testing. |
| `/smpadmin resetdata` | Clear all test teams, claims, and test permissions before official launch. |
| `/smpteam create peaceful <name> <leader>` | Create a **Peaceful Team** (assigned unique color). |
| `/smpteam create pvp <name> <leader>` | Create a **PvP Team** (assigned unique color). |

---

##  Player Commands
| Command | Action |
| :--- | :--- |
| `/smpclaim claim` | Claims current chunk + outlines border in team's unique color. |
| `/smpclaim unclaim` | Unclaims current chunk. |
| `/smpclaim highlight` | Outlines current chunk border in team's color particles. |
| `/smpclaim view` | Outlines **ALL** claimed team chunks in team color particles! |
| `/smpteam info [team]` | View team member list, class, color, and claim count. |
| `/smprules` | View server rules summary. |
