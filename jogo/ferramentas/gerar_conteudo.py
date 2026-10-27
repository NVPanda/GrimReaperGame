#!/usr/bin/env python3
"""Gera o conteúdo base do jogo: dados em JSON, tilemaps e assets placeholder.

Uso, na raiz do projeto:
    python3 ferramentas/gerar_conteudo.py

Todo o conteúdo é original. Os valores de balanceamento estão em docs/balanceamento.md.
Os assets gerados são PLACEHOLDER: formas simples por código, para testar a engine.
Substitua por arte final mantendo os mesmos nomes de arquivo e o manifest.json.
Depois de editar qualquer JSON gerado, rode os testes de conteúdo (backend: gradle test).
"""

import json
import math
import random
import shutil
import subprocess
import sys
import zlib
from pathlib import Path

if sys.version_info.releaselevel != "final":
    uv = shutil.which("uv")
    if uv is None:
        raise SystemExit(
            "Este Python é uma versão alfa/beta e não pode carregar o Pillow com segurança. "
            "Instale o uv e execute novamente, ou rode o gerador com um Python estável."
        )
    raiz = Path(__file__).resolve().parent.parent
    resultado = subprocess.run([
        uv, "run", "--python", "3.14",
        "--with-requirements", str(raiz / "ferramentas" / "requirements.txt"),
        str(Path(__file__).resolve()), *sys.argv[1:],
    ], check=False)
    raise SystemExit(resultado.returncode)

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
DADOS = RAIZ / "dados"
ASSETS = RAIZ / "assets"
LARGURA_MAPA, ALTURA_MAPA = 40, 30

ATRIBUTOS = ["forca", "agilidade", "vitalidade", "inteligencia", "destreza", "sorte"]
RANKS = ["F", "E", "D", "C", "B", "A", "S", "SS"]
NOME_RANK = dict(zip(RANKS, ["Simples", "Bronze", "Ferro", "Aço", "Mithril", "Runa", "Estelar", "Primordial"]))
COR_RANK = dict(zip(RANKS, ["#8a8a8a", "#3fae4a", "#3b7de0", "#2fc4c4", "#9b59d0", "#ff8c1a", "#e0332e", "#f5c542"]))
VENDA_RANK_MC = dict(zip(RANKS, [2, 5, 12, 25, 50, 100, 220, 500]))
TAXA_DROP_PCT = {"F": 40, "E": 25, "D": 12, "C": 6, "B": 2.5, "A": 1, "S": 0.2}

CONTINENTES = [
    {"id": "humano", "nome": "Continente Humano", "ordem": 1, "nivel": [1, 18],
     "paleta": ["#4f7a3a", "#8a6a3a", "#3a5f8a"]},
    {"id": "elfico", "nome": "Continente Élfico", "ordem": 2, "nivel": [14, 30],
     "paleta": ["#2e6b4f", "#5ea88a", "#8fd3b0"]},
    {"id": "anao", "nome": "Continente Anão", "ordem": 3, "nivel": [24, 38],
     "paleta": ["#6b5a4a", "#9c8466", "#c9a86a"]},
    {"id": "driade", "nome": "Continente Dríades", "ordem": 4, "nivel": [30, 44],
     "paleta": ["#1f5e2a", "#4aa35a", "#a6d96a"]},
    {"id": "gelo", "nome": "Continente de Gelo", "ordem": 5, "nivel": [38, 50],
     "paleta": ["#a9d6e8", "#e8f6ff", "#5b8fb0"]},
    {"id": "demoniaco", "nome": "Continente Demoníaco", "ordem": 6, "nivel": [44, 50],
     "paleta": ["#2a0a0a", "#7a1c1c", "#ff6a1a"]},
]

