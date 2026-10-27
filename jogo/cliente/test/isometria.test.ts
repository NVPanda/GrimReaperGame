import { describe, expect, it } from "vitest";
import { telaParaTile, tileParaTela } from "../src/isometria.js";

describe("conversão de coordenadas isométricas", () => {
  it("preserva os tiles no percurso mundo-tela-mundo", () => {
    for (let y = 0; y < 30; y += 1) {
      for (let x = 0; x < 40; x += 1) {
        const pixel = tileParaTela(x, y, 30);
        expect(telaParaTile(pixel.x, pixel.y, 30)).toEqual({ x, y });
      }
    }
  });

  it("posiciona tiles adjacentes em losango 2:1", () => {
    const origem = tileParaTela(10, 10, 30);
    const diagonal = tileParaTela(11, 10, 30);
    expect(diagonal.x - origem.x).toBe(16);
    expect(diagonal.y - origem.y).toBe(8);
  });
});
