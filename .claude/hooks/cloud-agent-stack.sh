#!/usr/bin/env bash
# Pinned, idempotent provisioning for Anthropic-hosted Claude Code sessions.
# Runs only in ephemeral cloud sessions. Never reads secrets or changes project code.
set -Eeuo pipefail
if [[ "${CLAUDE_CODE_REMOTE:-}" != "true" ]]; then exit 0; fi

root="${HOME}/.cache/claude-agent-stack"
skill_root="${HOME}/.claude/skills"
mkdir -p "$root" "$skill_root" "${HOME}/.local/bin"

install_repo_skills() {
  local name="$1" url="$2" revision="$3" repo="$root/$1"
  if [[ ! -d "$repo/.git" ]]; then
    git clone --quiet --no-checkout --filter=blob:none "$url" "$repo"
  fi
  if [[ "$(git -C "$repo" rev-parse HEAD 2>/dev/null || true)" != "$revision" ]]; then
    git -C "$repo" fetch --quiet --depth=1 origin "$revision"
    git -C "$repo" checkout --quiet --detach "$revision"
  else
    git -C "$repo" checkout --quiet --detach "$revision"
  fi

  local path name_of_skill target
  for path in "$repo"/skills/*; do
    [[ -f "$path/SKILL.md" ]] || continue
    name_of_skill="$(basename "$path")"
    target="$skill_root/$name_of_skill"
    # Do not replace skills owned by the user or another installer.
    if [[ -e "$target" && ! -L "$target" ]]; then continue; fi
    if [[ -L "$target" && "$(readlink "$target")" == "$path" ]]; then continue; fi
    ln -sfn "$path" "$target"
  done
}

if command -v git >/dev/null 2>&1; then
  install_repo_skills ponytail https://github.com/DietrichGebert/ponytail.git 84740e3d344b69b4a133201fa519eb37a62a2b2a || echo "[agent-stack] Ponytail unavailable; continuing."
  install_repo_skills agent-skills https://github.com/addyosmani/agent-skills.git aeac41d4d005999432bb7312f7ff06383a91d59d || echo "[agent-stack] Agent Skills unavailable; continuing."
fi

# Graphify CLI is local to the cloud sandbox: no paid model or API requests.
if command -v python3 >/dev/null 2>&1; then
  if [[ ! -x "$root/graphify-venv/bin/graphify" ]]; then
    if python3 -m venv "$root/graphify-venv"; then
      "$root/graphify-venv/bin/python" -m pip install --disable-pip-version-check --no-input "graphifyy==0.9.91" || echo "[agent-stack] Graphify pip install failed."
    fi
  fi
  if [[ -x "$root/graphify-venv/bin/graphify" ]]; then
    ln -sfn "$root/graphify-venv/bin/graphify" "${HOME}/.local/bin/graphify"
    if [[ -n "${CLAUDE_ENV_FILE:-}" ]]; then
      if ! grep -Fq 'claude-agent-stack: PATH' "$CLAUDE_ENV_FILE" 2>/dev/null; then
        printf '\n# claude-agent-stack: PATH\nexport PATH="%s/.local/bin:$PATH"\n' "$HOME" >> "$CLAUDE_ENV_FILE"
      fi
    fi
  fi
fi

# OmniRoute remains on the authenticated gateway. Do not start an empty
# local router or expose credentials inside temporary cloud environments.
echo "[agent-stack] Cloud bootstrap complete (versions pinned; project untouched)."
