# ADR 0002: IA gera alerta, nunca sanção automática

**Status:** aceito

**Contexto:** modelos de IA erram. Um banimento errado afeta um jogador real e pode gerar disputa legal.

**Decisão:** a IA devolve score de 0 a 1 sobre métricas anonimizadas. Score ≥ 0,7 vai para revisão humana. Score ≥ 0,95 limita ações temporariamente e exige revisão. Não existe decisão de banimento automático no código (`PoliticaRevisao` não tem essa opção).

**Consequências:** a equipe precisa de painel de revisão (fase 2). Fraude grave pode demorar mais para ser tratada. Todas as decisões são registradas para auditoria.