# Classes: soma dos atributos base = 60 (regra de balanceamento). Crescimento soma 1.0.
CLASSES = [
    {"id": "espadachim", "nome": "Espadachim", "papel": "linha de frente", "cor": "#c0c0c0",
     "base": {"forca": 14, "agilidade": 8, "vitalidade": 14, "inteligencia": 4, "destreza": 12, "sorte": 8},
     "crescimento": {"forca": 0.35, "vitalidade": 0.35, "destreza": 0.2, "sorte": 0.1},
     "habilidades": [
         {"id": "golpe_cortante", "nome": "Golpe Cortante", "custo_sp": 5, "dano_pct": 120},
         {"id": "postura_ferro", "nome": "Postura de Ferro", "custo_sp": 8, "efeito": "defesa +20% por 10s"},
         {"id": "investida", "nome": "Investida", "custo_sp": 10, "dano_pct": 150, "efeito": "avança até 4 casas"}]},
    {"id": "arqueiro", "nome": "Arqueiro", "papel": "distância física", "cor": "#3fae4a",
     "base": {"forca": 8, "agilidade": 14, "vitalidade": 8, "inteligencia": 4, "destreza": 18, "sorte": 8},
     "crescimento": {"destreza": 0.45, "agilidade": 0.3, "vitalidade": 0.15, "sorte": 0.1},
     "habilidades": [
         {"id": "flecha_perfurante", "nome": "Flecha Perfurante", "custo_sp": 5, "dano_pct": 115},
         {"id": "chuva_setas", "nome": "Chuva de Setas", "custo_sp": 12, "dano_pct": 90, "efeito": "área 3x3"},
         {"id": "passo_leve", "nome": "Passo Leve", "custo_sp": 6, "efeito": "esquiva +15% por 8s"}]},
    {"id": "mago", "nome": "Mago", "papel": "dano mágico à distância", "cor": "#9b59d0",
     "base": {"forca": 4, "agilidade": 8, "vitalidade": 6, "inteligencia": 22, "destreza": 12, "sorte": 8},
     "crescimento": {"inteligencia": 0.6, "destreza": 0.2, "vitalidade": 0.1, "sorte": 0.1},
     "habilidades": [
         {"id": "bola_chamas", "nome": "Bola de Chamas", "custo_sp": 6, "dano_pct": 130, "elemento": "fogo"},
         {"id": "gelo_congelante", "nome": "Gelo Congelante", "custo_sp": 8, "dano_pct": 100, "efeito": "lentidão 30% por 4s"},
         {"id": "escudo_arcano", "nome": "Escudo Arcano", "custo_sp": 10, "efeito": "absorve dano igual a 40% da inteligência"}]},
    {"id": "healer", "nome": "Healer", "papel": "suporte e cura", "cor": "#f5f0d0",
     "base": {"forca": 4, "agilidade": 6, "vitalidade": 14, "inteligencia": 20, "destreza": 8, "sorte": 8},
     "crescimento": {"inteligencia": 0.5, "vitalidade": 0.3, "sorte": 0.1, "destreza": 0.1},
     "habilidades": [
         {"id": "cura_menor", "nome": "Cura Menor", "custo_sp": 6, "efeito": "cura 60% da inteligência"},
         {"id": "bencao", "nome": "Bênção", "custo_sp": 8, "efeito": "força e destreza +10% por 30s em aliado"},
         {"id": "purificar", "nome": "Purificar", "custo_sp": 10, "efeito": "remove efeitos negativos"}]},
    {"id": "ninja", "nome": "Ninja", "papel": "dano rápido e furtivo", "cor": "#333344",
     "base": {"forca": 10, "agilidade": 18, "vitalidade": 8, "inteligencia": 4, "destreza": 12, "sorte": 8},
     "crescimento": {"agilidade": 0.4, "forca": 0.25, "destreza": 0.2, "sorte": 0.15},
     "habilidades": [
         {"id": "lamina_sombria", "nome": "Lâmina Sombria", "custo_sp": 5, "dano_pct": 110},
         {"id": "fumaca", "nome": "Fumaça", "custo_sp": 9, "efeito": "some por 3s, sem alvo"},
         {"id": "golpe_duplo", "nome": "Golpe Duplo", "custo_sp": 8, "dano_pct": 2 * 70}]},
    {"id": "taekwon", "nome": "Taekwon", "papel": "chutes e mobilidade", "cor": "#e0a030",
     "base": {"forca": 12, "agilidade": 16, "vitalidade": 10, "inteligencia": 4, "destreza": 10, "sorte": 8},
     "crescimento": {"agilidade": 0.35, "forca": 0.35, "vitalidade": 0.2, "sorte": 0.1},
     "habilidades": [
         {"id": "chute_giratorio", "nome": "Chute Giratório", "custo_sp": 6, "dano_pct": 125, "efeito": "atinge área"},
         {"id": "sequencia_veloz", "nome": "Sequência Veloz", "custo_sp": 7, "dano_pct": 80, "efeito": "3 golpes"},
         {"id": "salto_alto", "nome": "Salto Alto", "custo_sp": 8, "efeito": "pula sobre obstáculos"}]},
    {"id": "jiujitsu", "nome": "Jiu-Jitsu", "papel": "controle e contra-ataque", "cor": "#2a6fb0",
     "base": {"forca": 12, "agilidade": 6, "vitalidade": 16, "inteligencia": 4, "destreza": 14, "sorte": 8},
     "crescimento": {"vitalidade": 0.35, "forca": 0.35, "destreza": 0.2, "sorte": 0.1},
     "habilidades": [
         {"id": "chave_braco", "nome": "Chave de Braço", "custo_sp": 7, "dano_pct": 95, "efeito": "imobiliza 2s"},
         {"id": "contra_ataque", "nome": "Contra-Ataque", "custo_sp": 8, "efeito": "devolve 100% do próximo golpe"},
         {"id": "estrangulamento", "nome": "Estrangulamento", "custo_sp": 10, "dano_pct": 60, "efeito": "dano por 4s"}]},
    {"id": "karateka", "nome": "Karateka", "papel": "golpes diretos", "cor": "#d04a2a",
     "base": {"forca": 14, "agilidade": 10, "vitalidade": 14, "inteligencia": 4, "destreza": 10, "sorte": 8},
     "crescimento": {"forca": 0.4, "vitalidade": 0.3, "agilidade": 0.2, "sorte": 0.1},
     "habilidades": [
         {"id": "soco_direto", "nome": "Soco Direto", "custo_sp": 5, "dano_pct": 118},
         {"id": "quebra_guarda", "nome": "Quebra de Guarda", "custo_sp": 8, "dano_pct": 90, "efeito": "reduz defesa do alvo 15%"},
         {"id": "respiracao", "nome": "Respiração Profunda", "custo_sp": 6, "efeito": "recupera 15% do SP"}]},
    {"id": "gunslinger", "nome": "Gunslinger", "papel": "tiro de precisão", "cor": "#8a6a3a",
     "base": {"forca": 6, "agilidade": 12, "vitalidade": 8, "inteligencia": 6, "destreza": 20, "sorte": 8},
     "crescimento": {"destreza": 0.45, "agilidade": 0.25, "sorte": 0.2, "vitalidade": 0.1},
     "habilidades": [
         {"id": "disparo_duplo", "nome": "Disparo Duplo", "custo_sp": 6, "dano_pct": 2 * 65},
         {"id": "tiro_certeiro", "nome": "Tiro Certeiro", "custo_sp": 9, "dano_pct": 170},
         {"id": "recarga_rapida", "nome": "Recarga Rápida", "custo_sp": 5, "efeito": "próximo tiro sem custo"}]},
    {"id": "lanceiro", "nome": "Lanceiro", "papel": "alcance e controle de área", "cor": "#a0b0c0",
     "base": {"forca": 16, "agilidade": 8, "vitalidade": 14, "inteligencia": 4, "destreza": 10, "sorte": 8},
     "crescimento": {"forca": 0.4, "vitalidade": 0.3, "destreza": 0.2, "sorte": 0.1},
     "habilidades": [
         {"id": "estocada", "nome": "Estocada", "custo_sp": 5, "dano_pct": 120, "efeito": "alcance 3 casas"},
         {"id": "varredura", "nome": "Varredura", "custo_sp": 8, "dano_pct": 85, "efeito": "área em arco"},
         {"id": "muralha_lanca", "nome": "Muralha de Lança", "custo_sp": 9, "efeito": "bloqueia 1 inimigo por 5s"}]},
]

