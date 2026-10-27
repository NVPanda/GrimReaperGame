# SDD 05: Fases e fora de escopo

## Fase 1 (esta versão)

- Loja com preço fixo (SDD 01).
- Pagamento via Mercado Pago (SDD 02).
- Movimento autoritativo e anticheat por regra e IA como alerta (SDD 03).
- Autenticação por JWT e segurança de API (SDD 04).
- Cliente web e casca Android para teste.
- Cadastro/login por senha, login social Google/Discord/Facebook/Steam e sessão persistida em SQLite (SDD 04).
- Elenco de até cinco personagens por conta com apenas um personagem ativo por vez.
- Fluxo separado de autenticação, seleção de personagem, escolha definitiva de cidade/continente natal, ponto de respawn por NPC Save e viagens pagas por NPC Teleporte (SDD 07).
- Economia básica de MC: saldo persistido por personagem, recompensa por entrega de quests e venda de itens permitidos ao mercador; o servidor aplica as tarifas de teleporte.

## Próximas melhorias (ainda sem cenários)

- **Combate e raids:** monstros comuns ainda não atacam jogadores; raids precisam de prontidão, participação simultânea e mecânicas de chefe mais completas.
- **Multiplayer em escala:** o servidor atual funciona em um único processo; várias instâncias exigem coordenação de presença e estado.
- **Moderação:** criar painel para revisão humana dos alertas da IA.
- **Entrega da loja:** persistir pedidos e integrar a confirmação do pagamento à concessão do item ao personagem.
- **Progressão de longo prazo:** títulos e badges por ranking, marcos e eventos.

## Fora de escopo (decisão registrada)

- **NFT com chance de ganho por drop ou compra:** não será implementado. Ver ADR 0003.
- **Venda de item por dinheiro dentro do jogo com sorteio:** não será implementado.
- **Troca de itens por dinheiro externo entre jogadores:** não será implementado nesta fase. Se for considerado no futuro, exige análise jurídica antes de qualquer especificação.
- **Banimento automático por IA:** não será implementado. Ver ADR 0002.

## Limitações atuais de identidade

- Recuperação de senha e verificação de e-mail exigem serviço de envio de e-mail e ainda não estão disponíveis.
- O banco SQLite é adequado ao servidor único local; para múltiplas instâncias ou alta disponibilidade será necessária migração coordenada para PostgreSQL.
- O saldo de MC não possui extrato separado de créditos e débitos.
- Configure os aplicativos OAuth e suas URLs de callback antes de habilitar cada provedor. Steam requer cadastrar o domínio/realm no painel Steamworks.
- Um login novo invalida a sessão anterior da conta; sessões simultâneas por dispositivo não são permitidas.
