# SPL Course Projects

This repository contains three Systems Programming Laboratory projects. Together they cover the main course themes: C++ object-oriented programming and memory management, Java concurrency and synchronization, and TCP client/server communication with binary protocol encoding.

Each project has its own folder, source code, build files, and detailed local README. The PDF files in the project folders describe the original project requirements and are used here only as reference material.

## Projects

### Food Warehouse Management System

Location: `Food Warehouse Management System/`

A C++11 command-line simulation of a food warehouse that supplies food packages to customers. The system loads an initial warehouse state from a configuration file, accepts interactive commands, creates customer orders, assigns collector and driver volunteers, advances the simulation by time steps, and tracks pending, in-process, and completed orders.

Main course concepts:

- Object-oriented C++ design with inheritance and polymorphism.
- Manual heap allocation and ownership.
- Rule of 5 support for classes that manage dynamic resources.
- Command objects for user actions and action logging.
- Deep-copy backup and restore of program state.

Quick start:

```bash
cd "Food Warehouse Management System"
make
bin/warehouse tests/input_file.txt
```

### Set Card Game

Location: `Set Card Game/`

A Java implementation of the card game Set. The game uses a Swing interface, configurable human and computer players, keyboard controls, card assets, logging, and unit tests. The dealer manages the round flow while player threads place tokens and submit set claims.

Main course concepts:

- Java threads and thread lifecycle management.
- Synchronization over shared game state.
- Bounded queues for player input.
- Fair processing of concurrent player claims.
- Liveness, graceful termination, and unit testing.

Quick start:

```bash
cd "Set Card Game"
mvn compile
mvn exec:java
```

Tests:

```bash
mvn test
```

### Extended TFTP Server and Client

Location: `TFTP server and client/`

A Java TCP implementation of an extended TFTP-style file transfer system. The project includes a server, a Java client, and a reference Rust client. Users can log in, list server files, download files, upload files, delete files, disconnect, and receive broadcasts when files are added or removed.

Main course concepts:

- TCP sockets and stream-based communication.
- Binary message encoding and decoding.
- Big-endian numeric fields and zero-terminated UTF-8 strings.
- Thread-per-client server design.
- Bidirectional protocol handling through a shared connections registry.
- Separation between generic server infrastructure and protocol-specific behavior.

Run the server:

```bash
cd "TFTP server and client/server"
mvn compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="7777"
```

Run the Java client in another terminal:

```bash
cd "TFTP server and client/client"
mvn compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7777"
```

Example client commands:

```text
LOGRQ Mahmoud
DIRQ
RRQ A.txt
WRQ kelev_yam.mp3
DELRQ kelev_yam.mp3
DISC
```

## Repository Structure

```text
Food Warehouse Management System/   C++ warehouse simulation
Set Card Game/                      Java concurrent Set game
TFTP server and client/             Java extended TFTP server and client
README.md                           Repository overview
```

## Course Themes Represented

The three projects move through progressively broader systems-programming topics:

- Runtime environments and program execution.
- C++ classes, resources, copying, moving, and memory safety.
- Java classes, JVM execution, threads, monitors, and synchronization.
- Producer-consumer style coordination and fair queues.
- Networked applications using clients, servers, sockets, and protocols.
- Encoder/decoder separation from protocol logic.

## Notes

- Source code for each project is already implemented.
- Each project folder contains a more detailed README with build, run, and usage instructions.
- Generated build outputs may appear in `bin/`, `target/`, and `logs/` depending on the project.
