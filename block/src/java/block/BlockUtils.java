package block;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Created by xuelin on 7/17/17.
 */
public class BlockUtils {


    public static String numberToHex(Number n) {
        return String.format("%02X", n.byteValue());
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return bytes;
    }

    public static byte hexToByte(String s) {
        byte[] bytes = hexToBytes(s);
        return bytes[0];
    }

    public static byte[] readBytes(String path, long offset, int length) throws IOException
    {
        Path p = FileSystems.getDefault().getPath(path);

        try (FileChannel fc = FileChannel.open(p, EnumSet.of(StandardOpenOption.READ, StandardOpenOption.SYNC))) {
            ByteBuffer buf = ByteBuffer.allocate(length);
            fc.position(offset);
            int numBytes = fc.read(buf);
            byte[] allBytes = buf.array();
            return Arrays.copyOfRange(allBytes, 0, numBytes);
        }
    }

    public static byte readByte(String path, long offset) throws IOException
    {
        byte[] bytes = readBytes(path, offset, 1);
        return bytes[0];
    }

    public static String readHex(String path, long offset, int length) throws IOException
    {
        byte[] bytes = readBytes(path, offset, length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));

        }
        return sb.toString();
    }

    public static int writeHex(String path, long offset, String hex) throws IOException
    {
        byte[] bytes = hexToBytes(hex);
        return writeBytes(path, offset, bytes);
    }

    public static int writeBytes(String path, long offset, byte[] bytes) throws IOException
    {
        Path p = FileSystems.getDefault().getPath(path);

        try (FileChannel fc = FileChannel.open(p, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.SYNC))) {
            fc.position(offset);
            int numBytes = fc.write(ByteBuffer.wrap(bytes));
            return numBytes;
        }
    }

    public static int writeByte(String path, long offset, byte b) throws IOException
    {
        return writeBytes(path, offset, new byte[]{b});
    }

    public static void main(String[] args)
            throws IOException
    {
        String path = "/dev/ram8192";
        long offset = 2048;
        int numBytes = BlockUtils.writeHex(path, offset, "A0B1C2D3E4F5");
        String str = BlockUtils.readHex(path, offset, numBytes);
        System.out.println("out: " + str);
    }
}
