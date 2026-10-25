# Dominoes

An Android app for playing, learning and keeping score in dominoes.

## Features

**Play** against 1–3 computer opponents (Easy, Normal, Hard) in four games:

| Game | How it works | Default target |
|---|---|---|
| Draw | Match an open end; if you can't, draw from the boneyard. | 60 |
| Block | Like Draw, but you pass instead of drawing. | 60 |
| All Fives | Score whenever the open ends add up to a multiple of 5. The opening double is a spinner (four sides). | 100 |
| All Threes | Like All Fives with multiples of 3. | 90 |

- The default targets and scoring follow the domino tables in **Red Dead Redemption 2**, as described in player guides. Fives and Threes round end-of-hand points to the nearest 5 or 3, and the match ends the moment someone reaches the target.
- A coach rates your moves and explains better ones. There's also a Hint button and a running log of what the other players revealed by drawing or passing.
- The table is laid out like a real one, with snaking lines, crosswise doubles and a spinner cross. It animates as tiles are played.
- A match in progress is saved, so you can leave and resume it later.

**Play with friends** online, live or turn by turn.
- Invite friends with a code, a link or a QR code, or open your game to anyone on the server. Fill empty seats with computer players.
- Pick any target score, set a time per turn, and start a rematch when the game ends.
- In turn-by-turn games you get a notification when it's your move.
- This needs a small game server that you host. See [docs/ONLINE.md](docs/ONLINE.md).

**Same Wi-Fi**: play at one table, each on your own phone.
- One phone hosts the game and the others join by scanning its QR code. Everyone needs to be on the same Wi-Fi, or on the host's hotspot.
- No internet or server needed: the host's phone runs the game. Keep the app open on it while you play.
- Works like online play otherwise: lobby settings, time per turn, rematch.

**Learn**
- 20 lessons across five sections: basics, strategy, scoring games, advanced play and Red Dead Redemption 2.
- "Find the best move" puzzles, taken from real games between strong AIs.
- A tip of the day.

**Scores**: a score keeper for real-life games.
- Any number of players from 2 to 6.
- Win by reaching a target score, or by having the lowest score after a set number of rounds (Mexican Train style).
- Past games are kept.

**More**
- Stats per game: wins, coach agreement and puzzle streaks.
- Settings: theme, Material You colours, table felt colour, computer speed, vibration, and whether the coach is on by default.

## Project structure

```
engine/   Pure Kotlin game logic, no Android: rules, scoring, AI and coach, puzzles, table layout.
protocol/ Messages between app and server (shared, so they can't drift apart).
server/   Ktor game server for online play: SQLite storage, WebSockets, computer seats.
app/      Jetpack Compose UI.
  data/        DataStore repositories (settings, stats, saved game, score keeper)
  feature/     play, learn, scorekeeper and more screens with their ViewModels
  navigation/  Type-safe Navigation Compose routes, bottom bar or navigation rail
  ui/          Theme and shared components (tiles, table)
```

- **Architecture:** unidirectional data flow, with ViewModels exposing `StateFlow` and manual dependency injection through `AppContainer`. Game state is `@Serializable` so it can be saved and resumed.
- **Dependencies:** versions live in `gradle/libs.versions.toml`.
- **Data from version 1:** score keeper games and training stats from version 1 are migrated automatically.

## Build and test

You need JDK 17 or newer and the Android SDK (API 35).

```sh
./gradlew :engine:test              # rules, scoring, AI, layout, puzzles
./gradlew :server:test              # online API, hidden hands, live updates, storage
./gradlew :app:testDebugUnitTest    # ViewModels, lessons, migrations, UI tests, online end-to-end
./gradlew :app:lintDebug
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

To run the game server locally, use `./gradlew :server:run`. To host it with Portainer, see [docs/ONLINE.md](docs/ONLINE.md) and `deploy/portainer/docker-compose.yml`. The server image is published to `ghcr.io/dwzg/dominoes-server`. App and server stay compatible across versions (see "Compatibility" in the same doc).

## Releases

Download the app from the repository's **Releases** page. Each release comes with an APK signed with the permanent release key, so it installs over the previous one, and with a matching server image (`ghcr.io/dwzg/dominoes-server:<version>`).

To publish a release, go to **Actions → Release → Run workflow** and enter a version such as `2.1.0`. The one-time setup is in [docs/RELEASING.md](docs/RELEASING.md).

The app connects to the server set as `dominoServerUrl` in `gradle.properties`, currently `https://dominoes.convoluted.space`. Players can change it under More → Settings.

Every push also builds a test APK as the `domino-apk` artifact of the **Build APK** run. It's signed with a temporary key, so it can't update a release.

Not affiliated with Rockstar Games.
