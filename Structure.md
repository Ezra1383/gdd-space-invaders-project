# GDD Space Invaders Project

A Java remake/expansion of the classic Space Invaders, built as a Game Design & Development project. The game features multiple biomes, boss fights, power-ups, and a custom weapons/faction system built on top of a Swing-based sprite engine.

## Team Members
- Mohammad Rahemi — 6611695
- Mankirat Kaur — 6611585

## Features
- Multiple biomes/levels with progressively harder enemy waves
- Boss encounters
- Power-up system (Clone, Corruption, Shield, Speed Up, Weapon Up)
- Custom weapons and projectile/bullet pattern system
- Title scene and scene management
- Background music and sound effects

## References
This project is based from this 
[Space Invader](https://github.com/janbodnar/Java-Space-Invaders) repository.

## Architecture — how the files connect

The codebase is a Swing application. Execution flows top-down through a small
number of "controller" classes, which delegate to a sprite hierarchy, a
data-driven enemy/spawn system, and a set of shared asset/audio utilities.

### 1. Startup and scene management
- [Main.java](src/gdd/Main.java) is the entry point. It just constructs a [Game.java](src/gdd/Game.java) on the Swing event thread.
- `Game` extends `JFrame` and owns the two top-level screens: [TitleScene.java](src/gdd/scene/TitleScene.java) and [Scene1.java](src/gdd/scene/Scene1.java) (both `JPanel`s). `Game.loadTitle()`/`loadScene2()` swap which panel is added to the frame's content pane. It also kicks off a background thread that pre-decodes ship art ([Background.java](src/gdd/Background.java), [GifSprites.java](src/gdd/GifSprites.java)) while the title screen is showing, so gameplay never stalls on first-spawn image loads.
- `TitleScene` shows the title image + controls legend and, on SPACE, calls back into `Game.loadScene2()`.
- `Scene1` is the actual game: it runs a 60 FPS Swing `Timer`, and each tick calls an `update()` (game logic) followed by `repaint()` → `paintComponent()` (rendering). This is the classic Swing-game "update/draw" loop, and virtually all gameplay logic lives in this one class.
- [Global.java](src/gdd/Global.java) is a static constants holder (board size, image paths, etc.) imported (`import static gdd.Global.*`) by almost every other class, so there's no config object passed around — just shared constants.

### 2. The sprite hierarchy
Every visible, moving thing extends the abstract base [Sprite.java](src/gdd/sprite/Sprite.java), which holds position (`x/y/dx/dy`), visibility/dying state, and the shared `collidesWith()` rectangle-overlap check:
- [Player.java](src/gdd/sprite/Player.java) — the player's ship. Tracks weapon tier, clones, shields, and "corruption" (how much of the final boss the player has absorbed).
- [Enemy.java](src/gdd/sprite/Enemy.java) — a generic enemy: HP, a fly-in-then-hold movement pattern, and an optional [BulletPattern.java](src/gdd/sprite/BulletPattern.java) it fires on a timer. [Boss.java](src/gdd/sprite/Boss.java) extends `Enemy` and adds per-biome attack "movesets" (screen-wide ray beams, closing walls, and — for the final boss, Nemesis — a combination of everything fought so far).
- [BlueSelf.java](src/gdd/sprite/BlueSelf.java) — a special invincible enemy used only in the final-loop twist, where the corrupted player fights their unbeatable past self.
- [Bullet.java](src/gdd/sprite/Bullet.java) — enemy projectiles; [Shot.java](src/gdd/sprite/Shot.java) is the player-fired equivalent; [WaveShot.java](src/gdd/sprite/WaveShot.java) extends `Bullet` for the Kla'ed boss's sweeping wall attack.
- [Destruction.java](src/gdd/sprite/Destruction.java) and [SheetBlast.java](src/gdd/sprite/SheetBlast.java) are one-shot explosion animations dropped into `Scene1`'s `explosions` list when something dies.
- `gdd.powerup.*` — [PowerUp.java](src/gdd/powerup/PowerUp.java) is the abstract base (`upgrade(Player)`); [Clone.java](src/gdd/powerup/Clone.java), [Shield.java](src/gdd/powerup/Shield.java), [WeaponUp.java](src/gdd/powerup/WeaponUp.java), [Corruption.java](src/gdd/powerup/Corruption.java) and [SpeedUp.java](src/gdd/powerup/SpeedUp.java) are the concrete drops, spawned by `Scene1.killEnemy()` on a random roll.

`Scene1` holds a `List<...>` of every sprite type (enemies, shots, enemy bullets, powerups, explosions) and each frame: moves them (`act()`), checks collisions between the lists, and draws whatever is visible.

### 3. Enemy data and spawning
Enemies aren't hard-coded — they're described by data and driven by a pluggable spawn engine:
- [EnemyType.java](src/gdd/sprite/EnemyType.java) is an enum of every enemy "template" (HP, speed, bullet pattern, which faction's art it uses). `Enemy`'s constructor just reads its fields.
- [ProjectileType.java](src/gdd/sprite/ProjectileType.java) similarly defines each faction's bullet sprite, pre-rotated for any firing angle.
- [Faction.java](src/gdd/Faction.java) is the enum tying a biome together (`NAIRAN`, `KLAED`, `VOID`) — it points at the folder of ship art each biome's enemies/boss/backdrop use.
- [SpawnSource.java](src/gdd/SpawnSource.java) is an interface: "given the current frame and how many enemies are alive, return the spawns due now." [SpawnDetails.java](src/gdd/SpawnDetails.java) is the plain data record it returns (type, position, target column). Two implementations exist: [StaticSpawnSource.java](src/gdd/StaticSpawnSource.java) (a fixed, hand-authored list) and [Director.java](src/gdd/Director.java) (the one actually used) — a runtime wave/phase/boss-gate sequencer that escalates difficulty and rotates through biomes automatically.
- `Scene1.update()` polls `spawnSource.poll(frame, enemies.size())` every frame and turns any resulting `SpawnDetails` into real `Enemy`/`Boss` objects.

### 4. Shared art and audio utilities
- [Images.java](src/gdd/Images.java) — low-level image loading/scaling/tiling/color-keying helpers, used by nearly every sprite class to cut sub-images out of sprite sheets.
- [GifSprites.java](src/gdd/GifSprites.java) — loads a `Faction`'s whole ship pack (base, engine, weapon-flash, shield-flare, wreck, projectile layers) into ready-to-draw animation frames.
- [Background.java](src/gdd/Background.java) — the scrolling parallax backdrop per biome, swapped by `Scene1` whenever `Director` moves into a new phase.
- [Weapons.java](src/gdd/Weapons.java) — the player's own bullet sprites, shared with `Boss` because the final boss (Nemesis) fires the player's weapons back at them.
- [Music.java](src/gdd/Music.java) — looping background-music track switcher (one biome/boss theme at a time); [Sfx.java](src/gdd/Sfx.java) — sound effects synthesized in code (no audio files needed); [AudioPlayer.java](src/gdd/AudioPlayer.java) — a simple one-off clip player used by `TitleScene` for the title music.
- [RealityBreak.java](src/gdd/RealityBreak.java) — draws the extra floating "reality tear" windows used in the final boss's last phase and the final-loop twist.

### Typical call chain for one gameplay frame
`Scene1.GameCycle` (Swing `Timer` tick) → `update()` → `spawnSource.poll()` (via `Director`) → new `Enemy`/`Boss` objects created from `EnemyType` → each `Enemy.act()`/`maybeFire()` (using `BulletPattern`) → collision checks against `Player`/`Shot`/`Bullet` lists → `killEnemy()`/`killPlayer()` spawn `Destruction`/`SheetBlast` explosions and `PowerUp` drops → `repaint()` → `paintComponent()` draws the `Background`, then every sprite list, then the HUD.