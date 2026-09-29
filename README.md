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

LUDO-T is a Java 21 application that automatically simulates a four-player Ludo variant. Sixteen computer-controlled pieces move around a 52-cell shared track and colour-specific home paths; no human move selection is required. The implementation combines standard movement, capture, blockade, and finishing rules with bidirectional travel and temporary mystery-cell effects.

Two runtime modes are implemented:

- **Local:** one in-process simulation with a Swing board and event log.
- **Network:** an H2 service, a TCP game server, and one or more Swing observer clients. Requests may arrive concurrently, but games run serially and winners are persisted.

## Gameplay

- Four automated players (`RED`, `GREEN`, `YELLOW`, and `BLUE`) with four pieces each.
- The highest opening roll starts; only tied leaders reroll.
- A six releases a piece from base, then a coin toss sets its clockwise or counter-clockwise direction.
- Red prioritizes captures and avoids unnecessary blockades; Green favors blockade building; Yellow favors base entry, needed captures, and proximity to home; Blue rotates through pieces and varies mystery-cell preference by direction.
- A piece must make a capture before entering its home path. Clockwise pieces pass their approach once; counter-clockwise pieces must pass it twice. Reaching home requires an exact roll.
- Opponents are captured only on landing. Same-colour groups form blockades, move together by `dice value / group size`, stop opposing pieces, and can capture an equal-sized opposing blockade.
- A six or capture grants another roll. On a third consecutive six, an existing blockade is forcibly split; otherwise the roll is discarded.
- Once the first piece enters the shared track, a mystery cell appears after two completed rounds. It remains for four rounds, then respawns on a free track cell, avoiding its previous cell when another is available.
- Mystery effects teleport to Alpha, Beta, Gamma, base, the piece's starting square, or its approach square. Alpha doubles or halves movement temporarily, Beta pauses movement, and Gamma changes direction or redirects to Beta.
- A player wins when all four pieces reach home. A 10,000-round cap safely ends a simulation with no winner.

## Architecture

`GameBuilder` creates the board, random sources, rules, four strategy-specific players, turn manager, and logger. `GameEngine` selects the starting player and runs the automatic turn loop. A player selects a command; the engine executes it, applies capture and mystery effects, publishes state, checks for a winner, and decides whether a bonus turn is due.

```text
entry point -> GameBuilder -> GameEngine -> player strategy
                                      |-> Command -> Board / Piece state
                                      |-> RuleEngine -> movement / capture / block / mystery / win
                                      `-> GameLogger -> console, Swing, or socket clients
