<div align="center">
  <h1>LUDO-T</h1>
  <p><em>A Java 21 command-line simulation of LUDO with a twist, built as a modular, test-driven game engine.</em></p>

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36.svg)](https://maven.apache.org/)
[![JUnit](https://img.shields.io/badge/Tests-131%20passing-brightgreen.svg)](https://junit.org/junit5/)

</div>

---

## Overview

LUDO-T is a polished Java implementation of the classic LUDO board game, enhanced with special rule logic, mystery
effects, captures, blockades, and bonus turns. The project is organized around a clear object-oriented architecture that
separates board setup, player actions, random elements, and game rules.

This repository is designed to demonstrate:

- Clean separation of concerns in a game engine
- Rule-driven turn processing and win detection
- Testable simulation logic with JUnit 5
- A simple command-line entry point for running the game

---

## Key Features

- 4-player turn-based gameplay with color-based players
- Board construction and path movement management
- Capture, blockade, and movement-rule handling
- Mystery effects including teleport and twist mechanics
- Bonus roll logic and winning condition checks
- Modular factories for players, pieces, and random components
- A fully verified automated test suite

---

## Project Structure

- `src/main/java/com/ludot/engine/` – game loop, simulation entry point, and builder classes
- `src/main/java/com/ludot/board/` – board layout, home areas, and movement paths
- `src/main/java/com/ludot/rules/` – gameplay rules such as capture, movement, mystery, and win logic
- `src/main/java/com/ludot/player/` – player implementations and turn behavior
- `src/main/java/com/ludot/random/` – dice, coin toss, and mystery effect generation
- `src/test/java/` – JUnit tests covering rules, movement, players, and engine behavior

---

## Technology Stack

- Java 21
- Maven
- JUnit 5
- Command-line simulation runtime

---

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.9 or higher

### Run the tests

```bash
mvn test
```

### Build the project

```bash
mvn package
```

### Run the game

```bash
java -jar target/ludo-t-1.0.0.jar
```

---

## Verification

The current project has been verified with Maven:

- `mvn test` → 131 tests run, 0 failures, 0 errors, BUILD SUCCESS

---

## Academic / Project Note

This project serves as a Java-based game engineering example that combines simulation logic, object-oriented design, and
automated testing in one runnable application.

---

<div align="center">
  <p><strong>Disclaimer</strong></p>
  <p><em>This is an academic project developed for educational purposes and is not intended for commercial use.</em></p>
</div>
