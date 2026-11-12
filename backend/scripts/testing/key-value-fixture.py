"""Optional local Redis-protocol fixture; CI runs these tests against real Valkey."""
import argparse
import sys
import threading

from fakeredis import TcpFakeServer

parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=17489)
args = parser.parse_args()
server = TcpFakeServer(("127.0.0.1", args.port), server_type="redis")
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
print(f"Redis Lua fixture ready on 127.0.0.1:{args.port}; send quit to stop", flush=True)
try:
    for line in sys.stdin:
        if line.strip() == "quit":
            break
finally:
    server.shutdown()
    server.server_close()
