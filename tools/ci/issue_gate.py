"""Portão de Issues do Norte Único (Task 0.0 + regra R10 do índice).

Todo PR para OmegasPlatina precisa estar amarrado a uma Work Unit:
Issue aberta com label `wu`, título `NORTE-WU-0<N> · ...`, branch
`work/platina-f<N>-...`, todas as Tarefas marcadas, "Não provado"
preenchido, binding `docs/workunits/NORTE-WU-0<N>.md` coerente e todo
commit (exceto merges) com `NORTE-WU-0<N> · Tarefa <N>.<M>` no corpo.

Uso no CI: `python3 -B tools/ci/issue_gate.py <pr_number>` (precisa de `gh`).
"""
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
CLOSES = re.compile(r"\b(?:fecha|closes|close|closed|fixes|fix|fixed|resolve|resolves|resolved)\s+#(\d+)", re.I)
BRANCH = re.compile(r"^work/platina-f(\d)-[a-z0-9][a-z0-9-]*$")
BOX = re.compile(r"^\s*-\s*\[( |x|X)\]\s*(\S+)")


def _section(body, title):
    """Texto da seção `## <title>...` até o próximo `## `."""
    out, inside = [], False
    for line in body.splitlines():
        if line.startswith("## "):
            inside = line[3:].strip().lower().startswith(title.lower())
            continue
        if inside:
            out.append(line)
    return "\n".join(out) if out or inside else None


def closes_issue(pr_body):
    m = CLOSES.search(pr_body or "")
    return int(m.group(1)) if m else None


def check(pr_body, branch, issue, commits, binding=None):
    """Lista de violações (vazia = passa)."""
    errors = []
    number = closes_issue(pr_body)
    if number is None:
        errors.append("PR sem 'Fecha #n' no corpo: amarre o PR à Issue da Work Unit.")

    elif issue.get("state", "").upper() != "OPEN":
        errors.append(f"Issue #{number} está fechada; o PR precisa fechar uma Issue aberta.")
    if number is not None and "wu" not in {label.get("name") for label in issue.get("labels", [])}:
        errors.append(f"Issue #{number} sem label wu (Work Unit).")

    m = BRANCH.match(branch or "")
    if not m:
        errors.append(f"branch '{branch}' fora do padrão work/platina-f<N>-<slug>.")
        prefix = None
    else:
        prefix = f"NORTE-WU-0{m.group(1)}"
        if not issue.get("title", "").startswith(prefix + " "):
            errors.append(f"branch '{branch}' pede Issue com título iniciando em {prefix}; achei '{issue.get('title', '')}'.")

    body = issue.get("body", "") or ""
    tasks = _section(body, "Tarefas")
    if tasks is None:
        errors.append("Issue sem seção '## Tarefas'.")
    else:
        open_tasks = [b.group(2) for b in map(BOX.match, tasks.splitlines()) if b and b.group(1) == " "]
        if open_tasks:
            errors.append("Tarefas não marcadas na Issue: " + ", ".join(open_tasks) + ".")
    unproven = _section(body, "Não provado")
    if unproven is None or not unproven.strip():
        errors.append("seção 'Não provado' da Issue está vazia: diga o que este PR não prova.")

    if prefix:
        tag = re.compile(re.escape(prefix) + r"\s*·\s*Tarefa\s+" + m.group(1) + r"\.\d+")
        for c in commits:
            headline = c.get("messageHeadline", "")
            if headline.startswith("Merge "):
                continue
            if not tag.search(c.get("messageBody", "") or ""):
                errors.append(f"commit '{headline}' sem a linha '{prefix} · Tarefa {m.group(1)}.<M>' no corpo.")

        if binding is None:
            errors.append(f"binding docs/workunits/{prefix}.md ausente na head do PR (regra R10).")
        else:
            if number is not None and not re.search(rf"#{number}\b", binding):
                errors.append(f"binding docs/workunits/{prefix}.md não cita a Issue #{number}.")
            if branch not in binding:
                errors.append(f"binding docs/workunits/{prefix}.md não cita a branch {branch}.")
    return errors


def _gh(*args):
    return json.loads(subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout)


def main(pr_number):
    pr = _gh("pr", "view", pr_number, "--json", "body,headRefName,commits")
    number = closes_issue(pr["body"])
    issue = _gh("issue", "view", str(number), "--json", "state,labels,title,body") if number else {}
    m = BRANCH.match(pr["headRefName"])
    path = ROOT / "docs/workunits" / f"NORTE-WU-0{m.group(1)}.md" if m else None
    binding = path.read_text(encoding="utf-8") if path and path.is_file() else None
    errors = check(pr["body"], pr["headRefName"], issue, pr["commits"], binding)
    for e in errors:
        print(f"ISSUE_GATE_VIOLATION: {e}")
    print("ISSUE_GATE=" + ("FAIL" if errors else "PASS"))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