# Monstros: id, nome, família, continente, nível, boss, forma, índice da cor, fraqueza
MONSTROS_DEF = [
    ("constructo_palha", "Constructo de Palha", "Constructos", "humano", 2, False, "retangulo", 0, "fogo"),
    ("lobo_planicie", "Lobo da Planície", "Bestas", "humano", 5, False, "triangulo", 1, "terra"),
    ("espantalho_vivo", "Espantalho Vivo", "Constructos", "humano", 10, False, "retangulo", 2, "fogo"),
    ("colosso_argila", "Colosso de Argila", "Constructos", "humano", 15, True, "retangulo", 1, "agua"),
    ("cervo_espectral", "Cervo Espectral", "Espíritos", "elfico", 15, False, "triangulo", 0, "luz"),
    ("sprite_lunar", "Sprite Lunar", "Espíritos", "elfico", 19, False, "orbe", 2, "trevas"),
    ("guardiao_galhos", "Guardião de Galhos", "Plantas", "elfico", 26, False, "planta", 0, "fogo"),
    ("rainha_silfide", "Rainha Sílfide", "Espíritos", "elfico", 30, True, "orbe", 2, "trevas"),
    ("gnomo_minerio", "Gnomo de Minério", "Golems", "anao", 24, False, "retangulo", 1, "agua"),
    ("golem_cobre", "Golem de Cobre", "Golems", "anao", 28, False, "retangulo", 2, "agua"),
    ("verme_pedra", "Verme de Pedra", "Vermes", "anao", 33, False, "blob", 1, "raio"),
    ("forjador_rochoso", "Forjador Rochoso", "Golems", "anao", 38, True, "retangulo", 0, "agua"),
    ("brotoide", "Brotoide", "Plantas", "driade", 30, False, "planta", 2, "fogo"),
    ("fungo_errante", "Fungo Errante", "Fungos", "driade", 34, False, "blob", 2, "fogo"),
    ("arvore_abrigo", "Árvore-Abrigo", "Plantas", "driade", 38, False, "planta", 0, "fogo"),
    ("mae_floresta", "Mãe da Floresta", "Plantas", "driade", 44, True, "planta", 2, "fogo"),
    ("gelido_menor", "Gélido Menor", "Gélidos", "gelo", 38, False, "orbe", 0, "fogo"),
    ("lobo_gelo", "Lobo de Gelo", "Bestas", "gelo", 42, False, "triangulo", 0, "fogo"),
    ("espirito_nevasca", "Espírito da Nevasca", "Espíritos", "gelo", 46, False, "orbe", 1, "fogo"),
    ("tirano_gelo", "Tirano de Gelo", "Gélidos", "gelo", 50, True, "retangulo", 0, "fogo"),
    ("ignis_lacaio", "Ígneo Lacaio", "Ígneos", "demoniaco", 44, False, "triangulo", 1, "agua"),
    ("abissal_cacador", "Abissal Caçador", "Abissais", "demoniaco", 46, False, "blob", 2, "luz"),
    ("demonio_cinzas", "Demônio de Cinzas", "Ígneos", "demoniaco", 48, False, "triangulo", 0, "agua"),
    ("senhor_abismo", "Senhor do Abismo", "Abissais", "demoniaco", 50, True, "blob", 2, "luz"),
]

# Materiais de coleta: um por continente, com rank crescente.
MATERIAIS = {
    "humano": ("fibra_palha", "Fibra de Palha", "F"),
    "elfico": ("erva_lunar", "Erva Lunar", "E"),
    "anao": ("minerio_cobre", "Minério de Cobre", "D"),
    "driade": ("seiva_viva", "Seiva Viva", "C"),
    "gelo": ("cristal_gelido", "Cristal Gélido", "B"),
    "demoniaco": ("brasa_abissal", "Brasa Abissal", "A"),
}
PEIXES = [("peixe_lunar", "Peixe Lunar", "E"), ("peixe_gelido", "Peixe Gélido", "B")]

