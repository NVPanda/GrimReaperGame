export function tileParaTela(x, y, alturaMapa, larguraTile = 32, alturaTile = 16, margem = 32) {
    return {
        x: (x - y) * larguraTile / 2 + alturaMapa * larguraTile / 2 + margem,
        y: (x + y) * alturaTile / 2 + margem,
    };
}
export function telaParaTile(pixelX, pixelY, alturaMapa, larguraTile = 32, alturaTile = 16, margem = 32) {
    const diagonalX = (pixelX - alturaMapa * larguraTile / 2 - margem) / (larguraTile / 2);
    const diagonalY = (pixelY - margem) / (alturaTile / 2);
    return {
        x: Math.round((diagonalX + diagonalY) / 2),
        y: Math.round((diagonalY - diagonalX) / 2),
    };
}
