<div align="center">

# LUDO-T

**An automatic four-player Ludo simulation with a twist**

![Java](https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Apache Maven](https://img.shields.io/badge/Apache_Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit-25A162?style=flat&logo=junit5&logoColor=white)
![H2 Database](https://img.shields.io/badge/H2_Database-09476B?style=flat)

[Overview](#overview) | [Gameplay](#gameplay) | [Architecture](#architecture) | [Setup](#setup) | [Testing](#testing)

</div>

---

## Overview

LUDO-T is an Assignment 2 Java application that runs an automatic, four-player Ludo simulation. Each colour has four pieces and a distinct move-selection strategy. The project provides a Swing board and event log, a TCP game server and client, a console simulation, and H2-backed game history.

The GUI connects to a server-owned simulation. The server publishes board snapshots and stores the event messages; the client does not choose moves.

## Gameplay

- Players roll a six-sided die. A piece enters from base on a six; rolling a six or capturing an opponent grants another roll.
- Three consecutive sixes trigger the block-breaking rule. Pieces move clockwise or counter-clockwise, and groups of pieces can form blocks.
- Landing on an opponent captures its piece and sends it to base. Home entry requires at least one capture; counter-clockwise travel also requires two visits to the player's approach cell.
- A mystery cell appears after standard-path movement begins, remains active for four rounds and then relocates. Landing on it can send a piece to base, a start/approach cell, or Alpha, Beta or Gamma. These destinations can apply temporary movement, restriction or direction effects.
- Red favours captures, Green favours forming and keeping blocks, Yellow favours deployment and progress, and Blue cycles pieces while preferring counter-clockwise mystery-cell landings.
- The first player is selected by repeated dice-roll tie-breaks. A game ends when one player gets all four pieces Home or after 10,000 rounds.

## Architecture

The application separates game rules, application services, persistence and presentation:

- `ludot.model`, `ludot.movement`, `ludot.game`, `ludot.behaviour`, `ludot.state` and `ludot.mystery` implement the board, turn flow, move rules, player strategies and temporary effects.
- `ludot.application` owns the simulation service, repository contract and immutable `GameSnapshot` representation.
- `ludot.server.GameServer` serves clients, serialises requests with scheduled round advancement and broadcasts snapshots. It accepts up to 32 clients; each connection has reader and writer threads. Requests and round updates share a single worker.
- `ludot.client.GameClient` sends asynchronous requests and receives replies and pushed updates. `ludot.gui.GameWindow` and `BoardPanel` render the snapshot and paged event log using Swing.
- `ludot.persistence.JdbcGameRepository` stores simulation snapshots and messages; `DatabaseMain` starts the H2 TCP service.
- `ludot.Main` runs a standalone console simulation without the server or database.

## Technology stack

| Component | Implementation                                          |
| --------- | ------------------------------------------------------- |
| Language  | Java 17 (Maven compiler release)                        |
| Build     | Apache Maven; compiler, Surefire, JAR and Shade plugins |
| GUI       | Java Swing from the JDK                                 |
| Database  | H2 2.4.240 over JDBC                                    |
| Tests     | JUnit Jupiter 5.10.2                                    |

## Project structure

```text
src/
├── main/java/ludot/
│   ├── Main.java                  # Standalone console entry point
│   ├── application/               # GameService, GameSnapshot, repository contract
│   ├── behaviour/                 # Four automatic player strategies
│   ├── client/                    # TCP client and automatic request client
│   ├── config/                    # GameSettings
│   ├── game/, model/, movement/   # Simulation, board and rules
│   ├── gui/                       # GuiMain, GameWindow, BoardPanel
│   ├── mystery/, state/, random/  # Game effects and random source
│   ├── network/                   # Wire message helpers
│   ├── output/                    # Console and game messages
│   ├── persistence/               # H2 database and JDBC repository
│   └── server/                    # ServerMain and GameServer
└── test/java/ludot/               # JUnit tests and process/test fixtures
```

## Setup

Prerequisites: JDK 17 and Maven. From the project root, package the application:

```sh
mvn clean package
```

The shaded JAR is `target/ludo-t-1.0.jar`. For the networked GUI, start each component in a separate terminal, from the project root:

```sh
java -cp target/ludo-t-1.0.jar ludot.persistence.DatabaseMain
java -cp target/ludo-t-1.0.jar ludot.server.ServerMain
java -jar target/ludo-t-1.0.jar
```

The database must be available before the game server starts; the GUI then connects to the default game-server address and port. The JAR manifest launches `ludot.gui.GuiMain`.

To run the automatic console simulation without the server stack:

```sh
java -cp target/ludo-t-1.0.jar ludot.Main
```

## Configuration

Defaults are defined in `ludot.config.GameSettings` and the entry points:

| Setting                  | Default                                       |
| ------------------------ | --------------------------------------------- |
| Game server              | Port `5050`; binds to `0.0.0.0`               |
| H2 TCP database          | Port `9092`; database file `data/ludot.mv.db` |
| JDBC URL                 | `jdbc:h2:tcp://localhost:9092/ludot`          |
| Database user/password   | `sa` / empty password                         |
| Rounds per server update | `100`                                         |
| Maximum game length      | `10,000` rounds                               |

`DatabaseMain` accepts `[port] [database-directory]`. `ServerMain` accepts `[port] [jdbc-url] [bind-host]`. `GuiMain` accepts `[server-address] [port]`; its defaults are `localhost` and `5050`. The automatic request client also accepts `[server-address] [port] [requests-per-client]` (default `localhost`, `5050`, `200`; maximum `500`). Set `LUDOT_DB_PASSWORD` in the environment to use a non-empty H2 password; set it for both database and game-server processes.

## Socket protocol

The game server uses UTF-8, newline-delimited TCP messages. A request is a positive request ID and command separated by a tab:

```text
<request-id>\tSTART
<request-id>\tSTATUS
<request-id>\tLOG <session-id> <offset>
```

`START` begins a new simulation (or returns the active one); `STATUS` returns its current snapshot; `LOG` retrieves a page of up to 2,000 saved messages. Replies are `<id>\tOK\t<snapshot>` or `<id>\tERROR\t<base64-error>`. Unsolicited updates use ID `0` and type `UPDATE`.

A snapshot has seven tab-separated fields: revision, session ID, round, status, mystery-cell location, 16 comma-separated piece locations, and Base64-encoded recent log text. Base64 text is UTF-8. Requests are processed in order by the server's single worker; each client connection is limited to 128 characters per request line. The GUI and `GameClient` implement this protocol.

## Persistence

H2 stores data in `data/ludot.mv.db` by default. The JDBC repository creates `simulations` for the latest snapshot per session and `game_messages` for ordered session output. Each update saves the snapshot and new messages in one SQL transaction; output is returned in pages.

On server startup, the latest snapshot and its messages are loaded. A simulation saved as `RUNNING` is marked `INTERRUPTED`; starting again creates a new session. The H2 TCP service is started separately by `DatabaseMain` and is not embedded in the game server process.

## Testing

Tests are under `src/test/java/ludot`, organised around strategies, movement, model setup, game completion, server requests, GUI behaviour and persistence/architecture. Test support includes scripted randomness, isolated databases, subprocess fixtures and Swing window helpers.

Run the JUnit suite with:

```sh
mvn test
```

The Maven Surefire configuration uses three forked JVMs. This README records the test organisation and command; it does not report a test run or outcome.

## Limitations and security notes

- Players are automated; the GUI displays and starts the simulation but does not provide manual move controls.
- The game protocol has no application-level authentication or encryption. The default game server binds to all network interfaces, so restrict access with host/network controls and do not expose it to untrusted networks.
- The default database account uses an empty password. Configure `LUDOT_DB_PASSWORD` and restrict H2 TCP access before using data outside a local development environment.
- The implementation uses one server worker for round advancement and request handling. It is a coursework simulation, not a hardened or horizontally scalable game service.

---

## Contact Information

**Developer**: Dillon Fernandez  
**Email**: dillonfernandez@gmail.com  
**Institution**: APIIT

---

<div align="center">
&#x20; <p><strong>Disclaimer</strong></p>
&#x20; <p><em>This is an academic project developed for educational purposes and is not intended for commercial use.</em></p>
</div>
