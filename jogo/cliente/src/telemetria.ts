/**
 * Envio de ações de movimento ao servidor.
 *
 * Este código NÃO é barreira de segurança: o jogador pode alterá-lo.
 * O servidor decide a posição final (ADR 0001). O cliente só envia intenções
 * e corrige a exibição quando o servidor recusa.
 */

export async function enviarMovimento(
  urlApi: string,
  token: string,
  x: number,
  y: number,
): Promise<boolean> {
  const resposta = await fetch(`${urlApi}/api/acao/mover`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ x, y, timestampMs: Date.now() }),
    credentials: "omit",
  });
  // 409: o servidor recusou. A posição exibida deve voltar à posição oficial.
  return resposta.ok;
}

export async function entrarNaZona(urlApi: string, token: string, zonaId: string): Promise<boolean> {
  const resposta = await fetch(`${urlApi}/api/acao/entrar-zona`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ zonaId }),
    credentials: "omit",
  });
  return resposta.ok;
}
