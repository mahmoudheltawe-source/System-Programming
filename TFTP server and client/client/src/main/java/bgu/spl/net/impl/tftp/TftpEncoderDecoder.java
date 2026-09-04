package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private byte[] bytes = new byte[1 << 10];
    private int len = 0;
    private short opcode = -1;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        pushByte(nextByte);
        switch (opcode) {
            case 1:
            case 2:
            case 7:
            case 8:
                if (nextByte == 0) {
                    return popBytes();
                }
                break;
            case 5:
                if (len > 4 && nextByte == 0) {
                    return popBytes();
                }
                break;
            case 9:
                if (len > 3 && nextByte == 0) {
                    return popBytes();
                }
                break;
            case 3:
                if (len >= 6) {
                    short size = bytesToShort(bytes, 2);
                    if (len == size + 6) {
                        return popBytes();
                    }
                }
                break;
            case 4:
                if (len == 4) {
                    return popBytes();
                }
                break;
            case 6:
            case 10:
                if (len == 2) {
                    return popBytes();
                }
                break;
            default:
                if (len == 2 && opcode != -1) {
                    return popBytes();
                }
                break;
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            byte[] nextBytes = new byte[bytes.length * 2];
            System.arraycopy(bytes, 0, nextBytes, 0, bytes.length);
            bytes = nextBytes;
        }

        bytes[len] = nextByte;
        len++;
        if (len == 2 && opcode == -1) {
            opcode = bytesToShort(bytes, 0);
        }
    }

    private short bytesToShort(byte[] bytes, int index) {
        return (short) (((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff));
    }

    private byte[] popBytes() {
        byte[] result = new byte[len];
        System.arraycopy(bytes, 0, result, 0, len);
        len = 0;
        bytes = new byte[1 << 10];
        opcode = -1;
        return result;
    }
}
