"""Interactive OAuth token helper for DonationAlerts.

Before running, register the redirect URI below in your DonationAlerts OAuth
application exactly as written (or change REDIRECT_URI to the registered URI).
Only Python 3 is required.
"""

from __future__ import annotations

import json
import secrets
import sys
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen


# Insert the credentials of your DonationAlerts OAuth application here.
CLIENT_ID = "paste"
CLIENT_SECRET = "paste"

# This exact address must be added as Redirect URI in the OAuth application.
REDIRECT_URI = "http://127.0.0.1:8765/callback"
SCOPES = "oauth-user-show oauth-donation-subscribe oauth-goal-subscribe"

AUTHORIZE_URL = "https://www.donationalerts.com/oauth/authorize"
TOKEN_URL = "https://www.donationalerts.com/oauth/token"
OUTPUT_FILE = Path(__file__).with_name("donationalerts_tokens.json")


def exchange_code(code: str) -> dict:
    """Exchange a one-time authorization code for OAuth tokens."""
    payload = urlencode({
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "redirect_uri": REDIRECT_URI,
        "code": code,
    }).encode()
    request = Request(TOKEN_URL, data=payload, method="POST")
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"DonationAlerts returned HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise RuntimeError(f"Could not reach DonationAlerts: {error.reason}") from error


def save_tokens(tokens: dict) -> None:
    OUTPUT_FILE.write_text(json.dumps(tokens, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nTokens saved to: {OUTPUT_FILE}")
    print("Copy them into config.yml, then delete this file if you do not need it.")
    print(f"access_token:  {tokens.get('access_token', '')}")
    print(f"refresh_token: {tokens.get('refresh_token', '')}")


def main() -> int:
    if CLIENT_ID.startswith("PUT_") or CLIENT_SECRET.startswith("PUT_"):
        print("Fill in CLIENT_ID and CLIENT_SECRET at the top of this file first.")
        return 1

    parsed_redirect = urlparse(REDIRECT_URI)
    if parsed_redirect.scheme != "http" or parsed_redirect.hostname not in {"127.0.0.1", "localhost"}:
        print("This helper only supports a local HTTP redirect URI (localhost or 127.0.0.1).")
        return 1

    received: dict[str, str] = {}
    done = threading.Event()
    expected_state = secrets.token_urlsafe(32)

    class CallbackHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 (required method name)
            query = parse_qs(urlparse(self.path).query)
            received["code"] = query.get("code", [""])[0]
            received["state"] = query.get("state", [""])[0]
            received["error"] = query.get("error", [""])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write("<h2>Authorization received</h2><p>You may close this tab and return to the script.</p>".encode())
            done.set()

        def log_message(self, _format: str, *_args: object) -> None:
            return

    server = HTTPServer((parsed_redirect.hostname, parsed_redirect.port or 80), CallbackHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    authorization_url = AUTHORIZE_URL + "?" + urlencode({
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT_URI,
        "response_type": "code",
        "scope": SCOPES,
        "state": expected_state,
    })
    print("Opening DonationAlerts authorization page in your browser...")
    print("If it does not open, use this URL:\n" + authorization_url)
    webbrowser.open(authorization_url)
    print("Waiting for confirmation (up to 5 minutes)...")
    done.wait(timeout=300)
    server.shutdown()
    server.server_close()

    if not done.is_set():
        print("Timed out waiting for authorization.")
        return 1
    if received.get("state") != expected_state:
        print("Authorization response has an invalid state. No token was requested.")
        return 1
    if received.get("error"):
        print(f"Authorization was denied or failed: {received['error']}")
        return 1
    if not received.get("code"):
        print("No authorization code was received.")
        return 1

    try:
        tokens = exchange_code(received["code"])
    except RuntimeError as error:
        print(error)
        return 1

    if not tokens.get("access_token") or not tokens.get("refresh_token"):
        print("DonationAlerts response did not contain both tokens:\n" + json.dumps(tokens, indent=2))
        return 1
    tokens["obtained_at"] = int(time.time())
    tokens["scopes"] = SCOPES
    save_tokens(tokens)
    return 0


if __name__ == "__main__":
    sys.exit(main())
