import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "tools/ci"))
from issue_gate import check

ok_issue = {"state": "OPEN", "labels": [{"name": "wu"}], "title": "NORTE-WU-02 · Tirar o Predictor, o AutoMatch manual e o cérebro V7",
            "body": "## Tarefas\n- [x] 2.1 a\n- [x] 2.2 b\n\n## Prova exigida\nrender\n\n## Não provado (preencher ao fechar)\nBalão sobre outros apps."}
commit = [{"messageHeadline": "feat: x", "messageBody": "NORTE-WU-02 · Tarefa 2.1"}]
branch = "work/platina-f2-poda-1"
binding = "# NORTE-WU-02\n- Issue (checklist autoritativa): #12\n- Branch: `work/platina-f2-poda-1`\n"


def v(body="Fecha #12", br=branch, issue=ok_issue, commits=commit, bind=binding):
    return " ".join(check(body, br, issue, commits, bind))


assert check("Fecha #12", branch, ok_issue, commit, binding) == []
assert "sem 'Fecha #n'" in v(body="corpo")
assert "label wu" in v(issue={**ok_issue, "labels": []})
assert "fechada" in v(issue={**ok_issue, "state": "CLOSED"})
assert "NORTE-WU-02" in v(br="work/platina-f3-poda-2")
assert "branch" in v(br="claude/qualquer")
assert "2.2" in v(issue={**ok_issue, "body": ok_issue["body"].replace("[x] 2.2", "[ ] 2.2")})
assert "Não provado" in v(issue={**ok_issue, "body": "## Tarefas\n- [x] 2.1 a\n\n## Não provado (preencher ao fechar)\n"})
assert "Tarefa" in v(commits=[{"messageHeadline": "fix", "messageBody": ""}])
assert "Tarefa" in v(commits=[{"messageHeadline": "fix", "messageBody": "NORTE-WU-03 · Tarefa 3.1"}])
assert check("Fecha #12", branch, ok_issue, commit + [{"messageHeadline": "Merge branch 'OmegasPlatina'", "messageBody": ""}], binding) == []
# R10: binding versionado em docs/workunits/NORTE-WU-0<N>.md
assert "binding" in v(bind=None)
assert "#12" in v(bind=binding.replace("#12", "#99"))
assert "work/platina-f2-poda-1" in v(bind=binding.replace("f2-poda-1", "f9-x"))
# caixas de "Prova exigida" fora de "Tarefas" não contam como tarefa
assert check("closes #12", branch, ok_issue, commit, binding) == []
print("ISSUE_GATE_CONTRACT=PASS")