# Cidades, campos e masmorras. Monstros são escolhidos pela faixa de nível de cada zona.
ZONAS = [
    {"id": "vila_central", "nome": "Aurivela", "cont": "humano", "tipo": "cidade", "nivel": [1, 18]},
    {"id": "vale_campos", "nome": "Vale dos Campos", "cont": "humano", "tipo": "campo", "nivel": [1, 8]},
    {"id": "planicie_ventos", "nome": "Planície dos Ventos", "cont": "humano", "tipo": "campo", "nivel": [6, 14]},
    {"id": "cripta_humana", "nome": "Cripta Humana", "cont": "humano", "tipo": "masmorra", "nivel": [10, 18]},
    {"id": "clareira_sagrada", "nome": "Nácaris", "cont": "elfico", "tipo": "cidade", "nivel": [14, 30]},
    {"id": "bosque_luar", "nome": "Bosque do Luar", "cont": "elfico", "tipo": "campo", "nivel": [14, 22]},
    {"id": "vale_galhos", "nome": "Vale dos Galhos", "cont": "elfico", "tipo": "campo", "nivel": [20, 28]},
    {"id": "arvore_silfide", "nome": "Árvore Sílfide", "cont": "elfico", "tipo": "masmorra", "nivel": [22, 30]},
    {"id": "forja_profunda", "nome": "Korvurna", "cont": "anao", "tipo": "cidade", "nivel": [24, 38]},
    {"id": "mina_superior", "nome": "Mina Superior", "cont": "anao", "tipo": "campo", "nivel": [24, 30]},
    {"id": "mina_profunda", "nome": "Mina Profunda", "cont": "anao", "tipo": "campo", "nivel": [30, 36]},
    {"id": "forja_antiga", "nome": "Forja Antiga", "cont": "anao", "tipo": "masmorra", "nivel": [34, 38]},
    {"id": "abrigo_raizes", "nome": "Nascenraiz", "cont": "driade", "tipo": "cidade", "nivel": [30, 44]},
    {"id": "bosque_vivo", "nome": "Bosque Vivo", "cont": "driade", "tipo": "campo", "nivel": [30, 36]},
    {"id": "pantano_sussurro", "nome": "Pântano dos Sussurros", "cont": "driade", "tipo": "campo", "nivel": [34, 40]},
    {"id": "raizes_profundas", "nome": "Raízes Profundas", "cont": "driade", "tipo": "masmorra", "nivel": [38, 44]},
    {"id": "posto_norte", "nome": "Nívoria", "cont": "gelo", "tipo": "cidade", "nivel": [38, 50]},
    {"id": "planicie_gelada", "nome": "Planície Gelada", "cont": "gelo", "tipo": "campo", "nivel": [38, 44]},
    {"id": "geleira_azul", "nome": "Geleira Azul", "cont": "gelo", "tipo": "campo", "nivel": [42, 48]},
    {"id": "fortaleza_gelo", "nome": "Fortaleza de Gelo", "cont": "gelo", "tipo": "masmorra", "nivel": [46, 50]},
    {"id": "portao_brasa", "nome": "Braseal", "cont": "demoniaco", "tipo": "cidade", "nivel": [44, 50]},
    {"id": "cinzas_vermelhas", "nome": "Cinzas Vermelhas", "cont": "demoniaco", "tipo": "campo", "nivel": [44, 47]},
    {"id": "abismo_negro", "nome": "Abismo Negro", "cont": "demoniaco", "tipo": "campo", "nivel": [46, 49]},
    {"id": "trono_abismo", "nome": "Trono do Abismo", "cont": "demoniaco", "tipo": "masmorra", "nivel": [48, 50]},
]

NOMES_NPC = {
    "humano": ("Hélio", "Marta", "Tomé"), "elfico": ("Ilaveth", "Corvino", "Sabra"),
    "anao": ("Vorn", "Halda", "Durnok"), "driade": ("Tanuk", "Ysa", "Olmo"),
    "gelo": ("Gorok", "Nívea", "Rhaskar"), "demoniaco": ("Malvek", "Zarith", "Ignaro"),
}
TIPO_COLETA = {"humano": "plantio", "elfico": "pesca", "anao": "mineracao",
               "driade": "plantio", "gelo": "pesca", "demoniaco": "mineracao"}


def escrever_json(nome, dados):
    DADOS.mkdir(parents=True, exist_ok=True)
    (DADOS / nome).write_text(json.dumps(dados, ensure_ascii=False, indent=2), encoding="utf-8")


def rank_por_nivel(nivel):
    for limite, rank in [(10, "F"), (18, "E"), (26, "D"), (34, "C"), (40, "B"), (45, "A"), (48, "S")]:
        if nivel < limite:
            return rank
    return "S"


