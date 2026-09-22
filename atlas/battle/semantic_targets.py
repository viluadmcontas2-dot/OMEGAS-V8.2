#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

SEMANTIC_KINDS = {"method", "field", "field-use", "event", "action", "serial", "visual"}
PRIORITY = {
    "event": 0,
    "action": 1,
    "serial": 2,
    "visual": 3,
    "method": 4,
    "field-use": 5,
    "field": 6,
}


def _add(out, seen, kind, target, meta):
    key = (kind, target)
    if key in seen:
        return
    seen.add(key)
    out.append({"kind": kind, "target": target, "meta": meta})


def build(payload: dict) -> list[dict]:
    out = []
    seen = set()
    classes = payload.get("classes", {})

    linked_field_names = {x.get("field") for x in payload.get("object_field_links", [])}
    data_classes = {"TAutoCalDM", "TAutoCalDM_EE", "TAutoCalSettings"}

    for class_name, row in sorted(classes.items()):
        for method in row.get("published_methods", []):
            target = f"{class_name}::{method['name']}@{method['va_hex']}"
            _add(out, seen, "method", target, {"class": class_name, **method})
        for field in row.get("fields", []):
            target = f"{class_name}::{field['name']}@{field['offset_hex']}"
            meta = {"class": class_name, **field}
            _add(out, seen, "field", target, meta)
            if class_name in data_classes or field["name"] in linked_field_names:
                _add(out, seen, "field-use", target, meta)

    for event in payload.get("event_bindings", []):
        target = f"{event['resource']}/{event['object']}.{event['event']}->{event['handler']}"
        _add(out, seen, "event", target, dict(event))

    for action in payload.get("action_bindings", []):
        target = f"{action['resource']}/{action['object']}.Action->{action['action']}"
        _add(out, seen, "action", target, dict(action))

    for serial in payload.get("serial_objects", []):
        target = f"{serial['resource']}/{serial['object']}@{serial['serial_hex']}"
        _add(out, seen, "serial", target, dict(serial))

    for visual in payload.get("visual_objects", []):
        target = f"{visual['resource']}/{visual['object']}:{visual['object_class']}"
        _add(out, seen, "visual", target, dict(visual))

    out.sort(key=lambda x: (PRIORITY.get(x["kind"], 99), x["target"]))
    return out


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def catalog(path: Path) -> dict[tuple[str, str], dict]:
    return {(x["kind"], x["target"]): x for x in build(load(path))}


def find_method_target(payload: dict, handler: str) -> dict | None:
    matches = []
    for target in build(payload):
        if target["kind"] == "method" and target["meta"].get("name") == handler:
            matches.append(target)
    return matches[0] if len(matches) == 1 else None


def find_event_for_action(payload: dict, action_name: str) -> dict | None:
    matches = []
    for target in build(payload):
        if target["kind"] == "event" and target["meta"].get("object") == action_name:
            matches.append(target)
    preferred = [x for x in matches if x["meta"].get("event") == "OnExecute"]
    if len(preferred) == 1:
        return preferred[0]
    return matches[0] if len(matches) == 1 else None
