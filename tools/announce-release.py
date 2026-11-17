#!/usr/bin/env python3
"""Posts a published GitHub release to Discord #news as an embed: the "Changes" list from the
release notes, the jars and a link to the download page.

    DISCORD_WEBHOOK_URL=... GH_TOKEN=... python3 tools/announce-release.py V1.6.9
    DRY_RUN=1 python3 tools/announce-release.py V1.6.9      (prints the embed, posts nothing)

The webhook URL is a repository secret (DISCORD_WEBHOOK_URL), never written in this public repo:
anyone holding it can post in the channel. Without it this prints a notice and does nothing, so a
fork or a missing secret never fails a release. Plain Python, no packages: runs the same here and
on the CI machine.
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

# Discord's limits: 4096 for the description, 1024 per field value. Kept well under.
DESCRIPTION_LIMIT = 3800
FIELD_LIMIT = 1000
GREEN = 0x57F287


def changes(body):
    """The lines under "## Changes", up to the next heading. The jar table below it is Markdown
    Discord does not draw."""
    out, on = [], False
    for line in body.replace("\r", "").split("\n"):
        if line.startswith("## "):
            on = line.strip().lower() == "## changes"
            continue
        if on and line.strip():
            out.append(line.rstrip())
    return out


def fit(lines, limit, more):
    """Whole lines only, ending with a pointer to the full notes when they do not all fit."""
    text = "\n".join(lines)
    if len(text) <= limit:
        return text
    kept = []
    for line in lines:
        if len("\n".join(kept + [line, more])) > limit:
            break
        kept.append(line)
    return "\n".join(kept + [more])


def payload(release):
    url = release["url"]
    jars = [a["name"] for a in release.get("assets", []) if a["name"].endswith(".jar")]
    description = fit(changes(release.get("body") or ""), DESCRIPTION_LIMIT,
                      "…and more in the full notes.") or "- Small fixes."
    return {
        # Never ping anyone, whatever a commit subject says.
        "allowed_mentions": {"parse": []},
        "embeds": [{
            "title": (release.get("name") or release.get("tagName") or "New release")[:250],
            "url": url,
            "color": GREEN,
            "description": description,
            "fields": [
                {"name": "Download",
                 "value": f"[GitHub release]({url})\nPick the jar for your Minecraft version and put it "
                          "in your mods folder with Fabric API."},
                {"name": "Jars",
                 "value": fit([f"`{j}`" for j in jars], FIELD_LIMIT, "…") or "See the download page."},
            ],
        }],
    }


def post(webhook, data):
    body = json.dumps(data).encode("utf-8")
    for attempt in range(4):
        request = urllib.request.Request(webhook, data=body, method="POST", headers={
            "Content-Type": "application/json", "User-Agent": "minerefine-release-announcer"})
        try:
            with urllib.request.urlopen(request, timeout=30):
                return
        except urllib.error.HTTPError as e:
            # 429: Discord is busy and says how long to wait. Anything else 4xx will not get better.
            if e.code == 429 and attempt < 3:
                time.sleep(min(float(e.headers.get("Retry-After") or 2), 30))
                continue
            if e.code >= 500 and attempt < 3:
                time.sleep(2 ** attempt)
                continue
            # The response, never the URL: it is the secret.
            sys.exit(f"Discord refused the post: HTTP {e.code} {e.read()[:500].decode('utf-8', 'replace')}")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < 3:
                time.sleep(2 ** attempt)
                continue
            sys.exit(f"Could not reach Discord: {getattr(e, 'reason', e)}")


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: announce-release.py <tag>")
    tag = sys.argv[1]
    dry = os.environ.get("DRY_RUN") == "1"
    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not webhook and not dry:
        print(f"::notice::DISCORD_WEBHOOK_URL is not set; {tag} is not announced on Discord.")
        return
    release = json.loads(subprocess.run(
        ["gh", "release", "view", tag, "--json", "name,tagName,body,url,assets"],
        check=True, capture_output=True, text=True, encoding="utf-8").stdout)
    data = payload(release)
    if dry:
        print(json.dumps(data, indent=2, ensure_ascii=False))
        return
    post(webhook, data)
    print(f"Announced {tag} on Discord.")


if __name__ == "__main__":
    main()
