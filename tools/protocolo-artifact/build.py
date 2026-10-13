#!/usr/bin/env python3
"""Gera o HTML do artefato "Landi Renzo Omegas" a partir do repositório PROTOCOLO-LANDI.

Idempotente: mesma entrada, mesma saída (sem relógio, sem aleatoriedade). O modelo
(template.html) guarda a parte escrita à mão; o bloco entre os marcadores GEN é refeito
a cada execução com os arquivos NN-*.md do repositório e o SHA do commit lido.

Uso: python3 tools/protocolo-artifact/build.py --landi /home/user/protocolo-landi --out /caminho/artefato.html
"""
import argparse
import html
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BEGIN, END = "<!--GEN:BEGIN-->", "<!--GEN:END-->"


def inline(text: str) -> str:
    t = html.escape(text, quote=False)
    codes = []

    def keep(m):
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    t = re.sub(r"`([^`]+)`", keep, t)
    t = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"(?<![\w*])\*([^*\s][^*]*?)\*(?![\w*])", r"<em>\1</em>", t)
    t = re.sub(r"\[([^\]]+)\]\((https?://[^)\s]+)\)", r'<a href="\2" target="_blank" rel="noopener">\1</a>', t)
    return re.sub(r"\x00(\d+)\x00", lambda m: f"<code>{codes[int(m.group(1))]}</code>", t)


def render_md(src: str) -> tuple[str, str]:
    """Devolve (titulo, html). H1 vira título; H2..H4 descem um nível."""
    lines = src.replace("\r\n", "\n").split("\n")
    out, title, i = [], "", 0
    para: list[str] = []

    def flush():
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>")
            para.clear()

    while i < len(lines):
        ln = lines[i]
        if ln.startswith("```"):
            flush()
            buf = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            out.append("<pre><code>" + html.escape("\n".join(buf), quote=False) + "</code></pre>")
            i += 1
            continue
        m = re.match(r"^(#{1,4})\s+(.*)$", ln)
        if m:
            flush()
            lvl, txt = len(m.group(1)), m.group(2).strip()
            if lvl == 1 and not title:
                title = re.sub(r"[`*]", "", txt)
            else:
                lv = min(lvl + 1, 5)
                out.append(f"<h{lv}>{inline(txt)}</h{lv}>")
            i += 1
            continue
        if re.match(r"^\s*\|.*\|\s*$", ln) and i + 1 < len(lines) and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            flush()
            cells = lambda s: [c.strip() for c in s.strip().strip("|").split("|")]
            head = cells(ln)
            i += 2
            rows = []
            while i < len(lines) and re.match(r"^\s*\|.*\|\s*$", lines[i]):
                rows.append(cells(lines[i]))
                i += 1
            t = "<div class=\"tw\"><table><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr>"
            for r in rows:
                t += "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>"
            out.append(t + "</table></div>")
            continue
        m = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", ln)
        if m:
            flush()
            ordered = m.group(2)[0].isdigit()
            tag = "ol" if ordered else "ul"
            items = []
            while i < len(lines):
                mm = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", lines[i])
                if not mm:
                    break
                items.append(("&nbsp;&nbsp;" if len(mm.group(1)) >= 2 else "") + inline(mm.group(3)))
                i += 1
            out.append(f"<{tag}>" + "".join(f"<li>{x}</li>" for x in items) + f"</{tag}>")
            continue
        if ln.startswith(">"):
            flush()
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(lines[i].lstrip("> ").rstrip())
                i += 1
            out.append('<div class="note">' + inline(" ".join(buf)) + "</div>")
            continue
        if re.match(r"^\s*(-{3,}|\*{3,})\s*$", ln):
            flush()
            out.append("<hr>")
            i += 1
            continue
        if not ln.strip():
            flush()
        else:
            para.append(ln.strip())
        i += 1
    flush()
    return title, "\n".join(out)


def generated_block(landi: Path) -> str:
    sha, day = subprocess.run(
        ["git", "-C", str(landi), "log", "-1", "--format=%h|%cs"], capture_output=True, text=True, check=True
    ).stdout.strip().split("|")
    docs = sorted(p for p in landi.glob("[0-9][0-9]-*.md"))
    parts = [
        '<h2 id="ref">Referência atual do repositório</h2>',
        f'<div class="note"><strong>Gerado do repositório PROTOCOLO-LANDI</strong>, commit <code>{sha}</code> de {day}, '
        f"{len(docs)} documentos. Esta parte é refeita a cada atualização do repositório; o texto acima é escrito à mão. "
        "Se algo divergir, vale o repositório.</div>",
        '<div class="toc"><div class="toc-title">Documentos</div><ol>',
    ]
    bodies = []
    for p in docs:
        title, body = render_md(p.read_text(encoding="utf-8"))
        slug = p.stem.split("-")[0]
        label = title or p.stem
        parts.append(f'<li><a href="#ref-{slug}">{html.escape(label, quote=False)}</a></li>')
        bodies.append(
            f'<details id="ref-{slug}"><summary>{html.escape(label, quote=False)} '
            f'<span class="file-badge">{html.escape(p.name, quote=False)}</span></summary><div class="md">{body}</div></details>'
        )
    parts.append("</ol></div>")
    return "\n".join(parts + bodies)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--landi", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    a = ap.parse_args()
    tpl = (HERE / "template.html").read_text(encoding="utf-8")
    if tpl.count(BEGIN) != 1 or tpl.count(END) != 1:
        print("template.html precisa de exatamente um par de marcadores GEN", file=sys.stderr)
        return 1
    head, rest = tpl.split(BEGIN)
    tail = rest.split(END)[1]
    a.out.write_text(head + BEGIN + "\n" + generated_block(a.landi) + "\n" + END + tail, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
