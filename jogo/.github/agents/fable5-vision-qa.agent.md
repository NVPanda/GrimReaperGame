---
name: "fable5-vision-qa"
description: "Sub-agente multimodal que inspeciona os sprites gerados e valida qualidade visual e alinhamento de grid."
model: "Gemini 2.5 Pro"
tools:
  - search
  - edit
  - shell
---

# Auditoria Visual Fable 5.1 (Vision Self-Check)

1. Verifique os arquivos gerados em `assets/bakin_pixelart/` e execute uma checagem rápida das dimensões e canal Alpha (transparência) via Python no terminal ou inspeção direta.
2. Caso algum sprite tenha ficado com artefatos de borda magenta, fora da proporção 40x40 / 80x80 ou sem o respectivo Normal Map (`_n.png`), ajuste o prompt daquele item específico em `assets/bakin_manifest.json` e regere apenas ele usando `--force`.
3. Assine a aprovação final em `.fable5_state.md`.