```

### Runtime flows

| Mode    | Execution flow                                                                     |
| :------ | :--------------------------------------------------------------------------------- |
| Local   | `Main` → `GameBuilder` → `GameEngine` → `GuiGameLogger` → `LudoFrame`              |
| Network | `ClientMain` → `ServerMain` → `GameEngine` → `SocketGameLogger` → all clients + H2 |

- `ServerMain` assigns each connection to a virtual thread and queues every `START_GAME` request on one game executor, preventing simulations from overlapping.
- `SocketGameLogger` broadcasts readable events and piece-state messages to all registered clients and saves the winner through `GameResultRepository`.
- `ClientMain` opens a read-only Swing observer, listens on a virtual thread, and requests one game automatically. Swing updates are dispatched on the event thread.

### Entry points

| Class                                   | Purpose                                                                           |
| :-------------------------------------- | :-------------------------------------------------------------------------------- |
| `com.ludot.Main`                        | Packaged-JAR entry point; opens local Swing mode and runs one game.               |
| `com.ludot.database.DatabaseMain`       | Creates/opens `./ludotdb`, then starts H2 TCP (`9092`) and web (`8082`) services. |
| `com.ludot.server.ServerMain`           | Starts the game server on TCP `5050`; requires H2 to be reachable.                |
| `com.ludot.client.ClientMain`           | Opens an observer and requests a game; accepts an optional server hostname.       |
| `com.ludot.client.AutomaticTestClients` | Opens two headless clients and submits five requests from each.                   |

## Technology stack

| Area                   | Implementation                                               |
| :--------------------- | :----------------------------------------------------------- |
| Language/runtime       | Java 21 language target                                      |
| Build/package          | Maven; `com.ludot:ludo-t:1.0.0` JAR                          |
| UI                     | Java Swing                                                   |
| Networking/concurrency | TCP sockets, executors, virtual threads, `CompletableFuture` |
| Persistence            | H2 Database Engine 2.3.232                                   |
| Tests                  | JUnit Jupiter 5.10.2; Maven Surefire 3.2.5                   |

H2 is the only non-JDK production dependency. JUnit API, engine, and parameter support are test-scoped.

## Project structure

```text
src/main/java/com/ludot/
├── board, model, state       Board topology and mutable piece state
├── rules, command            Rule evaluation and executable game actions
├── player, factory           Four strategies and object construction
├── engine, random            Simulation loop and replaceable random sources
├── gui, output               Swing rendering and event formatting
├── client, server            TCP observers, requests, and broadcasts
└── database                  H2 service and winner repository
src/test/java/com/ludot/      JUnit suite and deterministic test doubles
pom.xml                       Dependencies, Java release, plugins, JAR entry point
ludotdb.mv.db                 File-backed local game-results database
```

## Setup

### Prerequisites

- JDK 21+
- Apache Maven
- A graphical desktop for local mode or `ClientMain`
- Free ports `5050`, `9092`, and `8082` for network mode

Run all commands from the repository root.

### Build and test

```powershell
mvn package
```

This compiles the project, runs the tests, and creates `target\ludo-t-1.0.0.jar` with `com.ludot.Main` in its manifest.

> [!NOTE]
> The artifact is not an all-dependencies JAR. Database and server processes also need the H2 JAR on their classpath.

### Run locally

```powershell
java -jar target\ludo-t-1.0.0.jar
```

The application opens the board/event-log window and immediately runs one game. Local mode neither connects to the server nor persists its winner.

### Run in network mode

Start these components in separate PowerShell terminals, in order. The example assumes Maven's default local repository; adjust `$h2Jar` when using a custom repository.

1. Start H2:

```powershell
$h2Jar = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar'
java -cp "target\ludo-t-1.0.0.jar;$h2Jar" com.ludot.database.DatabaseMain
```

The relative database URL means the process's working directory selects the `ludotdb.mv.db` file.

2. Start the game server:

```powershell
$h2Jar = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar'
java -cp "target\ludo-t-1.0.0.jar;$h2Jar" com.ludot.server.ServerMain
```

3. Start one or more observers, using no argument for `localhost` or supplying a remote game-server host:

```powershell
java -cp "target\ludo-t-1.0.0.jar" com.ludot.client.ClientMain
java -cp "target\ludo-t-1.0.0.jar" com.ludot.client.ClientMain server-hostname
```

To queue ten games without opening Swing windows:

```powershell
java -cp "target\ludo-t-1.0.0.jar" com.ludot.client.AutomaticTestClients
```

## Configuration

There are no environment variables or external application configuration files. Gameplay values, ports, H2 URLs, and local database credentials are constants in source. The only runtime option is `ClientMain`'s optional server hostname; the game port remains `5050`.

## Socket protocol

Communication is newline-delimited text over TCP.

| Direction        | Message                                                     | Purpose                                                                    |
| :--------------- | :---------------------------------------------------------- | :------------------------------------------------------------------------- |
| Client → server  | `START_GAME`                                                | Case-insensitively queues one full simulation.                             |
| Server → clients | `RESET_BOARD`                                               | Returns the displayed pieces to base before a game.                        |
| Server → clients | <code>PIECE&#124;id&#124;type&#124;colour&#124;index</code> | Publishes one piece location; `colour` may be `-` and `index` may be `-1`. |
| Server → clients | Any other line                                              | Appends a human-readable event to the log.                                 |

Clients receive broadcasts only after registration; a client joining mid-game receives no initial snapshot.

## Persistence

When `ServerMain` starts, `GameResultRepository` creates `game_results` if needed. Each completed server game inserts only the winning colour and timestamp. Moves, board states, unfinished games, and client identities are not stored; local mode does not use H2.

```sql
CREATE TABLE IF NOT EXISTS game_results (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    winner VARCHAR(20) NOT NULL,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
)
```

## Testing

```powershell
mvn test
```

The suite contains **150 JUnit Jupiter tests across 40 test classes**, covering board and piece state, both movement directions, all player strategies, captures/blockades, round and bonus rules, mystery effects, termination, the GUI protocol, virtual-thread clients, serial server execution, socket state publication, and isolated/concurrent H2 persistence.

```text
Tests run: 150, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Limitations and security notes

- Local `GuiGameLogger` updates the event log but not the local board's piece positions; network observers do render `PIECE` updates.
- Clients are observers only. Every client receives every broadcast, and there is no player assignment, authentication, authorization, or transport encryption.
- Fixed empty-password H2 credentials and hard-coded endpoints are appropriate only for local development.
- The server does not acknowledge invalid requests or provide late-joining clients with current state.
- No coverage tool, CI workflow, deployment configuration, or in-app server/database shutdown command is present.

---

## Contact Information

**Developer**: Dillon Fernandez  
**Email**: dillonfernandez@gmail.com  
**Institution**: APIIT

---

<div align="center">
  <p><strong>Disclaimer</strong></p>
  <p><em>This is an academic project developed for educational purposes and is not intended for commercial use.</em></p>
</div>
