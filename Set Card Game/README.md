# Set Card Game

This project is a Java implementation of the card game **Set**. It was built as a multi-threaded game with a Swing user interface, configurable players, keyboard controls, card images, logging, and unit tests.

## What the Code Does

The game starts from `bguspl.set.Main`. It loads configuration from `config.properties`, creates the game environment, opens the Swing UI when possible, creates the table, dealer, and players, then starts the dealer thread.

Main components:

- `src/main/java/bguspl/set/Main.java` - application entry point.
- `src/main/java/bguspl/set/Config.java` - loads game, UI, player, and keyboard settings.
- `src/main/java/bguspl/set/UtilImpl.java` - converts cards to features and checks valid sets.
- `src/main/java/bguspl/set/UserInterfaceSwing.java` - graphical game window.
- `src/main/java/bguspl/set/ex/Dealer.java` - manages the deck, timer, player claims, card replacement, and winner announcement.
- `src/main/java/bguspl/set/ex/Player.java` - handles human or computer player actions, scoring, penalties, and freeze timers.
- `src/main/java/bguspl/set/ex/Table.java` - stores cards on table slots and tracks player tokens.
- `src/main/resources/cards/` - card image assets used by the UI.
- `src/test/java/bguspl/set/ex/` - JUnit tests for the game logic.

## Requirements

- Java 8 or newer
- Maven
- A graphical desktop session for games with human players, because the UI uses Swing

## How to Build

From the project root:

```bash
mvn compile
```

## How to Run

Run the game with Maven:

```bash
mvn exec:java
```

You can also package the project and run the generated JAR:

```bash
mvn package
java -jar target/Set_Card_Game-1.0-SNAPSHOT.jar
```

The program creates log files in the `logs/` directory.

## How to Run Tests

```bash
mvn test
```

The tests use JUnit 5 and Mockito.

## Configuration

Default settings are in:

```text
src/main/resources/config.properties
```

At startup, the game first tries to read `config.properties` from the current working directory. If it is not found there, it loads the bundled resource from `src/main/resources`.

Useful settings:

- `HumanPlayers` - number of keyboard-controlled players.
- `ComputerPlayers` - number of AI players.
- `Rows` and `Columns` - table grid size.
- `Hints` - whether to print legal set hints to the console.
- `TurnTimeoutSeconds` - time before the dealer reshuffles.
- `PointFreezeSeconds` - freeze time after scoring.
- `PenaltyFreezeSeconds` - freeze time after an incorrect set.
- `PlayerNames` - names shown in the UI.
- `PlayerKeys1`, `PlayerKeys2`, etc. - keyboard scan codes for each human player.

For a headless AI-only run, set:

```properties
HumanPlayers=0
ComputerPlayers=1
```

## Default Controls

The keys map to the table slots row by row.

Player 1:

```text
Q W E R
A S D F
Z X C V
```

Player 2:

```text
U I O P
J K L ;
M , . /
```

Press a mapped key to place or remove a token on that slot. When a player has selected three cards, the dealer checks whether the selected cards form a legal set. A correct set awards a point; an incorrect set causes a penalty freeze.

## Troubleshooting

If the game cannot open a window, run it from a desktop terminal or an X11 session. For environments without a graphical display, configure only computer players by setting `HumanPlayers=0` and `ComputerPlayers` to a positive number.
