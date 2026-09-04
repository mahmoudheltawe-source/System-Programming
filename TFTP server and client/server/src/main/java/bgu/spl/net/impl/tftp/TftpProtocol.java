package bgu.spl.net.impl.tftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private static final int DATA_PACKET_SIZE = 512;
    private static final File FILES_DIR = new File("Files");
    private static final ConcurrentHashMap<String, Integer> LOGGED_IN_USERS = new ConcurrentHashMap<>();
    private static final Set<String> UPLOADING_FILES =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private int connectionId;
    private Connections<byte[]> connections;
    private boolean isLoggedIn;
    private boolean shouldTerminate;
    private String username;

    private TransferMode outgoingTransfer;
    private ArrayDeque<byte[]> outgoingDataPackets;
    private short lastOutgoingBlock;
    private boolean lastOutgoingPacketWasFinal;

    private FileOutputStream uploadStream;
    private String uploadFileName;
    private short expectedUploadBlock;

    private enum TransferMode {
        NONE,
        RRQ,
        DIRQ
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.isLoggedIn = false;
        this.shouldTerminate = false;
        this.username = "";
        this.outgoingTransfer = TransferMode.NONE;
        this.outgoingDataPackets = new ArrayDeque<>();
        this.lastOutgoingBlock = 0;
        this.lastOutgoingPacketWasFinal = false;
        this.uploadStream = null;
        this.uploadFileName = "";
        this.expectedUploadBlock = 1;
        ensureFilesDirectory();
    }

    @Override
    public void process(byte[] message) {
        if (message == null || message.length < 2) {
            sendErrorPacket((short) 4, "Illegal TFTP operation");
            return;
        }

        short opcode = bytesToShort(message, 0);
        if (opcode < 1 || opcode > 10 || opcode == 9) {
            sendErrorPacket((short) 4, "Illegal TFTP operation");
            return;
        }

        if (!isLoggedIn && opcode != 7) {
            sendErrorPacket((short) 6, "User not logged in");
            return;
        }

        switch (opcode) {
            case 1:
                handleRRQ(message);
                break;
            case 2:
                handleWRQ(message);
                break;
            case 3:
                handleDATA(message);
                break;
            case 4:
                handleACK(message);
                break;
            case 5:
                break;
            case 6:
                handleDIRQ();
                break;
            case 7:
                handleLOGRQ(message);
                break;
            case 8:
                handleDELRQ(message);
                break;
            case 10:
                handleDISC();
                break;
            default:
                sendErrorPacket((short) 4, "Illegal TFTP operation");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void handleLOGRQ(byte[] message) {
        String requestedUsername = readZeroTerminatedString(message, 2);
        if (isLoggedIn || LOGGED_IN_USERS.putIfAbsent(requestedUsername, connectionId) != null) {
            sendErrorPacket((short) 7, "User already logged in");
            return;
        }

        username = requestedUsername;
        isLoggedIn = true;
        connections.send(connectionId, createAckPacket((short) 0));
    }

    private void handleRRQ(byte[] message) {
        String fileName = readZeroTerminatedString(message, 2);
        File requestedFile = new File(FILES_DIR, fileName);
        if (!requestedFile.isFile()) {
            sendErrorPacket((short) 1, "File not found");
            return;
        }

        try {
            startDataTransfer(Files.readAllBytes(requestedFile.toPath()), TransferMode.RRQ);
        } catch (IOException e) {
            sendErrorPacket((short) 2, "Access violation");
        }
    }

    private void handleWRQ(byte[] message) {
        String fileName = readZeroTerminatedString(message, 2);
        File targetFile = new File(FILES_DIR, fileName);
        if (targetFile.exists() || !UPLOADING_FILES.add(fileName)) {
            sendErrorPacket((short) 5, "File already exists");
            return;
        }

        try {
            ensureFilesDirectory();
            if (!targetFile.createNewFile()) {
                UPLOADING_FILES.remove(fileName);
                sendErrorPacket((short) 5, "File already exists");
                return;
            }
            uploadStream = new FileOutputStream(targetFile);
            uploadFileName = fileName;
            expectedUploadBlock = 1;
            connections.send(connectionId, createAckPacket((short) 0));
        } catch (IOException e) {
            closeUploadStream();
            UPLOADING_FILES.remove(fileName);
            sendErrorPacket((short) 2, "Access violation");
        }
    }

    private void handleDATA(byte[] message) {
        if (uploadStream == null || message.length < 6) {
            sendErrorPacket((short) 0, "No write request in progress");
            return;
        }

        short packetSize = bytesToShort(message, 2);
        short blockNumber = bytesToShort(message, 4);
        if (packetSize < 0 || message.length < 6 + packetSize) {
            sendErrorPacket((short) 4, "Illegal TFTP operation");
            return;
        }

        if (blockNumber != expectedUploadBlock) {
            sendErrorPacket((short) 0, "Unexpected DATA block");
            return;
        }

        try {
            uploadStream.write(message, 6, packetSize);
            connections.send(connectionId, createAckPacket(blockNumber));
            if (packetSize < DATA_PACKET_SIZE) {
                closeUploadStream();
                UPLOADING_FILES.remove(uploadFileName);
                broadCast((byte) 1, uploadFileName);
                uploadFileName = "";
            } else {
                expectedUploadBlock++;
            }
        } catch (IOException e) {
            closeUploadStream();
            UPLOADING_FILES.remove(uploadFileName);
            sendErrorPacket((short) 2, "Access violation");
        }
    }

    private void handleACK(byte[] message) {
        if (outgoingTransfer == TransferMode.NONE || message.length < 4) {
            return;
        }

        short ackBlock = bytesToShort(message, 2);
        if (ackBlock != lastOutgoingBlock) {
            return;
        }

        if (lastOutgoingPacketWasFinal) {
            outgoingTransfer = TransferMode.NONE;
            outgoingDataPackets.clear();
        } else {
            sendNextDataPacket();
        }
    }

    private void handleDIRQ() {
        File[] files = FILES_DIR.listFiles(file -> file.isFile() && !UPLOADING_FILES.contains(file.getName()));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        ByteArrayOutputStream listing = new ByteArrayOutputStream();
        for (int i = 0; i < files.length; i++) {
            if (i > 0) {
                listing.write(0);
            }
            byte[] nameBytes = files[i].getName().getBytes(StandardCharsets.UTF_8);
            listing.write(nameBytes, 0, nameBytes.length);
        }

        startDataTransfer(listing.toByteArray(), TransferMode.DIRQ);
    }

    private void handleDELRQ(byte[] message) {
        String fileName = readZeroTerminatedString(message, 2);
        File targetFile = new File(FILES_DIR, fileName);
        if (!targetFile.exists() || !targetFile.isFile()) {
            sendErrorPacket((short) 1, "File not found");
            return;
        }

        if (!targetFile.delete()) {
            sendErrorPacket((short) 2, "Access violation");
            return;
        }

        connections.send(connectionId, createAckPacket((short) 0));
        broadCast((byte) 0, fileName);
    }

    private void handleDISC() {
        if (isLoggedIn) {
            LOGGED_IN_USERS.remove(username, connectionId);
            isLoggedIn = false;
            username = "";
        }

        connections.send(connectionId, createAckPacket((short) 0));
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void startDataTransfer(byte[] data, TransferMode transferMode) {
        outgoingDataPackets.clear();
        int offset = 0;
        while (offset < data.length) {
            int packetSize = Math.min(DATA_PACKET_SIZE, data.length - offset);
            outgoingDataPackets.add(Arrays.copyOfRange(data, offset, offset + packetSize));
            offset += packetSize;
        }

        if (data.length == 0 || data.length % DATA_PACKET_SIZE == 0) {
            outgoingDataPackets.add(new byte[0]);
        }

        outgoingTransfer = transferMode;
        lastOutgoingBlock = 0;
        lastOutgoingPacketWasFinal = false;
        sendNextDataPacket();
    }

    private void sendNextDataPacket() {
        if (outgoingDataPackets.isEmpty()) {
            outgoingTransfer = TransferMode.NONE;
            return;
        }

        byte[] data = outgoingDataPackets.removeFirst();
        lastOutgoingBlock++;
        lastOutgoingPacketWasFinal = data.length < DATA_PACKET_SIZE;
        connections.send(connectionId, createDataPacket(lastOutgoingBlock, data));
    }

    private void broadCast(byte addOrDelete, String fileName) {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[4 + fileNameBytes.length];
        output[0] = 0;
        output[1] = 9;
        output[2] = addOrDelete;
        System.arraycopy(fileNameBytes, 0, output, 3, fileNameBytes.length);
        output[output.length - 1] = 0;

        for (Integer id : LOGGED_IN_USERS.values()) {
            connections.send(id, output);
        }
    }

    private void sendErrorPacket(short errorNumber, String errorMessage) {
        connections.send(connectionId, createErrorPacket(errorNumber, errorMessage));
    }

    private byte[] createAckPacket(short blockNumber) {
        byte[] output = new byte[4];
        output[0] = 0;
        output[1] = 4;
        writeShort(output, 2, blockNumber);
        return output;
    }

    private byte[] createDataPacket(short blockNumber, byte[] data) {
        byte[] output = new byte[6 + data.length];
        output[0] = 0;
        output[1] = 3;
        writeShort(output, 2, (short) data.length);
        writeShort(output, 4, blockNumber);
        System.arraycopy(data, 0, output, 6, data.length);
        return output;
    }

    private byte[] createErrorPacket(short errorCode, String errorMessage) {
        byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[5 + errorMessageBytes.length];
        output[0] = 0;
        output[1] = 5;
        writeShort(output, 2, errorCode);
        System.arraycopy(errorMessageBytes, 0, output, 4, errorMessageBytes.length);
        output[output.length - 1] = 0;
        return output;
    }

    private String readZeroTerminatedString(byte[] message, int startIndex) {
        int endIndex = message.length;
        if (endIndex > startIndex && message[endIndex - 1] == 0) {
            endIndex--;
        }
        return new String(message, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
    }

    private short bytesToShort(byte[] bytes, int index) {
        return (short) (((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff));
    }

    private void writeShort(byte[] bytes, int index, short value) {
        bytes[index] = (byte) ((value >> 8) & 0xff);
        bytes[index + 1] = (byte) (value & 0xff);
    }

    private void ensureFilesDirectory() {
        if (!FILES_DIR.exists()) {
            FILES_DIR.mkdirs();
        }
    }

    private void closeUploadStream() {
        if (uploadStream != null) {
            try {
                uploadStream.close();
            } catch (IOException ignored) {
            }
            uploadStream = null;
        }
    }
}
