# SDD 04: Identidade e segurança da API

## Regras de negócio

- Rotas de jogo exigem token JWT válido, com emissor e audiência verificados e expiração futura.
- O `jogadorId` vem do token, nunca do corpo da requisição.
- Entradas de texto têm tamanho máximo e lista de caracteres permitida. Texto exibido a outros jogadores é escapado.
- Erros internos retornam mensagem genérica. Detalhes ficam só no log.
- CORS aceita apenas origens listadas na configuração.
- Limite de taxa por IP em rotas sensíveis e por jogador nas ações de jogo.
- Senhas são armazenadas com Argon2id; a API nunca devolve hashes nem indica se uma senha ou conta social existe.
- Sessões têm duração limitada, são revogáveis no logout e são verificadas no banco a cada requisição autenticada.
- Cada conta pode ter até cinco personagens. A sessão pode manter um único personagem ativo; um novo login revoga a sessão anterior da conta.
- Identidades Google, Discord, Facebook e Steam são vinculadas por identificador imutável do provedor. OAuth usa `state`, PKCE quando suportado e código de troca curto, de uso único.
- Contas e personagens são persistidos em SQLite. E-mail de rede social só é associado como e-mail da conta quando o provedor o marca como verificado; contas não são vinculadas automaticamente por coincidência de e-mail.
- O cliente mantém o token em `sessionStorage`; ele é descartado ao sair e não sobrevive ao fechamento da sessão do navegador.

## Cenários

**SEG-01** Rota de jogo sem token retorna 401.
- Dado uma chamada a `/api/checkout` sem cabeçalho de autorização, quando é processada, então a resposta é 401.

**SEG-02** Token expirado é recusado.
- Dado um JWT com expiração no passado, quando é usado, então a resposta é 401.

**SEG-03** Token de outro emissor ou audiência é recusado.
- Dado um JWT com emissor diferente, quando é usado, então a resposta é 401.

**SEG-04** Identidade do jogador vem do token.
- Dado um corpo de checkout com campo `jogador_id` diferente do token, quando processado, então o pedido usa o ID do token.

**SEG-05** Nome de personagem fora do padrão é recusado.
- Dado o nome `<script>x`, quando é validado, então ocorre erro.

**SEG-06** Texto de outro jogador é escapado.
- Dado o texto `<b>oi</b>`, quando é preparado para exibição, então o resultado é `&lt;b&gt;oi&lt;/b&gt;`.

**SEG-07** Texto acima do tamanho máximo é recusado.
- Dado um texto de 201 caracteres com limite de 200, quando é validado, então ocorre erro.

**SEG-08** Erros internos não vazam detalhes.
- Dado uma exceção inesperada, quando a resposta é montada, então o corpo contém apenas `erro interno`.

**ID-01** Cadastro por email cria uma conta persistente.
- Dado email válido e senha com pelo menos 12 caracteres, quando a conta é cadastrada, então um token de sessão é emitido e a conta pode ser encontrada após reiniciar o servidor.

**ID-02** Senha incorreta não inicia sessão.
- Dado email e senha incorretos, quando o login é tentado, então a API retorna 401 com mensagem genérica.

**ID-03** Senha é armazenada usando Argon2id.
- Dado um cadastro, quando a conta é gravada, então apenas hash e salt Argon2id ficam persistidos, nunca a senha em texto claro.

**ID-04** A conta possui no máximo cinco personagens.
- Dado uma conta com cinco personagens, quando um sexto é criado, então a API retorna conflito e preserva os cinco existentes.

**ID-05** Uma conta pode jogar com somente um personagem ativo.
- Dado uma conta com personagens, quando um personagem é selecionado, então ele é o único personagem ativo daquela sessão.

**ID-06** Um novo login revoga a sessão anterior da conta.
- Dado uma conta com sessão válida, quando um novo login é concluído, então o token anterior recebe 401 e apenas a nova sessão permanece ativa.

**ID-07** OAuth usa estado de uso único e callback exato.
- Dado um fluxo social iniciado, quando o estado é reutilizado, expirado ou enviado por outro provedor, então o login é recusado.

**ID-08** Código OAuth de troca é curto e de uso único.
- Dado uma autenticação social concluída, quando o código é trocado uma vez, então um token é emitido; uma segunda troca recebe 401.

**ID-09** Ações de movimento exigem personagem selecionado.
- Dado uma conta autenticada sem personagem ativo, quando tenta mover, então a API recusa a ação.
