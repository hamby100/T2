package rs.manufaktura.tiger.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.ys.serialport.SerialPort;
// Using the logger you provided
import com.bosphere.filelogger.FL;


/**
 * Controller for the OS-HHLED-A RGB LED light system.
 * This class manages the serial port connection and handles the communication protocol
 * for controlling the LED lights. It is implemented as a singleton to ensure a single
 * point of control for the hardware.
 */
public class OsHhledAController {

    private static final String TAG = "OsHhledAController serial";

    // --- Singleton Instance ---
    private static OsHhledAController sInstance;

    // --- Connection and Stream Properties ---
    private static SerialPort serialPort;

    // --- Protocol Constants ---
    // Header byte for every command packet.
    private static final byte HEADER                = (byte) 0xCA;
    // Baud rate for serial communication.
    public static final int BAUD_RATE               = 115200;
    // Read/Write Status
    private static final byte WRITE_STATUS          = 0x00;
    private static final byte READ_STATUS           = 0x01;
    // Command Instructions
    public static final byte CMD_POWER              = 0x01;
    public static final byte CMD_AUTO_MODE          = 0x02;
    public static final byte CMD_PAUSE              = 0x03;
    public static final byte CMD_MODE               = 0x04;
    public static final byte CMD_DIRECTION          = 0x05;
    public static final byte CMD_SPEED              = 0x06;
    public static final byte CMD_RGB_ORDER          = 0x07;
    public static final byte CMD_BRIGHTNESS         = 0x08;
    public static final byte CMD_LED_COUNT          = 0x09;
    public static final byte CMD_FW_VERSION         = 0x0A;
    // Parameter Values
    private static final byte VALUE_OFF             = 0x00;
    private static final byte VALUE_ON              = 0x01;
    private static final byte VALUE_AUTO_OFF        = 0x00;
    private static final byte VALUE_AUTO_ON         = 0x01;
    private static final byte VALUE_PAUSE_OFF       = 0x00;
    private static final byte VALUE_PAUSE_ON        = 0x01;
    private static final byte VALUE_DIR_FORWARD     = 0x00;
    private static final byte VALUE_DIR_REVERSE     = 0x01;
    // Color Values 00-0D
    private static final byte COLOR_RED             = 0x00;
    private static final byte COLOR_GREEN           = 0x01;
    private static final byte COLOR_BLUE            = 0x02;
    private static final byte COLOR_YELLOW          = 0x03;
    private static final byte COLOR_MAGENTA         = 0x04;
    private static final byte COLOR_CYAN            = 0x05;
    private static final byte COLOR_LIGHT_GRAY      = 0x06;
    private static final byte COLOR_BRIGHT_CYAN     = 0x07;
    private static final byte COLOR_BRIGHT_GREEN    = 0x08;
    private static final byte COLOR_BRIGHT_PURPLE   = 0x09;
    private static final byte COLOR_BRIGHT_BLUE     = 0x0A;
    private static final byte COLOR_BRIGHT_YELLOW   = 0x0B;
    private static final byte COLOR_BRIGHT_ORANGE   = 0x0C;
    private static final byte COLOR_BRIGHT_RED      = 0x0D;
    private static final byte COLOR_BLACK           = 0x0F;


    /**
     * Private constructor to enforce the singleton pattern.
     * @param port The opened SerialPort object.
     */
    private OsHhledAController(SerialPort port) {
        serialPort = port;
    }

    //  ---- 2

    /**
     * Initializes the connection to the serial port and creates the singleton instance.
     * This method should be called once to set up the controller.
     *
     * @param devicePath The path to the serial device, e.g., "/dev/ttyS1".
     * @param baudRate
     * @return The singleton instance of the controller, or null if connection fails.
     */
    public static synchronized OsHhledAController connect(String devicePath, int baudRate) {
        // defined BAUD_RATE is required, but let the configuration drive this
        if (sInstance == null) {
            try {
                SerialPort port = new SerialPort(devicePath, baudRate);
                sInstance = new OsHhledAController(port);
                FL.d(TAG, "Successfully connected to serial port " + devicePath + " baud rate: " + baudRate);
            } catch (SecurityException e) {
                FL.e(TAG, "Error connecting to serial port " + devicePath + " baud rate: " + baudRate, e);
                sInstance = null; // Ensure instance is null on failure
            }
        }
        return sInstance;
    }

    /**
     * Gets the singleton instance of the controller.
     * @return The initialized controller instance, or null if not connected.
     */
    public static OsHhledAController getInstance() {
        if (sInstance == null) {
            FL.w(TAG, "getInstance() called before connect(). Please call connect() first.");
        }
        return sInstance;
    }

    /**
     * Closes the serial port connection and releases the singleton instance.
     */
    public static void disconnect() {
        if (sInstance != null) {
            try {
                // Attempt to turn lights off before disconnecting for a clean shutdown.
                turnOff();
            } catch (IOException e) {
                FL.e(TAG, "Error sending turn off command during disconnect.", e);
            }
            if (serialPort != null) {
                serialPort.release();
                serialPort = null;
            }
            sInstance = null;
            FL.d(TAG, "Serial port disconnected and instance released.");
        }
    }

