# ADR 0007: identidade persistente, SQLite e sessão única

## Status

Aceita.

## Contexto

As rotas protegidas já exigiam JWT, mas não existiam cadastro, autenticação, seleção persistente de personagens ou revogação de sessão. O cliente aceitava um token digitado manualmente e permitia movimento local sem identidade. A fase 2 planejava contas e personagens, mas o protótipo precisa desses fluxos para ser jogável.

## Decisão

- Criar o contexto `jogo.identidade` com repositório SQLite para contas, credenciais, identidades externas, personagens, sessões e trocas OAuth.
- Armazenar senhas com Argon2id, salt aleatório por conta e comparação em tempo constante.
- Emitir JWT de curta duração identificando a conta e um ID de sessão; verificar no SQLite se a sessão ainda está válida em cada rota autenticada.
- Permitir até cinco personagens por conta e manter uma única sessão válida por conta. Um login novo revoga a sessão anterior; cada sessão seleciona no máximo um personagem ativo.
- Integrar Google, Discord, Facebook via OAuth 2.0 e Steam via OpenID 2.0. OAuth usa estado de curta duração e de uso único, PKCE quando suportado e troca por código temporário de uso único antes de entregar o JWT ao cliente.
- Manter o JWT em `sessionStorage` no cliente web. Recuperação de senha e verificação de e-mail ficam adiadas até existir um serviço de envio configurado.
- Limitar o servidor local a `127.0.0.1`; deployments públicos precisam de HTTPS e configuração explícita dos provedores.

## Consequências

- Contas e personagens sobrevivem a reinícios do servidor local.
- Somente uma sessão por conta pode movimentar personagens; login em outro dispositivo encerra a anterior.
- OAuth social não fica ativo até que credenciais de cada aplicativo sejam fornecidas por variáveis de ambiente e callbacks cadastrados no provedor.
- SQLite atende à execução em instância única. Alta disponibilidade ou várias instâncias exigem migração para um banco servidor.
- O token em `sessionStorage` limita sua persistência após fechar a sessão do navegador, mas permanece acessível ao JavaScript da origem.
- Falhas e timeouts do provedor externo podem interromper um login social; o login por senha continua disponível.

## Alternativas consideradas

- Sessões em memória: descartadas por não persistirem nem permitirem revogação confiável após reinício.
- JWT sem registro de sessão: descartado porque logout e sessão única não seriam aplicáveis imediatamente.
- Vincular contas sociais automaticamente pelo e-mail: descartado porque e-mail não verificado ou colisões podem permitir tomada indevida de conta.
- PostgreSQL desde o protótipo: adiado para evitar exigir um serviço externo local; SQLite permanece substituível por trás do repositório.
