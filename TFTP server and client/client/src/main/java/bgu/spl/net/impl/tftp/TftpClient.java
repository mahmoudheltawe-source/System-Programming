package bgu.spl.net.impl.tftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class TftpClient {
    private static final int DATA_PACKET_SIZE = 512;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final TftpEncoderDecoder encoderDecoder;
    private final Object stateLock = new Object();
    private final Object sendLock = new Object();

    private volatile boolean terminate;
    private boolean loggedIn;
    private ActiveCommand activeCommand;
    private String activeFileName;

    private File rrqFile;
    private FileOutputStream rrqOutput;
    private ByteArrayOutputStream dirqOutput;

    private FileInputStream wrqInput;
    private short nextWrqBlock;
    private short lastWrqBlock;
    private boolean lastWrqPacketWasFinal;

    private enum ActiveCommand {
        NONE,
        LOGRQ,
        DELRQ,
        RRQ,
        WRQ,
        DIRQ,
        DISC
    }

    public TftpClient(String serverHost, int serverPort) throws IOException {
        socket = new Socket(serverHost, serverPort);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        encoderDecoder = new TftpEncoderDecoder();
        terminate = false;
        loggedIn = false;
        activeCommand = ActiveCommand.NONE;
        activeFileName = "";
        nextWrqBlock = 1;
        lastWrqBlock = 0;
        lastWrqPacketWasFinal = false;
    }

    public void start() {
        Thread listeningThread = new Thread(this::listenToServer);
        listeningThread.start();

        readKeyboard();
        requestTermination();
        closeSocket();

        try {
            listeningThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java TftpClient <serverHost> <serverPort>");
            System.exit(1);
        }

        try {
            TftpClient client = new TftpClient(args[0], Integer.parseInt(args[1]));
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readKeyboard() {
        Scanner scanner = new Scanner(System.in);
        while (!shouldTerminate()) {
            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            handleKeyboardCommand(line);
        }
    }

    private void handleKeyboardCommand(String line) {
        int separatorIndex = line.indexOf(' ');
        String command = separatorIndex == -1 ? line : line.substring(0, separatorIndex);
        String argument = separatorIndex == -1 ? "" : line.substring(separatorIndex + 1).trim();

        try {
            switch (command) {
                case "LOGRQ":
                    handleLOGRQ(argument);
                    break;
                case "DELRQ":
                    handleDELRQ(argument);
                    break;
                case "RRQ":
                    handleRRQ(argument);
                    break;
                case "WRQ":
                    handleWRQ(argument);
                    break;
                case "DIRQ":
                    handleDIRQ(argument);
                    break;
                case "DISC":
                    handleDISC(argument);
                    break;
                default:
                    System.out.println("Invalid command");
            }
        } catch (IOException e) {
            requestTermination();
            closeSocket();
        }
    }

    private void handleLOGRQ(String username) throws IOException {
        if (username.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        beginCommand(ActiveCommand.LOGRQ, username);
        sendPacket(createStringPacket((short) 7, username));
        waitForActiveCommand();
    }

    private void handleDELRQ(String fileName) throws IOException {
        if (fileName.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        beginCommand(ActiveCommand.DELRQ, fileName);
        sendPacket(createStringPacket((short) 8, fileName));
        waitForActiveCommand();
    }

    private void handleRRQ(String fileName) throws IOException {
        if (fileName.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        File targetFile = new File(fileName);
        if (targetFile.exists()) {
            System.out.println("file already exists");
            return;
        }

        FileOutputStream output = new FileOutputStream(targetFile);
        synchronized (stateLock) {
            activeCommand = ActiveCommand.RRQ;
            activeFileName = fileName;
            rrqFile = targetFile;
            rrqOutput = output;
        }

        sendPacket(createStringPacket((short) 1, fileName));
        waitForActiveCommand();
    }

    private void handleWRQ(String fileName) throws IOException {
        if (fileName.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        File sourceFile = new File(fileName);
        if (!sourceFile.isFile()) {
            System.out.println("file does not exists");
            return;
        }

        FileInputStream input = new FileInputStream(sourceFile);
        synchronized (stateLock) {
            activeCommand = ActiveCommand.WRQ;
            activeFileName = fileName;
            wrqInput = input;
            nextWrqBlock = 1;
            lastWrqBlock = 0;
            lastWrqPacketWasFinal = false;
        }

        sendPacket(createStringPacket((short) 2, fileName));
        waitForActiveCommand();
    }

    private void handleDIRQ(String argument) throws IOException {
        if (!argument.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        synchronized (stateLock) {
            activeCommand = ActiveCommand.DIRQ;
            activeFileName = "";
            dirqOutput = new ByteArrayOutputStream();
        }

        sendPacket(createOpcodeOnlyPacket((short) 6));
        waitForActiveCommand();
    }

    private void handleDISC(String argument) throws IOException {
        if (!argument.isEmpty()) {
            System.out.println("Invalid command");
            return;
        }

        synchronized (stateLock) {
            if (!loggedIn) {
                terminate = true;
                stateLock.notifyAll();
                return;
            }
            activeCommand = ActiveCommand.DISC;
            activeFileName = "";
        }

        sendPacket(createOpcodeOnlyPacket((short) 10));
        waitForActiveCommand();
    }

    private void listenToServer() {
        try {
            int read;
            while (!shouldTerminate() && (read = in.read()) >= 0) {
                byte[] message = encoderDecoder.decodeNextByte((byte) read);
                if (message != null) {
                    processServerMessage(message);
                }
            }
        } catch (IOException ignored) {
        } finally {
            requestTermination();
            closeFiles();
        }
    }

    private void processServerMessage(byte[] message) throws IOException {
        if (message.length < 2) {
            return;
        }

        short opcode = bytesToShort(message, 0);
        switch (opcode) {
            case 3:
                handleDataPacket(message);
                break;
            case 4:
                handleAckPacket(message);
                break;
            case 5:
                handleErrorPacket(message);
                break;
            case 9:
                handleBcastPacket(message);
                break;
            default:
                break;
        }
    }

    private void handleAckPacket(byte[] message) throws IOException {
        if (message.length < 4) {
            return;
        }

        short blockNumber = bytesToShort(message, 2);
        System.out.println("ACK " + blockNumber);

        ActiveCommand command;
        synchronized (stateLock) {
            command = activeCommand;
        }

        if (command == ActiveCommand.LOGRQ && blockNumber == 0) {
            synchronized (stateLock) {
                loggedIn = true;
                finishCommandLocked();
            }
        } else if (command == ActiveCommand.DELRQ && blockNumber == 0) {
            finishCommand();
        } else if (command == ActiveCommand.DISC && blockNumber == 0) {
            synchronized (stateLock) {
                loggedIn = false;
                terminate = true;
                finishCommandLocked();
            }
        } else if (command == ActiveCommand.WRQ) {
            handleWrqAck(blockNumber);
        }
    }

    private void handleDataPacket(byte[] message) throws IOException {
        if (message.length < 6) {
            return;
        }

        short packetSize = bytesToShort(message, 2);
        short blockNumber = bytesToShort(message, 4);
        byte[] data = Arrays.copyOfRange(message, 6, 6 + packetSize);

        ActiveCommand command;
        synchronized (stateLock) {
            command = activeCommand;
        }

        if (command == ActiveCommand.RRQ) {
            rrqOutput.write(data);
            sendPacket(createAckPacket(blockNumber));
            if (packetSize < DATA_PACKET_SIZE) {
                closeRrqOutput();
                System.out.println("RRQ " + activeFileName + " complete");
                finishCommand();
            }
        } else if (command == ActiveCommand.DIRQ) {
            dirqOutput.write(data);
            sendPacket(createAckPacket(blockNumber));
            if (packetSize < DATA_PACKET_SIZE) {
                printDirectoryListing();
                finishCommand();
            }
        }
    }

    private void handleWrqAck(short blockNumber) throws IOException {
        boolean sendNextBlock = false;
        boolean completeTransfer = false;
        String completedFileName = "";

        synchronized (stateLock) {
            if (activeCommand != ActiveCommand.WRQ) {
                return;
            }

            if (lastWrqBlock == 0 && blockNumber == 0) {
                sendNextBlock = true;
            } else if (blockNumber == lastWrqBlock) {
                if (lastWrqPacketWasFinal) {
                    completeTransfer = true;
                    completedFileName = activeFileName;
                    closeWrqInput();
                    finishCommandLocked();
                } else {
                    sendNextBlock = true;
                }
            }
        }

        if (completeTransfer) {
            System.out.println("WRQ " + completedFileName + " complete");
        } else if (sendNextBlock) {
            sendNextWrqDataPacket();
        }
    }

    private void sendNextWrqDataPacket() throws IOException {
        byte[] packet;
        synchronized (stateLock) {
            if (activeCommand != ActiveCommand.WRQ || wrqInput == null) {
                return;
            }

            byte[] buffer = new byte[DATA_PACKET_SIZE];
            int read = wrqInput.read(buffer);
            if (read == -1) {
                read = 0;
            }

            byte[] data = Arrays.copyOf(buffer, read);
            short blockNumber = nextWrqBlock;
            nextWrqBlock++;
            lastWrqBlock = blockNumber;
            lastWrqPacketWasFinal = read < DATA_PACKET_SIZE;
            packet = createDataPacket(blockNumber, data);
        }

        sendPacket(packet);
    }

    private void handleBcastPacket(byte[] message) {
        if (message.length < 4) {
            return;
        }

        String action = message[2] == 0 ? "del" : "add";
        String fileName = readZeroTerminatedString(message, 3);
        System.out.println("BCAST " + action + " " + fileName);
    }

    private void handleErrorPacket(byte[] message) {
        short errorCode = message.length >= 4 ? bytesToShort(message, 2) : 0;
        String errorMessage = message.length > 4 ? readZeroTerminatedString(message, 4) : "";
        if (errorMessage.isEmpty()) {
            System.out.println("Error " + errorCode);
        } else {
            System.out.println("Error " + errorCode + " " + errorMessage);
        }

        synchronized (stateLock) {
            if (activeCommand == ActiveCommand.RRQ) {
                closeRrqOutput();
                if (rrqFile != null) {
                    rrqFile.delete();
                    rrqFile = null;
                }
            } else if (activeCommand == ActiveCommand.WRQ) {
                closeWrqInput();
            } else if (activeCommand == ActiveCommand.DISC) {
                terminate = true;
            }
            finishCommandLocked();
        }
    }

    private void printDirectoryListing() {
        byte[] data = dirqOutput.toByteArray();
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == 0) {
                if (i > start) {
                    System.out.println(new String(data, start, i - start, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        }
        dirqOutput = null;
    }

    private void beginCommand(ActiveCommand command, String fileName) {
        synchronized (stateLock) {
            activeCommand = command;
            activeFileName = fileName;
        }
    }

    private void waitForActiveCommand() {
        synchronized (stateLock) {
            while (activeCommand != ActiveCommand.NONE && !terminate) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    terminate = true;
                    break;
                }
            }
        }
    }

    private void finishCommand() {
        synchronized (stateLock) {
            finishCommandLocked();
        }
    }

    private void finishCommandLocked() {
        activeCommand = ActiveCommand.NONE;
        activeFileName = "";
        stateLock.notifyAll();
    }

    private boolean shouldTerminate() {
        return terminate;
    }

    private void requestTermination() {
        synchronized (stateLock) {
            terminate = true;
            stateLock.notifyAll();
        }
    }

    private void sendPacket(byte[] packet) throws IOException {
        synchronized (sendLock) {
            out.write(packet);
            out.flush();
        }
    }

    private byte[] createStringPacket(short opcode, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[3 + valueBytes.length];
        writeShort(packet, 0, opcode);
        System.arraycopy(valueBytes, 0, packet, 2, valueBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] createOpcodeOnlyPacket(short opcode) {
        byte[] packet = new byte[2];
        writeShort(packet, 0, opcode);
        return packet;
    }

    private byte[] createAckPacket(short blockNumber) {
        byte[] packet = new byte[4];
        writeShort(packet, 0, (short) 4);
        writeShort(packet, 2, blockNumber);
        return packet;
    }

    private byte[] createDataPacket(short blockNumber, byte[] data) {
        byte[] packet = new byte[6 + data.length];
        writeShort(packet, 0, (short) 3);
        writeShort(packet, 2, (short) data.length);
        writeShort(packet, 4, blockNumber);
        System.arraycopy(data, 0, packet, 6, data.length);
        return packet;
    }

    private short bytesToShort(byte[] bytes, int index) {
        return (short) (((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff));
    }

    private void writeShort(byte[] bytes, int index, short value) {
        bytes[index] = (byte) ((value >> 8) & 0xff);
        bytes[index + 1] = (byte) (value & 0xff);
    }

    private String readZeroTerminatedString(byte[] message, int startIndex) {
        int endIndex = message.length;
        if (endIndex > startIndex && message[endIndex - 1] == 0) {
            endIndex--;
        }
        return new String(message, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
    }

    private void closeFiles() {
        closeRrqOutput();
        closeWrqInput();
    }

    private void closeRrqOutput() {
        if (rrqOutput != null) {
            try {
                rrqOutput.close();
            } catch (IOException ignored) {
            }
            rrqOutput = null;
        }
    }

    private void closeWrqInput() {
        if (wrqInput != null) {
            try {
                wrqInput.close();
            } catch (IOException ignored) {
            }
            wrqInput = null;
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
