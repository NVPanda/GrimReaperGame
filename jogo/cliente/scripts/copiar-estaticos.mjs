// Copia páginas, dados e assets para dist/, para o build ficar autocontido.
import { cpSync, copyFileSync, mkdirSync } from "node:fs";

mkdirSync("dist", { recursive: true });
copyFileSync("src/index.html", "dist/index.html");
copyFileSync("src/jogo.html", "dist/jogo.html");
copyFileSync("src/personagens.html", "dist/personagens.html");
copyFileSync("src/nascimento.html", "dist/nascimento.html");
copyFileSync("src/mapa-mundi.html", "dist/mapa-mundi.html");
copyFileSync("src/theme.css", "dist/theme.css");
copyFileSync("src/runtime-config.js", "dist/runtime-config.js");
cpSync("../dados", "dist/dados", { recursive: true });
cpSync("../assets", "dist/assets", { recursive: true });
console.log("estáticos copiados para dist/");