    public static void setColor(String use_color, String use_luminance) throws IOException {
        byte color=getClosestColor(use_color);
        if (color == COLOR_BLACK || use_luminance.equals("off")) {
            // simply switch off
            FL.v(TAG, "setColor turnOff");
            turnOff();
            return;
        }
        // make sure it is turned on
        FL.v(TAG, "setColor turnOn");
        turnOn();
        try {
            Thread.sleep(100);
        } catch( InterruptedException e) {
            FL.v(TAG, "sleep got InterruptedException %s", e.getMessage());
        }
        FL.v(TAG, "setColor " + String.format("0x%02X", color & 0xFF) + " ("+use_color+")");
        setMode(color);
        try {
            Thread.sleep(100);
        } catch( InterruptedException e) {
            FL.v(TAG, "sleep got InterruptedException %s", e.getMessage());
        }
        FL.v(TAG, "setColor setBrightness: "+ use_luminance);
        setBrightness(Integer.valueOf(use_luminance));
        try {
            Thread.sleep(100);
        } catch( InterruptedException e) {
            FL.v(TAG, "sleep got InterruptedException %s", e.getMessage());
        }
    }

    // --- Public Control Methods ---

    public static void turnOn() throws IOException {
        sendWriteCommand(CMD_POWER, new byte[]{VALUE_ON});
    }

    public static void turnOff() throws IOException {
        sendWriteCommand(CMD_POWER, new byte[]{VALUE_OFF});
    }

    public static void setMode(int mode) throws IOException {
        if (mode < 0 || mode > 219) {
            throw new IllegalArgumentException("Mode must be between 0 and 219.");
        }
        sendWriteCommand(CMD_MODE, new byte[]{(byte) mode});
    }

    // --- 3

    public void setSpeed(int speed) throws IOException {
        if (speed < 0 || speed > 255) {
            throw new IllegalArgumentException("Speed must be between 0 and 255.");
        }
        sendWriteCommand(CMD_SPEED, new byte[]{(byte) speed});
    }

    public static void setBrightness(int brightness) throws IOException {
        if (brightness < 0 || brightness > 100) {
            throw new IllegalArgumentException("Brightness must be between 0 and 100.");
        }
        sendWriteCommand(CMD_BRIGHTNESS, new byte[]{(byte) brightness});
    }

    public void setAutoMode(boolean enable) throws IOException {
        byte value = enable ? VALUE_AUTO_ON : VALUE_AUTO_OFF;
        sendWriteCommand(CMD_AUTO_MODE, new byte[]{value});
    }

    public void setPaused(boolean pause) throws IOException {
        byte value = pause ? VALUE_PAUSE_ON : VALUE_PAUSE_OFF;
        sendWriteCommand(CMD_PAUSE, new byte[]{value});
    }

    public void setDirection(boolean forward) throws IOException {
        byte value = forward ? VALUE_DIR_FORWARD : VALUE_DIR_REVERSE;
        sendWriteCommand(CMD_DIRECTION, new byte[]{value});
    }

