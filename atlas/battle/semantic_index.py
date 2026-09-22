#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path

TARGET_CLASSES={"TAutoCalDM","TAutoCalDM_EE","TAutoCalSettings","TAutoCalUI","TFormRifAutocal"}
CLASS_RE=re.compile(r"^\s{2}(T\w+)\s+.*?size=(\d+)B,\s*vmt=(0x[0-9a-fA-F]+)")
FIELD_RE=re.compile(r"^\s+\+0x([0-9a-fA-F]+)\s+([A-Za-z_]\w*)\s*:\s*(.+?)\s*$")
METHOD_RE=re.compile(r"^\s+(0x[0-9a-fA-F]+)\s+([A-Za-z_]\w*)\s*$")
RESOURCE_RE=re.compile(r"^\s*resource\s+(\S+)",re.I)
OBJECT_RE=re.compile(r"^\s*<object\s+([^:>]+):([^>]+)>")
PROP_RE=re.compile(r"^\s+([A-Za-z_]\w*)\s*=\s*(.*?)\s*$")

def clean_value(value:str):
    v=value.strip()
    if len(v)>=2 and v[0]==v[-1]=='"': return v[1:-1]
    if re.fullmatch(r"-?\d+",v):
        try:return int(v)
        except ValueError:pass
    if v.lower() in {"true","false"}:return v.lower()=="true"
    return v

def parse(text:str):
    classes={}; current=None; section=None
    for line in text.splitlines():
        cm=CLASS_RE.match(line)
        if cm:
            current=cm.group(1);section=None
            if current in TARGET_CLASSES:
                classes[current]={"size":int(cm.group(2)),"vmt":cm.group(3).lower(),"fields":[],"published_methods":[]}
            continue
        if current not in TARGET_CLASSES:continue
        if "fields (" in line:section="fields";continue
        if "published methods (" in line:section="methods";continue
        if re.match(r"^\s{4}(?:interfaces|virtual methods|instance layout|published properties)",line):
            section=None
        if section=="fields":
            m=FIELD_RE.match(line)
            if m:classes[current]["fields"].append({"offset":int(m.group(1),16),"offset_hex":"0x"+m.group(1).lower(),"name":m.group(2),"type":m.group(3)})
        elif section=="methods":
            m=METHOD_RE.match(line)
            if m:classes[current]["published_methods"].append({"va":int(m.group(1),16),"va_hex":m.group(1).lower(),"name":m.group(2)})

    resources=[]; resource=None; obj=None
    for line in text.splitlines():
        rm=RESOURCE_RE.match(line)
        if rm:
            resource=rm.group(1)
            obj=None
            if "AUTOCAL" in resource.upper():
                resources.append({"name":resource,"objects":[]})
            continue
        if not resources or resources[-1]["name"]!=resource:continue
        om=OBJECT_RE.match(line)
        if om:
            obj={"class":om.group(1).strip(),"name":om.group(2).strip(),"properties":{}}
            resources[-1]["objects"].append(obj)
            continue
        if obj is not None:
            pm=PROP_RE.match(line)
            if pm:obj["properties"][pm.group(1)]=clean_value(pm.group(2))

    methods={m["name"]:{**m,"class":cn} for cn,c in classes.items() for m in c["published_methods"]}
    fields=[{**f,"class":cn} for cn,c in classes.items() for f in c["fields"]]
    objects=[{**o,"resource":r["name"]} for r in resources for o in r["objects"]]
    object_names={o["name"] for o in objects}
    event_bindings=[];action_bindings=[];serial_objects=[];visual_objects=[]
    for o in objects:
        for prop,val in o["properties"].items():
            if prop.startswith("On") and isinstance(val,str) and val:
                event_bindings.append({"resource":o["resource"],"object":o["name"],"object_class":o["class"],"event":prop,"handler":val,"resolved_method":val in methods})
            elif prop=="Action" and isinstance(val,str) and val:
                action_bindings.append({"resource":o["resource"],"object":o["name"],"action":val,"resolved_object":val in object_names})
        if isinstance(o["properties"].get("SerialCode"),int):
            serial_objects.append({"resource":o["resource"],"object":o["name"],"object_class":o["class"],"serial_code":o["properties"]["SerialCode"],"serial_hex":f"0x{o['properties']['SerialCode']:04x}","file_section":o["properties"].get("FileSection"),"file_key":o["properties"].get("FileKeyName")})
        if "Series" in o["class"] or o["class"] in {"TChart","TShape"}:
            visual_objects.append({"resource":o["resource"],"object":o["name"],"object_class":o["class"]})
    field_names={f["name"] for f in fields}
    object_field_links=[{"object":o["name"],"resource":o["resource"],"field":o["name"]} for o in objects if o["name"] in field_names]
    unresolved_events=[x for x in event_bindings if not x["resolved_method"]]
    unresolved_actions=[x for x in action_bindings if not x["resolved_object"]]
    return {
        "schema":"omegas.atlas.semantic-index.v1",
        "classes":classes,
        "fields":fields,
        "resources":resources,
        "objects":objects,
        "event_bindings":event_bindings,
        "action_bindings":action_bindings,
        "serial_objects":serial_objects,
        "visual_objects":visual_objects,
        "object_field_links":object_field_links,
        "unresolved_event_bindings":unresolved_events,
        "unresolved_action_bindings":unresolved_actions,
        "counts":{
            "classes":len(classes),"fields":len(fields),"published_methods":len(methods),
            "resources":len(resources),"objects":len(objects),"event_bindings":len(event_bindings),
            "action_bindings":len(action_bindings),"serial_objects":len(serial_objects),
            "visual_objects":len(visual_objects),"object_field_links":len(object_field_links),
            "unresolved_event_bindings":len(unresolved_events),"unresolved_action_bindings":len(unresolved_actions)
        }
    }

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--input",type=Path,required=True);ap.add_argument("--output",type=Path,required=True);a=ap.parse_args()
    payload=parse(a.input.read_text(encoding="utf-8",errors="replace"))
    required={"TAutoCalDM","TAutoCalUI"}
    missing=sorted(required-set(payload["classes"]))
    if missing:raise SystemExit("missing required AutoCal classes: "+", ".join(missing))
    if payload["counts"]["fields"]<100:raise SystemExit("implausibly small AutoCal field corpus")
    if payload["counts"]["published_methods"]<40:raise SystemExit("implausibly small AutoCal method corpus")
    if payload["counts"]["serial_objects"]<10:raise SystemExit("implausibly small AutoCal serial corpus")
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(payload,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(payload["counts"],indent=2))

if __name__=="__main__":main()
