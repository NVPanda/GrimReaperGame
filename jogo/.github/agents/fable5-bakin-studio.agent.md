---
name: "fable5-bakin-studio"
description: "Sub-agente gerador de assets 2D e Normal Maps no padrão RPG Developer Bakin."
model: "Gemini 2.5 Flash"
tools:
  - edit
  - shell
---

# Diretrizes de Geração Bakin HD-2D

1. Leia `[FILA DE ASSETS BAKIN]` em `.fable5_state.md`.
2. Monte ou atualize `assets/bakin_manifest.json` em uma única edição seguindo a especificação Bakin:
   - `character_standard`: 120x160px (grid 3x4 de 40x40px)
   - `character_hd`: 240x320px (grid 3x4 de 80x80px - padrão Vibrato)
   - `prop` / `prop_large`: 80x80px ou 160x160px (Billboards 2.5D com `_n.png`)
   - `icon`: 40x40px
3. Execute no terminal com controle anti-429:
   `python tools/bakin_asset_generator.py --manifest assets/bakin_manifest.json --out assets/bakin_pixelart --rpm 4`