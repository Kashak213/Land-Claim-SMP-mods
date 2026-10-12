# SMP Rules Mod

A custom, server-side Fabric mod built specifically for managing SMP gameplay, enforcing PvP rules, land claiming, and preventing cheating. 

## Features

### 🛡️ PvP & Combat
* **PvP Countdown:** Automatically enables PvP after a set number of real-world days.
* **Combat Logging Prevention:** Hitting a player initiates a 2-minute combat tag. If a tagged player disconnects, they instantly die, drop their inventory and a trophy head, and broadcast a combat-logging elimination message to the server.
* **Trophy Heads:** When a player dies in PvP, they drop a player head. The head has custom lore showing the killer, date, and time (in Eastern Time). If you place the head down as a block and break it later, this lore is perfectly preserved!
* **Friendly Fire:** Melee/arrow damage is blocked between teammates, but explosion damage (TNT/Creepers) is still allowed.
* **The End Lock:** Access to The End is disabled until an admin manually unlocks it.

### 🏕️ Land Claiming & Teams
* **Teams:** Players can create teams and invite others.
* **Chunk Claiming:** Teams can claim chunks (`/smpclaim claim`) to protect their builds. Maximum claims depend on the number of players in the team (configurable).
* **Chunk Highlights:** `/smpclaim highlight` will render highly visible, glowing particle borders around the chunk you are standing in for 10 seconds.
* **Claim Messages:** Walking in and out of claims displays a custom text HUD above your hotbar, instantly telling you whose land you are entering or if you are in the Wilderness.
* **Villages:** Villages are strictly public territory and cannot be claimed.
* **Spawn Protection:** Only admins can build or break blocks in the spawn area.

### 🚫 Anti-Cheat & Restrictions
* **Banned Items:** Crafting, using, or placing restricted items (e.g., Netherite gear, End Crystals, Maces, Respawn Anchors) is blocked.
* **Elytra Combat:** Attacking other players while flying with an Elytra is strictly forbidden.
* **3-Strike System:**
  1. **Strike 1:** Warning.
  2. **Strike 2:** The player's entire inventory and armor are wiped and replaced with **Ink Sacs**.
  3. **Strike 3:** Permanent ban.

### 📊 Dashboard & Stats
* **Session Tracking:** Tracks playtime and disconnects (all timestamps use Eastern Time).
* **Death Logging:** Records the exact location and cause of death.

---

## ⌨️ Player Commands

* **`/smphelp`** — Opens the custom help menu showing server rules, End/PvP status, and a command cheat sheet.
* **`/smprules`** — Displays a full list of all server rules.
* **`/smpteam list`** — See all active teams.
* **`/smpteam info [name]`** — See details, members, and claim limits for a specific team.
* **`/smpclaim claim`** — Claim the chunk you are currently standing in for your team.
* **`/smpclaim unclaim`** — Unclaim the chunk you are standing in.
* **`/smpclaim highlight`** — Show glowing 10-second borders around the chunk you are in.
* **`/smpclaim view`** — View a list of all chunks your team has claimed.
* **`/smpclaim info`** — See who owns the chunk you are currently standing in.

---

## 🛠️ Admin Commands

* **`/smpadmin pvpstart`** — Starts the 48-hour countdown to automatically enable PvP.
* **`/smpadmin pvpstatus`** — Checks the remaining time on the PvP countdown.
* **`/smpadmin pvpon`** — Forcefully enables PvP immediately.
* **`/smpadmin pvpoff`** — Forcefully disables PvP.
* **`/smpadmin unlockend`** — Unlocks the End dimension.
* **`/smpadmin tpspawn [player]`** — Teleports yourself (or a target player) to world spawn.
* **`/smpadmin resetinventories`** — Clears the inventory of every online player.
* **`/smpban <player> [reason]`** — Manually permabans a player.
* **`/smpunban <player>`** — Unbans a player and clears their strikes.
* **`/smpteam setpeaceful <team>`** — Forces a team to be peaceful (cannot raid or be raided).
* **`/smpteam setpvp <team>`** — Forces a team to be PvP-enabled.
* **`/smpteam disband <team>`** — Forcefully deletes a team.
* **`/smpteam create <name> peaceful/pvp <leader>`** — Create a team.
* **`/smpteam addplayer <team> <player>`** — Forcefully add a player to a team.
* **`/smpteam removeplayer <player>`** — Kicks a player from their team.
* **`/smpadmin markpaid <team>`** — Mark a peaceful team as having paid their tax for the 3-day cycle.
* **`/smpadmin markunpaid <team>`** — Mark a peaceful team as UNPAID for the 3-day cycle.
* **`/smpdashboard`** — View the overall security & activity dashboard.
* **`/smpdashboard tax`** — View the 3-day Tax tracker and payment status of all Peaceful teams.
* **`/smpdashboard kills`** — View all PvP kills.
* **`/smpdashboard deaths`** — View all player deaths.
* **`/smpdashboard playtime`** — View total playtime for players.
* **`/smpdashboard activity`** — View recent session activity logs.
* **`/smpdashboard strikes`** — View AntiCheat strikes for all players.
