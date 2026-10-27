export interface Ponto {
  x: number;
  y: number;
}

export function tileParaTela(
  x: number,
  y: number,
  alturaMapa: number,
  larguraTile = 32,
  alturaTile = 16,
  margem = 32,
): Ponto {
  return {
    x: (x - y) * larguraTile / 2 + alturaMapa * larguraTile / 2 + margem,
    y: (x + y) * alturaTile / 2 + margem,
  };
}

export function telaParaTile(
  pixelX: number,
  pixelY: number,
  alturaMapa: number,
  larguraTile = 32,
  alturaTile = 16,
  margem = 32,
): Ponto {
  const diagonalX = (pixelX - alturaMapa * larguraTile / 2 - margem) / (larguraTile / 2);
  const diagonalY = (pixelY - margem) / (alturaTile / 2);
  return {
    x: Math.round((diagonalX + diagonalY) / 2),
    y: Math.round((diagonalY - diagonalX) / 2),
  };
}
