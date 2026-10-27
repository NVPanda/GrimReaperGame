import os
import time
import json
import argparse
from io import BytesIO
import numpy as np
from PIL import Image
from google import genai
from google.genai import types
from google.genai.errors import APIError

BAKIN_STYLE_PROMPT = (
    "authentic 2D JRPG pixel art asset in the exact visual style of RPG Developer Bakin "
    "(SmileBoom / Vibrato HD-2D pixel pack aesthetic). Crisp pixel edges, selective dark outlines, "
    "vibrant high-contrast 32-bit JRPG color palette, expressive anime-proportioned sprite, "
    "designed as a 2D billboard sprite on a solid flat magenta (#FF00FF) background, "
    "no anti-aliasing on outer edges, no soft gradients, clean pixel grid."
)

class KeyPool:
    """Gerencia uma ou múltiplas chaves Gemini para evitar 429 Too Many Requests."""
    def __init__(self):
        raw_keys = os.environ.get("GEMINI_API_KEYS") or os.environ.get("GEMINI_API_KEY", "")
        self.keys = [k.strip() for k in raw_keys.split(",") if k.strip()]
        if not self.keys:
            raise ValueError(
                "Nenhuma chave encontrada! Defina GEMINI_API_KEY ou GEMINI_API_KEYS='chave1,chave2'"
            )
        self.index = 0

    def get_client(self) -> genai.Client:
        key = self.keys[self.index % len(self.keys)]
        self.index += 1
        return genai.Client(api_key=key)

def remove_magenta_bg(img: Image.Image, tolerance: int = 45) -> Image.Image:
    img = img.convert("RGBA")
    data = np.array(img)
    r, g, b, _ = data[:, :, 0], data[:, :, 1], data[:, :, 2], data[:, :, 3]
    mask = (r > 255 - tolerance) & (g < tolerance) & (b > 255 - tolerance)
    data[mask] = [0, 0, 0, 0]
    return Image.fromarray(data, "RGBA")

def pixelate_and_quantize(img: Image.Image, target_w: int, target_h: int, colors: int = 32) -> Image.Image:
    small = img.resize((target_w, target_h), resample=Image.Resampling.NEAREST)
    alpha = small.split()[-1]
    rgb = small.convert("RGB").quantize(colors=colors, method=Image.Quantize.FASTOCTREE).convert("RGBA")
    rgb.putalpha(alpha.point(lambda p: 255 if p > 128 else 0))
    return rgb

def generate_normal_map(img: Image.Image) -> Image.Image:
    gray = img.convert("L")
    arr = np.asarray(gray, dtype=np.float32) / 255.0
    gx = np.gradient(arr, axis=1)
    gy = np.gradient(arr, axis=0)
    nz = np.ones_like(arr) * 0.5
    length = np.sqrt(gx**2 + gy**2 + nz**2)
    nx = ((-gx / length) * 0.5 + 0.5) * 255.0
    ny = ((-gy / length) * 0.5 + 0.5) * 255.0
    nz = ((nz / length) * 0.5 + 0.5) * 255.0
    alpha = np.asarray(img.split()[-1])
    normal_rgba = np.stack([nx, ny, nz, alpha], axis=-1).astype(np.uint8)
    return Image.fromarray(normal_rgba, "RGBA")

def call_with_backoff(pool: KeyPool, full_prompt: str, aspect_ratio: str, max_retries: int = 6):
    """Executa a chamada à API com Exponential Backoff e troca de chave em caso de erro 429."""
    base_wait = 15
    for attempt in range(1, max_retries + 1):
        try:
            client = pool.get_client()
            return client.models.generate_images(
                model="imagen-3.0-generate-002",
                prompt=full_prompt,
                config=types.GenerateImagesConfig(
                    number_of_images=1,
                    output_mime_type="image/png",
                    aspect_ratio=aspect_ratio,
                ),
            )
        except APIError as e:
            if e.code == 429 or "RESOURCE_EXHAUSTED" in str(e) or "Too Many Requests" in str(e):
                wait_time = base_wait * (2 ** (attempt - 1))
                print(f"    [!] Limite atingido (429). Tentativa {attempt}/{max_retries}. Aguardando {wait_time}s e rotacionando chave...")
                time.sleep(wait_time)
            else:
                raise e
    raise RuntimeError("Número máximo de tentativas excedido após múltiplos erros 429.")

def generate_asset(pool: KeyPool, item: dict, output_dir: str, delay_seconds: float, force: bool = False):
    name = item["name"]
    os.makedirs(output_dir, exist_ok=True)
    out_path = os.path.join(output_dir, f"{name}.png")

    # Pula se o asset já existir para economizar cota da API
    if os.path.exists(out_path) and not force:
        print(f"[~] Pulando '{name}' (já existe em {out_path})")
        return

    category = item.get("category", "prop")
    resolutions = {
        "character_standard": (120, 160),
        "character_hd": (240, 320),
        "prop": (80, 80),
        "prop_large": (160, 160),
        "tileset": (320, 320),
        "icon": (40, 40),
    }
    target_w, target_h = resolutions.get(category, (80, 80))
    if "width" in item and "height" in item:
        target_w, target_h = item["width"], item["height"]

    full_prompt = f"{item['prompt']}, {BAKIN_STYLE_PROMPT}"
    aspect_ratio = "1:1" if target_w == target_h else "3:4"

    print(f"[*] Gerando '{name}' ({target_w}x{target_h}px)...")
    response = call_with_backoff(pool, full_prompt, aspect_ratio)

    for generated_image in response.generated_images:
        raw_img = Image.open(BytesIO(generated_image.image.image_bytes))
        clean_img = remove_magenta_bg(raw_img)
        pixel_img = pixelate_and_quantize(clean_img, target_w, target_h, colors=item.get("colors", 32))
        pixel_img.save(out_path)
        print(f"    [+] Salvo: {out_path}")

        if item.get("normal_map", True):
            normal_img = generate_normal_map(pixel_img)
            normal_img.save(os.path.join(output_dir, f"{name}_n.png"))

    # Pausa preventiva (Throttling) entre requisições
    if delay_seconds > 0:
        print(f"    [...] Respeitando limite de taxa: aguardando {delay_seconds:.1f}s...")
        time.sleep(delay_seconds)

def main():
    parser = argparse.ArgumentParser(description="Gerador Bakin Anti-429 com Backoff e Throttling")
    parser.add_argument("--manifest", required=True, help="Caminho para o JSON de assets")
    parser.add_argument("--out", default="./assets/bakin_pixelart", help="Pasta de destino")
    parser.add_argument("--rpm", type=int, default=4, help="Requisições por minuto máximas (Padrão seguro: 4)")
    parser.add_argument("--force", action="store_true", help="Regerar mesmo que o arquivo já exista")
    args = parser.parse_args()

    pool = KeyPool()
    delay = 60.0 / max(1, args.rpm)

    with open(args.manifest, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    assets = manifest.get("assets", [])
    print(f"=== Iniciando fila com {len(assets)} assets | Chaves ativas: {len(pool.keys)} | Ritmo: {args.rpm} RPM ===")

    for item in assets:
        sub_dir = os.path.join(args.out, item.get("folder", ""))
        generate_asset(pool, item, sub_dir, delay_seconds=delay, force=args.force)

if __name__ == "__main__":
    main()