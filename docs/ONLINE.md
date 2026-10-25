# Online play: running the game server

Online games go through a small server (the `server/` module). It deals the tiles,
checks every move, plays the computer seats and sends each player only their own hand.
Games are stored in a SQLite file.

The app connects to `https://dominoes.convoluted.space` by default (`dominoServerUrl`
in `gradle.properties`). Players can enter another server under
**More → Settings → Online server**.

## Hosting with Portainer and Nginx Proxy Manager

The server runs as a Docker container behind Nginx Proxy Manager, which handles HTTPS.
The app refuses plain `http://` servers on the internet.

### 1. The image

GitHub Actions publishes the server to `ghcr.io/dwzg/dominoes-server`, for amd64 and arm64:

| Tag | Updated |
|---|---|
| `latest` | On every push to `main` and every release |
| `2.1.0`, `2.1`, … | When that version is released (see [RELEASING.md](RELEASING.md)) |

Portainer needs to log in to pull it, because packages of a private repository are private:
1. On GitHub, create a token: **Settings → Developer settings → Personal access tokens → Tokens (classic)**, with only the `read:packages` scope.
2. In Portainer, go to **Registries → Add registry → Custom registry** and enter:
   - URL: `ghcr.io`
   - Username: your GitHub name
   - Password: the token

### 2. The stack

In Portainer, go to **Stacks → Add stack → Web editor** and paste
[`deploy/portainer/docker-compose.yml`](../deploy/portainer/docker-compose.yml).

It joins the `nginx-proxy-network` Docker network, so Nginx Proxy Manager can reach the
container by name. Change the network name if yours differs (Portainer → Networks).

### 3. Nginx Proxy Manager

Add a **Proxy Host** for your domain:
- **Scheme:** `http`
- **Forward Hostname:** `dominoes` (the container name)
- **Forward Port:** `8080`
- **Websockets Support:** on (live games need it)
- **SSL tab:** request a certificate and turn on **Force SSL**

### 4. Check it

`https://<your domain>/health` should say `ok`, and `https://<your domain>/version`
shows the server's version, e.g. `{"version":"2.1.0","protocol":1,"minProtocol":1}`.

If you get **502 Bad Gateway**, Nginx Proxy Manager can't reach the container. Check that:
- the container is running (Portainer → Containers → `dominoes` → Logs)
- it's on the same network as the proxy
- the forward hostname isn't `localhost`

### Updating

Open the stack in Portainer and choose **Update the stack** with **Re-pull image** on.
Releases can also do this for you through a Portainer webhook; see
[RELEASING.md](RELEASING.md#automatically-with-a-portainer-webhook).
Games in progress, players and their identities are kept. To stay on a specific release,
use e.g. `ghcr.io/dwzg/dominoes-server:2.1.0` as the image and change it deliberately.

### Backups

Everything is in the stack's data volume (`domino.db`). Stop the stack briefly and copy it, for example:
```sh
docker run --rm -v dominoes_dominoes-data:/data -v "$PWD":/backup alpine tar czf /backup/dominoes-backup.tgz -C /data .
```

## Compatibility between app and server versions

People update the app at different times, and the server is updated separately, so
both sides are built to keep working with older versions of the other:

- **Protocol versions.** Every request says which protocol the app speaks
  (`X-Dominoes-Protocol`; apps without it are protocol 1). The server reports what it
  speaks at `/version`.
- **Additive changes only.** New fields always have defaults, and nothing is renamed or
  removed. Apps ignore fields they don't know.
- **New features stay invisible to old apps.** If a new game mode or kind of event is
  added, the server leaves it out for apps that wouldn't understand it. An old app is
  asked to update only if it tries to open such a game.
- **A clear message instead of errors.** If the server ever has to stop supporting very
  old apps, those apps are told "Please update the app". If the app needs a newer server,
  it says so too.
- **The database only grows.** Schema changes are numbered, additive steps applied on
  start-up. Saved games are JSON with defaults, so an older server image can still read
  them if you roll back.
- **Tested.** `server/src/test/resources/compat/v1` holds real messages and a saved game from
  protocol 1. Tests check that every later version still reads them and still writes
  everything an old app expects. When the protocol changes, add a `v2` folder next to it
  (`WRITE_COMPAT_FIXTURES=1 ./gradlew :server:test`) and keep the old ones.

The rules for changing the protocol are at the top of
`protocol/src/main/kotlin/de/wtzg/domino/protocol/Compatibility.kt`.

## Trying it on your computer

```sh
./gradlew :server:run          # listens on http://localhost:8080
```

- **Android emulator:** use `http://10.0.2.2:8080` as the server.
- **Phone over USB:** run `adb reverse tcp:8080 tcp:8080`, then use `http://localhost:8080`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Port to listen on |
| `DATABASE` | `domino.db` | SQLite file; the image uses `/data/domino.db` on the volume |

## How it works

- **Players:** you pick a name. The server returns a random token that the app keeps; there are no passwords. Clearing the app's data means a new identity.
- **Games:** the host creates a game and shares the 6-letter code, the invite link (`https://<server>/join/CODE`, which opens the app) or the QR code in the lobby. Free seats get computer players when the host starts.
- **Public or private:** a game is invite only unless the host makes it open to anyone. Open games are listed for everyone on the server while they're in the lobby with a free seat; lobbies untouched for 24 hours drop off the list. Each player can list up to 3 open games at a time.
- **Time per turn:** the host can set a limit (live: 30 seconds to 5 minutes; turn by turn: 1 to 7 days). If the player to move runs out of time, the game ends there with the scores as they stand and nobody wins. The server checks every few seconds.
- **Rematch:** after a game ends, anyone at the table can ask for a rematch. The first to ask hosts a new lobby with the same settings, and everyone after joins it.
- **Live games** stream updates over a WebSocket while the game is open.
- **Turn-by-turn games:** while the app is closed, it checks every 15 minutes and notifies you when it's your turn.
- **Fairness:** every action carries the game version it was based on, so moves made on an outdated screen are refused. Other players' tiles are never sent to your phone.

## API

| Method | Path | |
|---|---|---|
| GET | `/health` | Liveness check |
| GET | `/version` | `{version, protocol, minProtocol}` |
| POST | `/players` | Register `{name}` → `{playerId, token, name}` |
| PATCH | `/players/me` | Rename |
| GET | `/games` | Your games |
| POST | `/games` | Create `{mode, players, target, pace, visibility, turnLimitSeconds}` (`visibility` is `PRIVATE` or `PUBLIC`, default `PRIVATE`; `turnLimitSeconds` 0 is no limit) |
| GET | `/games/open` | Public lobbies you could join |
| POST | `/games/join` | Join `{code}` |
| GET | `/games/{id}` | Your view of a game |
| POST | `/games/{id}/settings` | Host changes the game in the lobby `{mode, players, target, pace, turnLimitSeconds}`; never fewer seats than people seated |
| POST | `/games/{id}/rematch` | Sets up or joins the rematch of a finished game |
| POST | `/games/{id}/visibility` | Host lists or hides the lobby `{visibility}` |
| POST | `/games/{id}/start` | Host starts `{difficulty}` |
| POST | `/games/{id}/actions` | `{action: play/draw/pass/leave, version}` |
| WS | `/games/{id}/live?token=` | Your view, pushed on every change |
| GET | `/join/{code}` | Invite page that opens the app |

Authenticated calls send `Authorization: Bearer <token>`. Message formats are in
`protocol/src/main/kotlin/de/wtzg/domino/protocol/Protocol.kt`.