    public void setLedCount(int count) throws IOException {
        if (count < 0 || count > 2048) {
            throw new IllegalArgumentException("LED count must be between 0 and 2048.");
        }
        byte lsb = (byte) (count & 0xFF);
        byte msb = (byte) ((count >> 😍 & 0xFF);
        sendWriteCommand(CMD_LED_COUNT, new byte[]{lsb, msb});
    }

    public byte[] getFirmwareVersion() throws IOException {
        return sendReadCommand(CMD_FW_VERSION);
    }

    // --- Private Helper Methods ---

    private static void sendWriteCommand(byte command, byte[] params) throws IOException {
        int paramLength = (params != null) ? params.length : 0;
        byte packetLength = (byte) (5 + paramLength);
        byte[] packet = new byte[packetLength];

        packet[0] = HEADER;
        packet[1] = packetLength;
        packet[2] = WRITE_STATUS;
        packet[3] = command;

        if (params != null) {
            System.arraycopy(params, 0, packet, 4, paramLength);
        }

        packet[packetLength - 1] = calculateChecksum(packet, packetLength - 1);

        if (!serialPort.write(packet)) {
            throw new IOException("Failed to sendWrite command to serial port: "+ bytesToHexString(packet));
        } else {
            FL.v(TAG, "sendWrite command: " + bytesToHexString(packet));
        }

    }

    /**
     * Executes a command that expects a response, handling both sending the request
     * and reading/validating the response packet. This method is synchronized to
     * prevent communication issues from multiple threads.
     *
     * @param command The command byte to send.
     * @return The full, validated response packet.
     * @throws IOException if there is a communication error or the response is invalid.
     */
    private synchronized byte[] sendReadCommand(byte command) throws IOException {
        // 1. Send the read request packet
        byte requestPacketLength = 5;
        byte[] requestPacket = new byte[requestPacketLength];
        requestPacket[0] = HEADER;
        requestPacket[1] = requestPacketLength;
        requestPacket[2] = READ_STATUS;
        requestPacket[3] = command;
        requestPacket[4] = calculateChecksum(requestPacket, requestPacketLength - 1);
        if (!serialPort.write(requestPacket)) {
            throw new IOException("Failed to sendRead command to serial port: "+ bytesToHexString(requestPacket));
        } else {
            FL.v(TAG, "sendRead command: " + bytesToHexString(requestPacket));
        }

        // 2. Read the response packet
        long startTime = System.currentTimeMillis();
        long timeoutMillis = 500; // 500ms timeout for the read operation
        byte[] buffer = new byte[7]; // Expected response packet length is 7 bytes
        int bytesRead = 0;


        // --- 4

        // Loop until we have read all expected bytes or a timeout occurs
        while (bytesRead < buffer.length && System.currentTimeMillis() - startTime < timeoutMillis) {
            byte[] receivedData = serialPort.read();
            if (receivedData != null && receivedData.length > 0) {
                // Copy the newly received bytes into our main buffer
                int bytesToCopy = Math.min(receivedData.length, buffer.length - bytesRead);
                System.arraycopy(receivedData, 0, buffer, bytesRead, bytesToCopy);
                bytesRead += bytesToCopy;
            }

            // Add a small delay to prevent the loop from consuming 100% CPU
            if (bytesRead < buffer.length) {
                try {
                    Thread.sleep(20); // Poll every 20ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    FL.e(TAG, "Read thread interrupted", e);
                    return null;
                }
            }
        }

        if (bytesRead < buffer.length) {
            FL.w(TAG, "sendReadCommand timed out. Expected 7 bytes, but only received " + bytesRead);
            return null; // Timeout occurred before receiving the full packet
        }

        // --- Optional but recommended: Validate the received packet ---
        // 1. Check header
        if (buffer[0] != HEADER) {
            FL.e(TAG, "sendReadCommand: Invalid response header.");
            return null;
        }
        // 2. Check reported length
        if (buffer[1] != buffer.length) {
            FL.e(TAG, "sendReadCommand: Packet length mismatch.");
            return null;
        }
        // 3. Verify checksum
        byte expectedChecksum = calculateChecksum(buffer, buffer.length - 1);
        byte actualChecksum = buffer[buffer.length - 1];
        if (expectedChecksum != actualChecksum) {
            FL.e(TAG, "sendReadCommand: Checksum mismatch. Expected=" + expectedChecksum + ", Actual=" + actualChecksum);
            return null;
        }

        FL.v(TAG, "sendReadCommand result buffer: "+ bytesToHexString(buffer));
        return buffer;
    }

    private static byte calculateChecksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += data[i] & 0xFF; // Treat byte as unsigned
        }
        return (byte) (sum & 0xFF);
    }

    /**
     * Converts a byte array to a readable hex string with spaces.
     * @param bytes The byte array to convert.
     * @return A string representation of the byte array in hex.
     */
    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString().trim();
    }

    private static byte getClosestColor(String rrggbb) {
        int r1 = Integer.parseInt(rrggbb.substring(0, 2), 16);
        int g1 = Integer.parseInt(rrggbb.substring(2, 4), 16);
        int b1 = Integer.parseInt(rrggbb.substring(4, 6), 16);

        int[][] palette = {
                {0x80, 0x00, 0x00}, // RED
                {0x00, 0x80, 0x00}, // GREEN
                {0x00, 0x00, 0x80}, // BLUE
                {0x80, 0x80, 0x00}, // YELLOW
                {0x80, 0x00, 0x80}, // MAGENTA
                {0x00, 0x80, 0x80}, // CYAN
                {0xC0, 0xC0, 0xC0}, // LIGHT GRAY
                {0x00, 0xFF, 0xFF}, // BRIGHT CYAN
                {0x00, 0xFF, 0x00}, // BRIGHT GREEN
                {0xD0, 0x90, 0xFF}, // BRIGHT PURPLE
                {0x00, 0x00, 0xFF}, // BRIGHT BLUE
                {0xFF, 0xFF, 0x00}, // BRIGHT YELLOW
                {0xFF, 0x80, 0x00}, // BRIGHT ORANGE
                {0xFF, 0x00, 0x00}, // BRIGHT RED
                {0x00, 0x00, 0x00}  // BLACK
        };

        // --- 5

        byte[] constants = {
                COLOR_RED, COLOR_GREEN, COLOR_BLUE, COLOR_YELLOW, COLOR_MAGENTA,
                COLOR_CYAN, COLOR_LIGHT_GRAY, COLOR_BRIGHT_CYAN, COLOR_BRIGHT_GREEN,
                COLOR_BRIGHT_PURPLE, COLOR_BRIGHT_BLUE, COLOR_BRIGHT_YELLOW,
                COLOR_BRIGHT_ORANGE, COLOR_BRIGHT_RED, COLOR_BLACK
        };

        double minDist = Double.MAX_VALUE;
        int closest = 0;

        for (int i = 0; i < palette.length; i++) {
            int dr = r1 - palette[i][0];
            int dg = g1 - palette[i][1];
            int db = b1 - palette[i][2];
            double dist = dr * dr + dg * dg + db * db;

            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }

        return constants[closest];
    }

}