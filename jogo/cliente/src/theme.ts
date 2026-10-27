type Theme = "light" | "dark";

const STORAGE_KEY = "jogo.theme";
const root = document.documentElement;
const savedTheme = window.localStorage.getItem(STORAGE_KEY);
let theme: Theme = savedTheme === "light" ? "light" : "dark";

const button = document.createElement("button");
button.type = "button";
button.className = "theme-toggle";
button.addEventListener("click", () => {
  theme = theme === "dark" ? "light" : "dark";
  window.localStorage.setItem(STORAGE_KEY, theme);
  renderTheme();
});

function renderTheme(): void {
  const isLight = theme === "light";
  root.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute(
    "content",
    isLight ? "#f5ede3" : "#101318",
  );
  button.setAttribute("aria-pressed", String(isLight));
  button.setAttribute(
    "aria-label",
    isLight ? "Ativar tema escuro do panda cyberpunk" : "Ativar tema claro do panda vermelho cyberpunk",
  );
  button.textContent = isLight ? "◐ Panda · Escuro" : "☼ Panda-vermelho · Claro";
}

renderTheme();
document.querySelector("header")?.append(button);
