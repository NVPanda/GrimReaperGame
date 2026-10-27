import os
import json
import subprocess
from google import genai
from google.genai import types

def run_fable_pipeline(goal: str):
    client = genai.Client(api_key=os.environ.get("GEMINI_API_KEY"))

    print("[1/3] 🧠 Fable-Core (Gemini 2.5 Pro): Projetando arquitetura e manifesto Bakin...")
    plan_prompt = f"""
    Você é o núcleo arquiteto Fable. Objetivo do projeto de jogo: {goal}
    Retorne APENAS um JSON válido com duas chaves:
    1. "files": lista de objetos {{"path": "caminho/arquivo", "content": "codigo completo"}}
    2. "bakin_manifest": objeto no formato do bakin_manifest.json com a chave "assets" (categorias: character_hd, prop, icon).
    """
    plan_resp = client.models.generate_content(
        model="gemini-2.5-pro",
        contents=plan_prompt,
        config=types.GenerateContentConfig(response_mime_type="application/json"),
    )
    data = json.loads(plan_resp.text)

    print("[2/3] ⚡ Fable-Coder: Escrevendo arquivos de código do jogo...")
    for file_info in data.get("files", []):
        os.makedirs(os.path.dirname(file_info["path"]) or ".", exist_ok=True)
        with open(file_info["path"], "w", encoding="utf-8") as f:
            f.write(file_info["content"])
        print(f"    [+] Código gerado: {file_info['path']}")

    print("[3/3] 🎨 Fable-Bakin: Salvando manifesto e gerando Pixel Art HD-2D...")
    os.makedirs("assets", exist_ok=True)
    manifest_path = "assets/bakin_manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(data.get("bakin_manifest", {"assets": []}), f, indent=2, ensure_ascii=False)

    subprocess.run([
        "python", "tools/bakin_asset_generator.py",
        "--manifest", manifest_path,
        "--out", "assets/bakin_pixelart",
        "--rpm", "4"
    ], check=True)

if __name__ == "__main__":
    import sys
    objetivo = " ".join(sys.argv[1:]) or "Criar sistema de exploração 2.5D com herói espadachim, baú interativo e poste de luz"
    run_fable_pipeline(objetivo)