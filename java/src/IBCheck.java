import com.ib.client.DefaultEWrapper;
import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import com.ib.client.TickAttrib;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IBCheck extends DefaultEWrapper {
    private static final int DEFAULT_CLIENT_ID = 10;
    private static final int SPX_REQ_ID = 9001;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 5000;
    private static final int DEFAULT_SPX_TIMEOUT_SEC = 15;

    private EClientSocket client;
    private EJavaSignal signal = new EJavaSignal();
    private EReader reader;
    private volatile boolean accountReceived = false;
    private volatile boolean spxCheckFinished = false;
    private volatile boolean spxDataReceived = false;
    private volatile String spxErrorMessage = "";
    
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
        accountReceived = true;
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        if (tickerId != SPX_REQ_ID || spxDataReceived || price <= 0 || !Double.isFinite(price)) {
            return;
        }

        spxDataReceived = true;
        spxCheckFinished = true;
        logInfo("📈 SPX Data OK: " + tickFieldName(field) + " = " + price);
        client.cancelMktData(SPX_REQ_ID);
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        if (reqId == SPX_REQ_ID && !spxDataReceived) {
            spxCheckFinished = true;
            if (spxErrorMessage.isEmpty()) {
                spxErrorMessage = "Snapshot ended without a usable SPX price.";
            }
        }
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        if (reqId == SPX_REQ_ID) {
            logInfo("📡 SPX Market Data Type: " + marketDataTypeName(marketDataType));
        }
    }

    @Override
    public void error(int id, long time, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        // IB sends system info (like 2104, 2106, 2158 data farm status) through the error method.
        // We filter out codes between 2100 and 2160 to keep the console output clean.
        if (errorCode >= 2100 && errorCode <= 2160) {
            return;
        }

        if (id == SPX_REQ_ID) {
            spxErrorMessage = "Code [" + errorCode + "]: " + errorMsg;
            if (errorCode == 200 || errorCode == 354 || errorCode == 420) {
                spxCheckFinished = true;
            }
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

    private void requestSpxData() {
        Contract spx = new Contract();
        spx.symbol("SPX");
        spx.secType("IND");
        spx.exchange("CBOE");
        spx.currency("USD");

        logInfo("📈 Checking SPX market data snapshot...");
        client.reqMarketDataType(3);
        client.reqMktData(SPX_REQ_ID, spx, "", true, false, null);
    }

    private boolean waitUntil(BooleanSupplier condition, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private static String marketDataTypeName(int marketDataType) {
        switch (marketDataType) {
            case 1:
                return "live";
            case 2:
                return "frozen";
            case 3:
                return "delayed";
            case 4:
                return "delayed-frozen";
            default:
                return "unknown (" + marketDataType + ")";
        }
    }

    private static String tickFieldName(int field) {
        switch (field) {
            case 1:
                return "bid";
            case 2:
                return "ask";
            case 4:
                return "last";
            case 6:
                return "high";
            case 7:
                return "low";
            case 9:
                return "close";
            case 14:
                return "open";
            case 66:
                return "delayed bid";
            case 67:
                return "delayed ask";
            case 68:
                return "delayed last";
            case 72:
                return "delayed high";
            case 73:
                return "delayed low";
            case 75:
                return "delayed close";
            case 76:
                return "delayed open";
            default:
                return "field " + field;
        }
    }

    public void start(String host, int port, boolean checkSpx, int spxTimeoutSec) {
        // UI Formatting for console
        System.out.println("\n=========================================");
        logInfo("🚀 IBKR API Connection Test Started");
        logInfo("🔗 Target: " + host + ":" + port);
        logInfo("📊 SPX Data Check: " + (checkSpx ? "enabled" : "disabled"));
        System.out.println("-----------------------------------------");
        
        client.eConnect(host, port, DEFAULT_CLIENT_ID);

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

        boolean success = waitUntil(() -> accountReceived, DEFAULT_CONNECTION_TIMEOUT_MS);

        if (success && checkSpx) {
            requestSpxData();
            success = waitUntil(() -> spxCheckFinished, spxTimeoutSec * 1000);
            if (!spxDataReceived) {
                success = false;
                if (!spxCheckFinished) {
                    spxErrorMessage = "SPX data timeout after " + spxTimeoutSec + " second(s).";
                    client.cancelMktData(SPX_REQ_ID);
                }
            }
        }

        System.out.println("-----------------------------------------");
        if (!accountReceived) {
            logError("⚠️ Connection Timeout. Check if IB Gateway/TWS is running.");
        } else if (checkSpx && !spxDataReceived) {
            logError("⚠️ SPX Data Check Failed. " + spxErrorMessage);
        } else {
            logInfo("✨ Test Completed.");
        }
        
        System.out.println("=========================================\n");
        
        client.eDisconnect();
        System.exit(success ? 0 : 1);
    }

    // Display help menu
    private static void printHelp() {
        System.out.println("Usage: java -jar IBCheck.jar [options]");
        System.out.println("Options:");
        System.out.println("  -h, --help       Show this help message");
        System.out.println("  -host, --host    Set the IP address or host name (Default: 127.0.0.1)");
        System.out.println("  -port, --port    Set the port number (Default: 4002)");
        System.out.println("  --skip-spx       Skip the SPX market data check");
        System.out.println("  --spx-timeout    Seconds to wait for SPX data (Default: 15)");
        System.out.println("Example:");
        System.out.println("  java -jar IBCheck.jar -port 4002 -host 127.0.0.1");
        System.out.println("  java -jar IBCheck.jar --host 127.0.0.1 --port 4002 --spx-timeout 20");
    }

    public static void main(String[] args) {
        // Set default values
        String host = "127.0.0.1";
        int port = 4002;
        boolean checkSpx = true;
        int spxTimeoutSec = DEFAULT_SPX_TIMEOUT_SEC;

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
                case "--skip-spx":
                    checkSpx = false;
                    break;
                case "--spx-timeout":
                    if (i + 1 < args.length) {
                        try {
                            spxTimeoutSec = Integer.parseInt(args[++i]);
                            if (spxTimeoutSec <= 0) {
                                System.err.println("Error: SPX timeout must be greater than 0");
                                System.exit(1);
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: SPX timeout must be a valid number");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: Missing SPX timeout parameter value");
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.println("Unknown parameter ignored: " + args[i]);
                    break;
            }
        }

        new IBCheck().start(host, port, checkSpx, spxTimeoutSec);
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
