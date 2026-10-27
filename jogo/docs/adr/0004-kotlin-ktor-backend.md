# ADR 0004: Backend em Kotlin com Ktor

**Status:** aceito

**Contexto:** o backend precisa de escalabilidade, segurança de API e portabilidade para mobile. O projeto anterior tinha loja em Python e backend em Kotlin, duplicando regras.

**Decisão:** um único backend em Kotlin com Ktor. A loja e o anticheat passam a ficar nele, organizados por contexto (DDD). Testes com kotlin-test em JUnit 5.

**Consequências:** a loja Python fica obsoleta (`/home/claude/loja/` não é mais a fonte de verdade). Kotlin Multiplatform fica como opção para compartilhar regras com o cliente móvel no futuro.
