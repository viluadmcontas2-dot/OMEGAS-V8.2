#!/usr/bin/env python3
import argparse, json
from pathlib import Path

def r1():
    return [
        {"id":"ui-bytes","driver":"bytestring","target":"TAutoCalUI"},
        {"id":"ui-gnu-strings","driver":"gnu-strings","target":"TAutoCalUI"},
        {"id":"ui-pe-map","driver":"pe-map","target":"TAutoCalUI"},
        {"id":"ui-undelphi","driver":"undelphi","target":"TAutoCalUI"},
        {"id":"action-bytes","driver":"bytestring","target":"ActionAutoCalRifExecute"},
        {"id":"action-pe-map","driver":"pe-map","target":"ActionAutoCalRifExecute"},
        {"id":"fixture-escalate","driver":"fixture-escalate","target":"fixture:escalation"},
        {"id":"fixture-contradiction-a","driver":"fixture-a","target":"fixture:contradiction"},
        {"id":"fixture-contradiction-b","driver":"fixture-b","target":"fixture:contradiction"},
    ]

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--round", type=int, required=True)
    ap.add_argument("--frontier")
    ap.add_argument("--output", required=True)
    args=ap.parse_args()
    if args.round == 1:
        lanes=r1()
    else:
        frontier=json.loads(Path(args.frontier).read_text(encoding="utf-8"))
        lanes=frontier.get("lanes", [])
    Path(args.output).write_text(json.dumps({"include":lanes}, separators=(",",":"))+"\n", encoding="utf-8")
    print(json.dumps({"round":args.round,"lanes":len(lanes)}))

if __name__=="__main__":
    main()
