import { describe, expect, it } from "vitest";
import { formatarPrecoBRL } from "../src/loja";

describe("apresentação de preço", () => {
  it("LOJA-06 centavos viram reais com vírgula", () => {
    expect(formatarPrecoBRL(490)).toContain("4,90");
  });
});
