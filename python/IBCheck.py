import sys
import threading
import time
import argparse
from datetime import datetime
from ibapi.client import EClient
from ibapi.wrapper import EWrapper

class IBCheck(EWrapper, EClient):
    def __init__(self):
        EClient.__init__(self, self)
        self.is_finished = False
        self.time_format = "%Y-%m-%d %H:%M:%S"

    def log_info(self, message):
        timestamp = datetime.now().strftime(self.time_format)
        print(f"[{timestamp}] [INFO] {message}")

    def log_error(self, message):
        timestamp = datetime.now().strftime(self.time_format)
        print(f"[{timestamp}] [ERROR] {message}", file=sys.stderr)

    def nextValidId(self, orderId: int):
        self.log_info("✅ Status: API Connected Successfully")

    def managedAccounts(self, accounts: str):
        self.log_info(f"📋 Accounts Found: {accounts}")
        self.is_finished = True 

    def error(self, reqId, errorCode, errorString, advancedOrderRejectJson=''):
        if 2100 <= errorCode <= 2160:
            return
        self.log_error(f"Code [{errorCode}]: {errorString}")

    def start(self, host, port):
        print("\n=========================================")
        self.log_info("🚀 IBKR API Connection Test Started (Python)")
        self.log_info(f"🔗 Target: {host}:{port}")
        print("-----------------------------------------")

        client_id = 10
        self.connect(host, port, client_id)

        thread = threading.Thread(target=self.run, daemon=True)
        thread.start()

        timeout_counter = 0
        while not self.is_finished and timeout_counter < 50:
            time.sleep(0.1)
            timeout_counter += 1

        print("-----------------------------------------")
        if not self.is_finished:
            self.log_error("⚠️ Connection Timeout. Check if IB Gateway/TWS is running.")
        else:
            self.log_info("✨ Test Completed.")
        
        print("=========================================\n")
        self.disconnect()
        sys.exit(0)

def main():
    # Use argparse for robust and safe CLI argument parsing
    # We disable default help to manually handle -h if needed, 
    # but here we follow standard conventions.
    parser = argparse.ArgumentParser(
        description="IBKR API Connection Test Tool",
        add_help=True
    )

    # Adding arguments with type validation and default values
    parser.add_argument(
        '-host', '--host', 
        type=str, 
        default='127.0.0.1', 
        help='The IP address or host name (Default: 127.0.0.1)'
    )
    parser.add_argument(
        '-port', '--port', 
        type=int, 
        default=4002, 
        help='The port number (Default: 4002)'
    )

    args = parser.parse_args()

    # Initialize and start the check
    app = IBCheck()
    app.start(args.host, args.port)

if __name__ == "__main__":
    main()