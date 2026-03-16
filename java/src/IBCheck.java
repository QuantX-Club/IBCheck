import com.ib.client.DefaultEWrapper;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IBCheck extends DefaultEWrapper {
    private EClientSocket client;
    private EJavaSignal signal = new EJavaSignal();
    private EReader reader;
    private boolean isFinished = false;
    
    // Define standard log time format (e.g., 2026-03-17 12:30:45)
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public IBCheck() {
        // Initialize the client socket
        client = new EClientSocket(this, signal);
    }

    // Helper method for standard info logging
    private void logInfo(String message) {
        System.out.println("[" + LocalDateTime.now().format(FORMATTER) + "] [INFO] " + message);
    }

    // Helper method for standard error logging
    private void logError(String message) {
        System.err.println("[" + LocalDateTime.now().format(FORMATTER) + "] [ERROR] " + message);
    }

    @Override
    public void nextValidId(int orderId) {
        logInfo("✅ Status: API Connected Successfully");
    }

    @Override
    public void managedAccounts(String accounts) {
        logInfo("📋 Accounts Found: " + accounts);
        isFinished = true; // Mark as finished to proceed to exit
    }

    @Override
    public void error(int id, long time, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        // IB sends system info (like 2104, 2106, 2158 data farm status) through the error method.
        // We filter out codes between 2100 and 2160 to keep the console output clean.
        if (errorCode >= 2100 && errorCode <= 2160) {
            return;
        }
        // Print actual errors
        logError("Code [" + errorCode + "]: " + errorMsg);
    }
    
    @Override
    public void error(Exception e) {
        logError("Exception: " + e.getMessage());
    }

    @Override
    public void error(String str) {
        logError("Message: " + str);
    }

    public void start(String host, int port) {
        // UI Formatting for console
        System.out.println("\n=========================================");
        logInfo("🚀 IBKR API Connection Test Started");
        logInfo("🔗 Target: " + host + ":" + port);
        System.out.println("-----------------------------------------");
        
        int clientId = 10;
        client.eConnect(host, port, clientId);

        // Start the API reader thread
        final EReaderSignal readerSignal = signal;
        reader = new EReader(client, readerSignal);
        reader.start();
        
        new Thread(() -> {
            while (client.isConnected()) {
                readerSignal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    logError("Read Error: " + e.getMessage());
                }
            }
        }).start();

        // Wait for results (timeout after 5 seconds)
        int timeout = 0;
        while (!isFinished && timeout < 50) {
            try { Thread.sleep(100); timeout++; } catch (Exception e) {}
        }

        System.out.println("-----------------------------------------");
        if (!isFinished) {
            logError("⚠️ Connection Timeout. Check if IB Gateway/TWS is running.");
        } else {
            logInfo("✨ Test Completed.");
        }
        
        System.out.println("=========================================\n");
        
        client.eDisconnect();
        System.exit(0);
    }

    // Display help menu
    private static void printHelp() {
        System.out.println("Usage: java -jar IBCheck.jar [options]");
        System.out.println("Options:");
        System.out.println("  -h, --help       Show this help message");
        System.out.println("  -host, --host    Set the IP address or host name (Default: 127.0.0.1)");
        System.out.println("  -port, --port    Set the port number (Default: 4002)");
        System.out.println("Example:");
        System.out.println("  java -jar IBCheck.jar -port 4002 -host 127.0.0.1");
    }

    public static void main(String[] args) {
        // Set default values
        String host = "127.0.0.1";
        int port = 4002;

        // Iterate through all command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
                case "-host":
                case "--host":
                    // Ensure there is a value to read next
                    if (i + 1 < args.length) {
                        host = args[++i]; // Read the next element and increment the index
                    } else {
                        System.err.println("Error: Missing IP/Host parameter value");
                        System.exit(1);
                    }
                    break;
                case "-port":
                case "--port":
                    // Ensure there is a value to read next
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Port must be a valid number");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: Missing Port parameter value");
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.println("Unknown parameter ignored: " + args[i]);
                    break;
            }
        }

        new IBCheck().start(host, port);
    }
}