def gerar_itens():
    itens = []
    for i, rank in enumerate(RANKS):
        mult = 1.4 if rank == "SS" else 1.2 if rank == "S" else 1.0
        bonus = round((3 + 4 * i) * mult)
        for slot, base, atributos in [
            ("arma", "Lâmina", {"forca": bonus, "destreza": bonus // 2}),
            ("armadura", "Armadura", {"vitalidade": bonus}),
            ("acessorio", "Amuleto", {"sorte": bonus, "inteligencia": bonus // 2}),
            ("anel", "Anel", {"sorte": bonus // 2, "agilidade": bonus // 2}),
            ("pingente", "Pingente", {"inteligencia": bonus, "vitalidade": bonus // 2}),
            ("botas", "Botas", {"agilidade": bonus, "vitalidade": bonus // 2}),
        ]:
            itens.append({"id": f"{slot}_{rank.lower()}", "nome": f"{base} {NOME_RANK[rank]}",
                          "tipo": "equipamento", "categoria_loot": "equipamento", "slot": slot, "rank": rank,
                          "atributos": atributos, "venda_mc": VENDA_RANK_MC[rank]})
    for cont in CONTINENTES:
        if cont["id"] in MATERIAIS:
            iid, nome, rank = MATERIAIS[cont["id"]]
            itens.append({"id": iid, "nome": nome, "tipo": "material", "slot": None,
                          "categoria_loot": "basicos" if rank in ("F", "E") else "intermediarios",
                          "rank": rank, "atributos": {}, "venda_mc": VENDA_RANK_MC[rank]})
    for iid, nome, rank in PEIXES:
        itens.append({"id": iid, "nome": nome, "tipo": "material", "slot": None,
                      "categoria_loot": "intermediarios", "rank": rank, "atributos": {},
                      "venda_mc": VENDA_RANK_MC[rank]})
    itens.extend([
        {"id": "marca_mercado_negro", "nome": "Marca do Mercado Sombrio", "tipo": "material",
         "slot": None, "rank": "S", "categoria_loot": "ranqueados", "atributos": {}, "venda_mc": 0},
        {"id": "insignia_cla_ss", "nome": "Insígnia de Acesso SS", "tipo": "chave",
         "slot": None, "rank": "SS", "categoria_loot": "ultra_raros", "atributos": {}, "venda_mc": 0},
        {"id": "nucleo_ranqueado", "nome": "Núcleo de Aprimoramento", "tipo": "material",
         "slot": None, "rank": "SS", "categoria_loot": "ranqueados_ultra_raros",
         "atributos": {}, "venda_mc": 0},
        {"id": "placa_cacada_mundi", "nome": "Placa de Caçada Mundi", "tipo": "quest",
         "slot": None, "rank": "A", "categoria_loot": "ranqueados", "atributos": {}, "venda_mc": 0},
    ])
    return itens


def gerar_jogabilidade(zonas, monstros):
    por_id = {monstro["id"]: monstro for monstro in monstros}
    masmorras = []
    for zona in zonas:
        if zona["tipo"] != "masmorra":
            continue
        chefe = next((por_id[mid] for mid in zona["monstros"] if por_id[mid]["boss"]), None)
        if chefe is None:
            continue
        masmorras.append({
            "id": zona["id"],
            "grau": "SS" if zona["id"] == "trono_abismo" else "A",
            "circulo_pk": {"centro": [20, 15], "raio": 8},
            "andares": [] if zona["id"] == "trono_abismo" else [
                {"nivel": nivel, "miniboss_id": chefe["id"], "chave_drop_pct": 0.1}
                for nivel in (10, 20, 30, 40, 50)
            ],
        })
    return {
        "nivel_maximo": 50,
        "bosses_mundi": {
            "intervalo_spawn_segundos": 3600,
            "retorno_apos_derrota_segundos": 18000,
            "vida": 3000,
            "regioes": [
                {"id": "guardiao_raizes", "nome": "Guardião das Raízes", "zona_id": "vale_campos",
                 "centro": [13, 13], "raio": 5, "cor": "#54b879", "forma": "planta"},
                {"id": "mariposa_lunar", "nome": "Mariposa Lunar", "zona_id": "bosque_luar",
                 "centro": [25, 12], "raio": 5, "cor": "#bb86f2", "forma": "blob"},
                {"id": "sentinela_ferro", "nome": "Sentinela de Ferro", "zona_id": "mina_superior",
                 "centro": [14, 20], "raio": 5, "cor": "#9da7a2", "forma": "retangulo"},
                {"id": "oraculo_brejo", "nome": "Oráculo do Brejo", "zona_id": "bosque_vivo",
                 "centro": [24, 16], "raio": 5, "cor": "#51a9a1", "forma": "orbe"},
                {"id": "urso_geada", "nome": "Urso da Geada Azul", "zona_id": "planicie_gelada",
                 "centro": [17, 13], "raio": 5, "cor": "#78c9eb", "forma": "blob"},
                {"id": "draco_cinzas", "nome": "Draco das Cinzas", "zona_id": "cinzas_vermelhas",
                 "centro": [21, 17], "raio": 5, "cor": "#e76b45", "forma": "triangulo"},
            ],
        },
        "pk": {
            "aviso_entrada": True,
            "dano_fora_da_area": "bloquear_e_registrar",
            "chance_drop_equipamento_pct": 5,
            "tempo_saque_segundos": 30,
            "tempo_saque_com_lag_segundos": 60,
            "pesos_rank_drop": TAXA_DROP_PCT | {"SS": 0.5},
        },
        "raid_ss": {
            "zona_id": "trono_abismo",
            "item_acesso": "insignia_cla_ss",
            "membros_obrigatorios": 10,
            "consumir_chave_ao_iniciar": True,
            "reembolsar_ao_abortar": False,
            "bonus_nivel_chefe": 10,
            "andar_chefe_final": 50,
        },
        "masmorras": masmorras,
        "loot": {
            "categorias": [
                "basicos", "intermediarios", "ranqueados", "ultra_raros",
                "ranqueados_ultra_raros",
            ],
            "grupo_maximo": 10,
            "drop_chave_miniboss_pct": 0.1,
        },
    }


def gerar_monstros():
    cont_por_id = {c["id"]: c for c in CONTINENTES}
    monstros = []
    slots = ["arma", "armadura", "acessorio"]
    for idx, (mid, nome, familia, cont, nivel, boss, forma, cor_idx, fraqueza) in enumerate(MONSTROS_DEF):
        c = cont_por_id[cont]
        fator_vida = 3 if boss else 1
        slot = slots[idx % 3]
        mat_id, _, mat_rank = MATERIAIS[cont]
        if boss:
            drops = [
                {"item": mat_id, "taxa_pct": 60},
                {"item": f"{slot}_{rank_por_nivel(nivel).lower()}", "taxa_pct": 15},
                {"item": f"{slot}_ss", "taxa_pct": 0.5},  # SS só de boss (regra de drops)
            ]
            if cont != "demoniaco":
                drops.extend([
                    {"item": "insignia_cla_ss", "taxa_pct": 0.1},
                    {"item": "nucleo_ranqueado", "taxa_pct": 0.1},
                ])
        else:
            drops = [
                {"item": mat_id, "taxa_pct": TAXA_DROP_PCT[mat_rank]},
                {"item": f"{slot}_{rank_por_nivel(nivel).lower()}", "taxa_pct": TAXA_DROP_PCT[rank_por_nivel(nivel)]},
                {"item": "marca_mercado_negro", "taxa_pct": 0.1},
            ]
        monstros.append({
            "id": mid, "nome": nome, "familia": familia, "continente": cont, "nivel": nivel,
            "boss": boss, "fraqueza": fraqueza,
            "atributos": {
                "vida": round((60 + 18 * nivel) * fator_vida),
                "ataque": round((4 + 1.6 * nivel) * (1.6 if boss else 1)),
                "defesa": round((2 + 0.9 * nivel) * (1.4 if boss else 1)),
                "xp": round((nivel ** 1.6) * (6 if boss else 1)),
            },
            "respawn_s": 900 if boss else 20,
            "cor": c["paleta"][cor_idx],
            "forma": forma,
            "drops": drops,
        })
    return monstros


def gerar_zonas(monstros):
    zonas = []
    for z in ZONAS:
        if z["tipo"] == "cidade":
            monstros_zona = []
            npcs = [f"npc_{z['cont']}_{papel}" for papel in ("mercador", "guia", "ferreiro")]
        else:
            monstros_zona = [m["id"] for m in monstros
                             if m["continente"] == z["cont"] and z["nivel"][0] <= m["nivel"] <= z["nivel"][1]
                             and (not m["boss"] or z["tipo"] == "masmorra")]
            npcs = []
        zonas.append({**z, "monstros": monstros_zona, "npcs": npcs})
    return zonas


def gerar_npcs():
    npcs = []
    for cont, (mercador, guia, ferreiro) in NOMES_NPC.items():
        zona = next(z["id"] for z in ZONAS if z["cont"] == cont and z["tipo"] == "cidade")
        for papel, nome, funcao in [("mercador", mercador, "loja"), ("guia", guia, "missoes"),
                                    ("ferreiro", ferreiro, "forja")]:
            npcs.append({"id": f"npc_{cont}_{papel}", "nome": f"{nome} ({papel})", "continente": cont,
                         "zona": zona, "funcao": funcao})
    return npcs


def gerar_quests(zonas, monstros):
    quests = []
    for c in CONTINENTES:
        cont = c["id"]
        ordem = c["ordem"]
        guia = f"npc_{cont}_guia"
        campos = [z for z in zonas if z["cont"] == cont and z["tipo"] == "campo"]
        masmorra = next(z for z in zonas if z["cont"] == cont and z["tipo"] == "masmorra")
        normal = next(m for m in monstros if m["id"] in campos[0]["monstros"] and not m["boss"])
        boss = next(m for m in monstros if m["id"] in masmorra["monstros"] and m["boss"])
        mat_id = MATERIAIS[cont][0] if cont in MATERIAIS else None
        coleta_item = mat_id
        if cont == "elfico":
            coleta_item = "peixe_lunar"
        elif cont == "gelo":
            coleta_item = "peixe_gelido"
        quests.append({
            "id": f"{cont}_caca_01", "nome": f"Caça em {campos[0]['nome']}", "continente": cont,
            "npc": guia, "tipo": "caca", "zona": campos[0]["id"], "alvo": normal["id"],
            "quantidade": 10, "nivel_minimo": campos[0]["nivel"][0], "recompensa_mc": 30 * ordem,
            "dialogos": {
                "inicio": f"Os {normal['nome']}s estão tomando {campos[0]['nome']}. Elimine 10 deles e volte.",
                "andamento": "Ainda restam criaturas por lá.",
                "entrega": "Bom trabalho. Aqui está sua recompensa.",
            },
        })
        quests.append({
            "id": f"{cont}_coleta_01", "nome": f"Coleta em {campos[-1]['nome']}", "continente": cont,
            "npc": guia, "tipo": TIPO_COLETA[cont], "zona": campos[-1]["id"], "alvo": coleta_item,
            "quantidade": 5, "nivel_minimo": campos[-1]["nivel"][0], "recompensa_mc": 20 * ordem,
            "dialogos": {
                "inicio": f"Preciso de 5 unidades de {coleta_item.replace('_', ' ')}. Procure em {campos[-1]['nome']}.",
                "andamento": "Continue coletando.",
                "entrega": "Ótimo. Isso vai ajudar muito.",
            },
        })
        quests.append({
            "id": f"{cont}_masmorra_01", "nome": f"Chefe de {masmorra['nome']}", "continente": cont,
            "npc": guia, "tipo": "masmorra", "zona": masmorra["id"], "alvo": boss["id"],
            "quantidade": 1, "nivel_minimo": masmorra["nivel"][0], "recompensa_mc": 120 * ordem,
            "dialogos": {
                "inicio": f"{boss['nome']} domina {masmorra['nome']}. Derrote-o e traga a prova.",
                "andamento": "A masmorra ainda não foi limpa.",
                "entrega": "Você venceu. Essa região está mais segura.",
            },
        })
    return quests


def gerar_tiles(caminho, paleta, semente):
    cores = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for h in paleta]
    rng = random.Random(semente)
    img = Image.new("RGBA", (32 * 8, 32), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    for i in range(8):
        base = cores[i % len(cores)]
        if i >= len(cores):
            base = tuple(min(255, v + 25) for v in base[:3]) + (255,)
        if i >= 6:
            base = tuple(int(v * 0.6) for v in base[:3]) + (255,)
        x0 = i * 32
        d.rectangle([x0, 0, x0 + 31, 31], fill=base)
        for _ in range(20):
            d.point((x0 + rng.randrange(32), rng.randrange(32)),
                    fill=tuple(int(v * 0.85) for v in base[:3]) + (255,))
    img.save(caminho)


def hex_rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def escurecer(rgba, f=0.6):
    return (int(rgba[0] * f), int(rgba[1] * f), int(rgba[2] * f), rgba[3])


def clarear(rgba, f=0.35):
    return tuple(min(255, int(v + (255 - v) * f)) if i < 3 else v for i, v in enumerate(rgba))


def caixa(x1, y1, x2, y2):
    return [int(x1), int(y1), int(x2), int(y2)]


def gerar_monstro(caminho, cor, forma, boss, quadros=4, tam=64):
    base = hex_rgba(cor)
    escuro = escurecer(base)
    img = Image.new("RGBA", (tam * quadros, tam), (0, 0, 0, 0))
    for q in range(quadros):
        d = ImageDraw.Draw(img)
        ox = q * tam
        bob = round(3 * math.sin(q * math.pi / 2))
        r = 26 if boss else 20
        cx, cy = ox + tam // 2, tam // 2 + 6 + bob
        if forma == "orbe":
            d.ellipse(caixa(cx - r, cy - r, cx + r, cy + r), fill=base, outline=escuro, width=2)
        elif forma == "triangulo":
            d.polygon([(cx, cy - r), (cx + r, cy + r), (cx - r, cy + r)], fill=base, outline=escuro)
        elif forma == "retangulo":
            d.rounded_rectangle(caixa(cx - r, cy - r, cx + r, cy + r), radius=6, fill=base, outline=escuro, width=2)
        elif forma == "planta":
            d.ellipse(caixa(cx - r * 0.8, cy - r * 0.6, cx + r * 0.8, cy + r * 0.9), fill=base, outline=escuro, width=2)
            for dx in (-14, 0, 14):
                d.ellipse(caixa(cx + dx - 5, cy - r - 4, cx + dx + 5, cy - r + 6), fill=clarear(base), outline=escuro)
        else:  # blob
            d.ellipse(caixa(cx - r * 1.1, cy - r * 0.7, cx + r * 1.1, cy + r * 0.9), fill=base, outline=escuro, width=2)
            for dx in (-r * 0.6, 0, r * 0.6):
                d.ellipse(caixa(cx + dx - 6, cy + r * 0.5, cx + dx + 6, cy + r * 0.9), fill=base)
        ey = cy - 4
        for ex in (cx - 7, cx + 7):
            d.ellipse(caixa(ex - 4, ey - 4, ex + 4, ey + 4), fill=(255, 255, 255, 255), outline=escuro)
            px = ex + (1 if q % 2 else -1)
            d.ellipse(caixa(px - 2, ey - 2, px + 2, ey + 2), fill=(20, 20, 20, 255))
        if boss:
            for dx in (-14, 0, 14):
                d.polygon([(cx + dx - 5, cy - r + 2), (cx + dx, cy - r - 10), (cx + dx + 5, cy - r + 2)],
                          fill=(245, 197, 66, 255), outline=escuro)
    img.save(caminho)


def gerar_personagem(caminho, cor, quadros=4, tam=48):
    base = hex_rgba(cor)
    escuro = escurecer(base)
    img = Image.new("RGBA", (tam * quadros, tam), (0, 0, 0, 0))
    for q in range(quadros):
        d = ImageDraw.Draw(img)
        cx = q * tam + tam // 2
        perna = 3 if q % 2 else -3
        d.ellipse(caixa(cx - 7, 6, cx + 7, 20), fill=(240, 200, 160, 255), outline=escuro)
        d.rounded_rectangle(caixa(cx - 8, 20, cx + 8, 36), radius=4, fill=base, outline=escuro)
        d.rectangle(caixa(cx - 6 + perna, 36, cx - 2 + perna, 46), fill=escuro)
        d.rectangle(caixa(cx + 2 - perna, 36, cx + 6 - perna, 46), fill=escuro)
        d.line([(cx + 10, 22), (cx + 16, 40)], fill=(200, 200, 210, 255), width=2)
    img.save(caminho)


def gerar_icone(caminho, rank, slot):
    cor = hex_rgba(COR_RANK[rank])
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(caixa(2, 2, 29, 29), radius=5, fill=(30, 32, 40, 255), outline=cor, width=2)
    if slot == "arma":
        d.line([(9, 23), (23, 9)], fill=cor, width=3)
    elif slot == "armadura":
        d.polygon([(16, 7), (24, 10), (22, 23), (16, 26), (10, 23), (8, 10)], fill=cor)
    elif slot == "acessorio":
        d.ellipse(caixa(10, 10, 22, 22), outline=cor, width=3)
    elif slot is None:
        d.polygon([(16, 7), (24, 16), (16, 25), (8, 16)], fill=cor)
    else:
        d.ellipse(caixa(7, 11, 25, 21), fill=cor)
    img.save(caminho)


def gerar_tilemap(zona, semente):
    rng = random.Random(semente)
    linhas = []
    for y in range(ALTURA_MAPA):
        linha = []
        for x in range(LARGURA_MAPA):
            if zona["tipo"] == "cidade":
                tile = 1 if (x == LARGURA_MAPA // 2 or y == ALTURA_MAPA // 2) else 0
            elif zona["tipo"] == "masmorra":
                borda = x in (0, LARGURA_MAPA - 1) or y in (0, ALTURA_MAPA - 1)
                tile = 6 if borda or rng.random() < 0.1 else 7
            else:
                tile = rng.choices([0, 1, 2, 3], weights=[60, 20, 10, 10])[0]
            linha.append(tile)
        linhas.append(linha)
    return linhas


def main():
    for pasta in ["dados/tilemaps", "assets/monstros", "assets/personagens", "assets/tiles", "assets/itens"]:
        (RAIZ / pasta).mkdir(parents=True, exist_ok=True)

    escrever_json("continentes.json", [{k: v for k, v in c.items() if k != "paleta"} | {"paleta": c["paleta"]}
                                       for c in CONTINENTES])
    escrever_json("classes.json", CLASSES)
    itens = gerar_itens()
    escrever_json("itens.json", itens)
    monstros = gerar_monstros()
    escrever_json("monstros.json", monstros)
    zonas = gerar_zonas(monstros)
    escrever_json("mapas.json", zonas)
    escrever_json("npcs.json", gerar_npcs())
    escrever_json("quests.json", gerar_quests(zonas, monstros))
    jogabilidade = gerar_jogabilidade(zonas, monstros)
    escrever_json("jogabilidade.json", jogabilidade)
    escrever_json("drops.json", {
        "taxas_por_rank_pct": TAXA_DROP_PCT,
        "regras": [
            "Drops são calculados por monstro, individualmente, sem chance de NFT ou item pago.",
            "Item de rank SS só cai de boss (ADR 0003 e SDD 06).",
            "Em grupo, o nível de referência é o maior nível do grupo para liberar ranks acima do monstro.",
            "Marca do Mercado Sombrio cai de monstros comuns; insígnia de acesso e núcleo de aprimoramento caem dos chefes menores das masmorras.",
        ],
        "taxa_ss_boss_pct": 0.5,
    })
    escrever_json("economia.json", {
        "moeda": {"nome": "Mitrum Coin", "abreviacao": "MC"},
        "venda_npc_mc_por_rank": VENDA_RANK_MC,
        "tarifas_teleporte_mc": {
            "cidade_mesmo_continente": 100,
            "cidade_intercontinental": 1_000,
            "masmorra": 10_000,
            "masmorra_ss": 100_000,
            "zona_masmorra_ss": "trono_abismo",
        },
        "fontes_de_mc": ["quests", "venda de itens ao NPC mercador"],
        "restricoes": [
            "MC não é convertível em dinheiro real.",
            "MC não é negociável fora do jogo.",
            "Itens com chance de ganho pago não existem (ADR 0003).",
        ],
    })

    manifesto = {"versao": 2, "monstros": {}, "personagens": {}, "tiles": {}, "itens": {}}
    for m in monstros:
        caminho = ASSETS / "monstros" / f"{m['id']}.png"
        gerar_monstro(caminho, m["cor"], m["forma"], m["boss"])
        manifesto["monstros"][m["id"]] = {"arquivo": f"assets/monstros/{m['id']}.png", "quadros": 4, "tamanho": [64, 64]}
    for boss in jogabilidade["bosses_mundi"]["regioes"]:
        caminho = ASSETS / "monstros" / f"{boss['id']}.png"
        gerar_monstro(caminho, boss["cor"], boss["forma"], True)
        manifesto["monstros"][boss["id"]] = {
            "arquivo": f"assets/monstros/{boss['id']}.png",
            "quadros": 4,
            "tamanho": [64, 64],
        }
    for c in CLASSES:
        caminho = ASSETS / "personagens" / f"{c['id']}.png"
        gerar_personagem(caminho, c["cor"])
        manifesto["personagens"][c["id"]] = {"arquivo": f"assets/personagens/{c['id']}.png", "quadros": 4, "tamanho": [48, 48]}
    for cont in CONTINENTES:
        caminho = ASSETS / "tiles" / f"{cont['id']}.png"
        gerar_tiles(caminho, cont["paleta"], zlib.crc32(cont["id"].encode()))
        manifesto["tiles"][cont["id"]] = {"arquivo": f"assets/tiles/{cont['id']}.png", "quantidade": 8, "tamanho": [32, 32]}
    for item in itens:
        caminho = ASSETS / "itens" / f"{item['id']}.png"
        gerar_icone(caminho, item["rank"], item["slot"])
        manifesto["itens"][item["id"]] = {"arquivo": f"assets/itens/{item['id']}.png", "tamanho": [32, 32]}
    (ASSETS / "manifest.json").write_text(json.dumps(manifesto, ensure_ascii=False, indent=2), encoding="utf-8")

    for z in zonas:
        semente = zlib.crc32(z["id"].encode())
        mapa = {"zona": z["id"], "continente": z["cont"], "tipo": z["tipo"],
                "largura": LARGURA_MAPA, "altura": ALTURA_MAPA, "tiles": gerar_tilemap(z, semente)}
        (DADOS / "tilemaps" / f"{z['id']}.json").write_text(json.dumps(mapa), encoding="utf-8")

    quests = json.loads((DADOS / "quests.json").read_text(encoding="utf-8"))
    print(f"Gerado: {len(CONTINENTES)} continentes, {len(CLASSES)} classes, {len(monstros)} monstros, "
          f"{len(itens)} itens, {len(zonas)} zonas, {len(quests)} quests, {len(zonas)} tilemaps.")


if __name__ == "__main__":
    main()
