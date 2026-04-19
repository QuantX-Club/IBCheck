# IBCheck - IBKR API Connection Test Tool

A professional utility to verify connectivity with Interactive Brokers (IBKR) TWS or IB Gateway, primarily implemented in Java with a Python alternative.

## Project Structure

```text
IBCheck/
├── docker-compose.yml       # IB Gateway container definition
├── java/                    # Primary Java implementation
│   ├── lib/                 # Third-party libraries (.jar files tracked by LFS)
│   ├── src/                 
│   │   └── IBCheck.java     # Main Java source code (Core Logic)
│   └── build.sh             # Shell script to compile and package the project
├── python/                  # Python implementation
│   └── IBCheck.py           # Python connection test script
├── .env.example             # Template IB Gateway environment variables
├── .env                     # Local environment variables (Ignored by Git)
├── .gitattributes           # Git LFS configuration for .jar files
└── .gitignore               # Git exclusion rules

```

## Prerequisites

1. **IBKR Software**: TWS or IB Gateway must be running.
2. **API Configuration**: Enable "Enable ActiveX and Socket Clients" in IBKR settings.
3. **Java**: JDK 11 or higher.
4. **Python**: Python 3.13 or higher is required.
5. **Docker**: Docker Desktop / Docker Engine (for IB Gateway container).

---

## Docker Compose (IB Gateway)

Reference: [gnzsnz/ib-gateway-docker](https://github.com/gnzsnz/ib-gateway-docker)

### 1. Create `.env` from `.env.example`

```bash
cp .env.example .env
```

Then edit `.env` and fill in at least:

- `TWS_USERID`
- `TWS_PASSWORD`
- `TRADING_MODE` (`paper` or `live`)
- `VNC_SERVER_PASSWORD` (optional but recommended)

### 2. Start IB Gateway

```bash
docker compose up -d
```

### 3. Verify status and logs

```bash
docker compose ps
docker compose logs --tail=100 ib-gateway
```

### 4. Recreate container after env changes

If you changed `.env` and want a clean restart:

```bash
docker compose down
docker compose up -d
```

---

## Java Implementation

### 1. Build

To compile the source code and package it into an executable JAR:

```bash
cd java
chmod +x build.sh
./build.sh
```

### 2. Run

You can run the tool using the generated JAR file:

```bash
java -jar IBCheck.jar --host "127.0.0.1" --port 4002
```

Alternatively, run the compiled class directly:

```bash
java -cp "bin:lib/*" IBCheck --host "127.0.0.1" --port 4002
```

---

## Python Implementation

### 1. Installation

Install the official Interactive Brokers Python API:

```bash
pip install ibapi
```

### 2. Run

Run the Python version with the same command-line arguments:

```bash
python python/IBCheck.py --host "127.0.0.1" --port 4002
```

---

## Command Line Arguments

Both implementations share the same CLI interface:

| Argument | Description | Default |
| --- | --- | --- |
| `-h`, `--help` | Show this help message and exit | N/A |
| `-host`, `--host` | Set the IP address or host name of TWS/Gateway | `127.0.0.1` |
| `-port`, `--port` | Set the socket port number | `4002` |

### Usage Examples

```bash
# Basic test with default settings
java -jar IBCheck.jar

# Connect to a specific port
java -jar IBCheck.jar --port 7497

# Connect to a remote machine using Python
python python/IBCheck.py --host "127.0.0.1" --port 4002
```

---

## Version Control Notes

* **Git LFS**: All `.jar` files in `java/lib/` are managed via Git LFS to keep the repository size manageable.
* **Exclusions**: Build artifacts (`bin/`, `IBCheck.jar`), local settings (`.env`), and compressed archives (`*.zip`) are ignored via `.gitignore`.

---

**Version**: 0.2.0
