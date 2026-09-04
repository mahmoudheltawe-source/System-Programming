# Extended TFTP Server And Client

This workspace contains an SPL Assignment 3 extended TFTP implementation and a reference Rust client.

The assignment documents and lecture notes were used only as protocol/course references. The code follows the assignment's TCP-based TFTP packet format and the course server pattern separation:

- `MessageEncoderDecoder<T>` handles encoding, framing, and decoding bytes.
- `BidiMessagingProtocol<T>` handles protocol state and behavior.
- `ConnectionHandler<T>`, `Connections<T>`, and `BaseServer<T>` handle the generic server infrastructure.

## Folder Overview

### `server/`

Java Maven project for the TFTP server.

Important files:

- `src/main/java/bgu/spl/net/impl/tftp/TftpServer.java`
  Starts the thread-per-client TFTP server.
- `src/main/java/bgu/spl/net/impl/tftp/TftpProtocol.java`
  Implements the assignment protocol behavior: `LOGRQ`, `RRQ`, `WRQ`, `DIRQ`, `DELRQ`, `DISC`, `DATA`, `ACK`, `ERROR`, and `BCAST`.
- `src/main/java/bgu/spl/net/impl/tftp/TftpEncoderDecoder.java`
  Decodes and encodes binary TFTP packets using the assignment framing rules.
- `src/main/java/bgu/spl/net/srv/`
  Generic server infrastructure.
- `Files/`
  The server-side file storage directory.

What was fixed/improved:

- The server infrastructure was made generic again by using `BidiMessagingProtocol<T>` instead of hard-coding `TftpProtocol`.
- `RRQ` and `DIRQ` now send real `DATA` packets and wait for client `ACK`s before continuing.
- `WRQ` now accepts uploads with `ACK 0`, writes binary file data, acknowledges each block, and broadcasts only after upload completes.
- `DIRQ` uses real zero-byte separators between file names.
- `LOGRQ`, duplicate login, not-logged-in access, file-not-found, file-exists, delete, and disconnect behavior were aligned with the assignment.
- Debug prints that were not part of the required output were removed from the TFTP path.

### `client/`

Java Maven project for the TFTP client.

Important files:

- `src/main/java/bgu/spl/net/impl/tftp/TftpClient.java`
  Main Java client. It has a keyboard side and a listening side, sends raw binary packets, and manages file transfer state.
- `src/main/java/bgu/spl/net/impl/tftp/TftpEncoderDecoder.java`
  Client-side binary packet decoder/encoder.

What was fixed/improved:

- The old client used `BufferedReader` and `BufferedWriter`, which is wrong for a binary protocol and corrupts TFTP packets.
- The client now uses raw `InputStream` and `OutputStream`.
- `RRQ` downloads files into the current client working directory.
- `WRQ` uploads files from the current client working directory.
- The client sends `ACK` packets for received `DATA` packets.
- The client prints assignment-required output such as `ACK <block>`, `BCAST add/del <file>`, `Error <code> <message>`, `RRQ <file> complete`, and `WRQ <file> complete`.
- The client refuses local invalid operations before contacting the server:
  - `RRQ <file>` prints `file already exists` if the file exists locally.
  - `WRQ <file>` prints `file does not exists` if the file does not exist locally.
- The unused broken TFTP helper classes were removed from the Java client package.

### `TFTP-rust-client-master/`

Reference Rust client supplied for testing the server.

This folder was not part of the Java implementation changes. It can still be used to test the Java server.

Important files:

- `src/main.rs`
  Rust client entry point.
- `src/encdec.rs`
  Rust packet encoder/decoder.
- `src/connection_handler.rs`
  Rust TCP connection wrapper.
- `target/release/Tftpclinet`
  Existing compiled Rust binary in this workspace.

Note: the binary name is spelled `Tftpclinet` in this folder.

## How To Run

Use two terminals: one for the server and one for the client.

### 1. Run The Java Server

From the `server` folder:

```bash
cd "/home/mahmoud/Documents/SPL/TFTP server and client/server"
mvn clean compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="7777"
```

Expected server output:

```text
Server started
```

Keep this terminal open while testing clients.

If port `7777` is busy, use another port:

```bash
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="7778"
```

### 2. Run The Java Client

From the `client` folder:

```bash
cd "/home/mahmoud/Documents/SPL/TFTP server and client/client"
mvn clean compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7777"
```

If the server is on port `7778`, run:

```bash
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7778"
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

Important working-directory rule:

- Server files are stored in `server/Files`.
- Java client `RRQ` downloads into the directory where the client command is running.
- Java client `WRQ` uploads from the directory where the client command is running.

### 3. Run The Rust Reference Client

Start the Java server first.

Then run the existing Rust binary:

```bash
cd "/home/mahmoud/Documents/SPL/TFTP server and client/TFTP-rust-client-master"
./target/release/Tftpclinet 127.0.0.1 7777
```

If the server is on port `7778`, run:

```bash
./target/release/Tftpclinet 127.0.0.1 7778
```

If Rust and Cargo are installed and you want to rebuild the Rust client:

```bash
cd "/home/mahmoud/Documents/SPL/TFTP server and client/TFTP-rust-client-master"
cargo build --release
./target/release/Tftpclinet 127.0.0.1 7777
```

## Expected Client Output Examples

### Login, Directory Listing, Download, Disconnect

Input:

```text
LOGRQ Mahmoud
DIRQ
RRQ A.txt
DISC
```

Output with the current server files:

```text
ACK 0
A.txt
this is a file with spaces.txt
RRQ A.txt complete
ACK 0
```

### Upload

Input:

```text
LOGRQ Mahmoud
WRQ kelev_yam.mp3
DISC
```

Output shape:

```text
ACK 0
ACK 0
ACK 1
ACK 2
...
WRQ kelev_yam.mp3 complete
BCAST add kelev_yam.mp3
ACK 0
```

The final ACK number depends on the file size. Each `DATA` block carries up to 512 bytes.

### Delete

Input:

```text
LOGRQ Mahmoud
DELRQ some-file.txt
DISC
```

Output after a successful delete:

```text
ACK 0
ACK 0
BCAST del some-file.txt
ACK 0
```

### Common Error Cases

Before login:

```text
DIRQ
```

Expected:

```text
Error 6 User not logged in
```

RRQ when the file already exists locally:

```text
file already exists
```

WRQ when the file does not exist locally:

```text
file does not exists
```

WRQ when the file already exists on the server:

```text
Error 5 File already exists
```

RRQ or DELRQ when the file does not exist on the server:

```text
Error 1 File not found
```

## Assignment Submission Shape

For assignment submission, the important folders are:

```text
client/
server/
```

The assignment expects the Java client and Java server Maven projects. The Rust folder is useful for testing but is not part of the Java assignment implementation.

