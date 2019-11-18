package com.example.android_tcp.util;

import java.io.IOException;
import java.io.InputStream;

public class NetTools {
    public static final String RELAY_COMMAND_NEW = "relay command new";
    public static final String RELAY_COMMAND_END = "relay command end";

    public static int readall(InputStream in, byte[] buffer, int off, int len) throws IOException {
        int size;
        size = in.read(buffer, off, len);
        if (size == -1) {
            return -1;
        } else if (size == len) {
            return len;
        }

        int done = size;
        int remain = len - size;
        while (len > done) {
            size = in.read(buffer, off + done, remain);
            if (size == -1) {
                // to end
                break;
            }
            done += size;
            remain -= size;
        }
        return done;
    }


    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    public static byte[] intToBytes(int l) {
        byte[] result = new byte[4];
        for (int i = 3; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static int bytesToInt(byte[] b) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
