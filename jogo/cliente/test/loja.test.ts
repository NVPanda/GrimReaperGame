import { describe, expect, it } from "vitest";
import { formatarPrecoBRL } from "../src/loja";

describe("formatarPrecoBRL", () => {
  it("LOJA-06 formata centavos como reais", () => {
    // Intl usa espaço não separável após o símbolo; comparamos só o valor.
    expect(formatarPrecoBRL(1990)).toContain("19,90");
  });

  it("formata valores de um real ou menos", () => {
    expect(formatarPrecoBRL(990)).toContain("9,90");
  });
});
