import sys
import threading
import time
import argparse
import math
from datetime import datetime
from ibapi.client import EClient
from ibapi.contract import Contract
from ibapi.wrapper import EWrapper


class IBCheck(EWrapper, EClient):
    DEFAULT_CLIENT_ID = 10
    DEFAULT_CONNECTION_TIMEOUT_SEC = 5
    DEFAULT_SPX_TIMEOUT_SEC = 15
    SPX_REQ_ID = 9001

    def __init__(self):
        EClient.__init__(self, self)
        self.account_received = False
        self.spx_check_finished = False
        self.spx_data_received = False
        self.spx_error_message = ""
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
        self.account_received = True

    def tickPrice(self, reqId, tickType, price, attrib):
        if (
            reqId != self.SPX_REQ_ID
            or self.spx_data_received
            or price is None
            or not math.isfinite(price)
            or price <= 0
        ):
            return

        self.spx_data_received = True
        self.spx_check_finished = True
        self.log_info(f"📈 SPX Data OK: {self.tick_field_name(tickType)} = {price}")
        self.cancelMktData(self.SPX_REQ_ID)

    def tickSnapshotEnd(self, reqId: int):
        if reqId == self.SPX_REQ_ID and not self.spx_data_received:
            self.spx_check_finished = True
            if not self.spx_error_message:
                self.spx_error_message = "Snapshot ended without a usable SPX price."

    def marketDataType(self, reqId: int, marketDataType: int):
        if reqId == self.SPX_REQ_ID:
            self.log_info(f"📡 SPX Market Data Type: {self.market_data_type_name(marketDataType)}")

    def error(self, reqId, errorCode, errorString, advancedOrderRejectJson=''):
        if 2100 <= errorCode <= 2160:
            return
        if reqId == self.SPX_REQ_ID:
            self.spx_error_message = f"Code [{errorCode}]: {errorString}"
            if errorCode in {200, 354, 420}:
                self.spx_check_finished = True
        self.log_error(f"Code [{errorCode}]: {errorString}")

    def request_spx_data(self):
        spx = Contract()
        spx.symbol = "SPX"
        spx.secType = "IND"
        spx.exchange = "CBOE"
        spx.currency = "USD"

        self.log_info("📈 Checking SPX market data snapshot...")
        self.reqMarketDataType(3)
        self.reqMktData(self.SPX_REQ_ID, spx, "", True, False, [])

    def wait_until(self, condition, timeout_sec):
        deadline = time.monotonic() + timeout_sec
        while not condition() and time.monotonic() < deadline:
            time.sleep(0.1)
        return condition()

    @staticmethod
    def market_data_type_name(market_data_type):
        return {
            1: "live",
            2: "frozen",
            3: "delayed",
            4: "delayed-frozen",
        }.get(market_data_type, f"unknown ({market_data_type})")

    @staticmethod
    def tick_field_name(tick_type):
        return {
            1: "bid",
            2: "ask",
            4: "last",
            6: "high",
            7: "low",
            9: "close",
            14: "open",
            66: "delayed bid",
            67: "delayed ask",
            68: "delayed last",
            72: "delayed high",
            73: "delayed low",
            75: "delayed close",
            76: "delayed open",
        }.get(tick_type, f"field {tick_type}")

    def start(self, host, port, check_spx=True, spx_timeout_sec=DEFAULT_SPX_TIMEOUT_SEC):
        print("\n=========================================")
        self.log_info("🚀 IBKR API Connection Test Started (Python)")
        self.log_info(f"🔗 Target: {host}:{port}")
        self.log_info(f"📊 SPX Data Check: {'enabled' if check_spx else 'disabled'}")
        print("-----------------------------------------")

        self.connect(host, port, self.DEFAULT_CLIENT_ID)

        thread = threading.Thread(target=self.run, daemon=True)
        thread.start()

        success = self.wait_until(
            lambda: self.account_received,
            self.DEFAULT_CONNECTION_TIMEOUT_SEC,
        )

        if success and check_spx:
            self.request_spx_data()
            success = self.wait_until(
                lambda: self.spx_check_finished,
                spx_timeout_sec,
            )
            if not self.spx_data_received:
                success = False
                if not self.spx_check_finished:
                    self.spx_error_message = (
                        f"SPX data timeout after {spx_timeout_sec} second(s)."
                    )
                    self.cancelMktData(self.SPX_REQ_ID)

        print("-----------------------------------------")
        if not self.account_received:
            self.log_error("⚠️ Connection Timeout. Check if IB Gateway/TWS is running.")
        elif check_spx and not self.spx_data_received:
            self.log_error(f"⚠️ SPX Data Check Failed. {self.spx_error_message}")
        else:
            self.log_info("✨ Test Completed.")

        print("=========================================\n")
        self.disconnect()
        sys.exit(0 if success else 1)


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
    parser.add_argument(
        '--skip-spx',
        action='store_true',
        help='Skip the SPX market data check'
    )
    parser.add_argument(
        '--spx-timeout',
        type=int,
        default=IBCheck.DEFAULT_SPX_TIMEOUT_SEC,
        help='Seconds to wait for SPX data (Default: 15)'
    )

    args = parser.parse_args()
    if args.spx_timeout <= 0:
        parser.error('--spx-timeout must be greater than 0')

    # Initialize and start the check
    app = IBCheck()
    app.start(args.host, args.port, not args.skip_spx, args.spx_timeout)


if __name__ == "__main__":
    main()
