# Agent Roster — Capacidades e Regras de Atribuição

## Visão geral do ambiente

| Agente      | Qtd (2 PCs) | Melhor uso |
|-------------|-------------|------------|
| Claude Code | 2           | Análise, design, docs, review, bugs complexos |
| Codex       | 2           | Features completas (alto esforço/tokens) |
| Cursor      | 2           | Implementação via spec MD, bugs pequenos/médios |
| TRAE        | 2           | Frontend (80%), lint-heavy, spec-driven |

---

## Claude Code

**Forte em:**
- Análise de features em alto nível (trade-offs, benchmark, alternativas)
- Criação e refinamento de specs/documentação de features e backlog
- Análise de bugs complexos ou que outros agentes não resolveram
- Code review com foco em qualidade arquitetural
- Partes de código que exigem maior precisão ou julgamento

**Não usar para:**
- Implementação de features grandes e bem definidas (custo alto, Codex faz melhor)
- Tasks repetitivas com padrão claro (Cursor é mais rápido)

**Input ideal:** contexto rico, pergunta aberta ou código problemático

---

## Codex

**Forte em:**
- Desenvolvimento de features completas end-to-end
- Tasks que exigem grande volume de tokens / muitos arquivos
- Seguir especificações detalhadas com alta fidelidade

**Não usar para:**
- Tasks com spec ambígua ou incompleta — **vai produzir código errado**
- Análise, revisão ou decisões de design

**Input ideal:** arquivo de spec completo com: contexto do domínio, arquivos relevantes, comportamento esperado, regras de negócio, DoD claro

**Checklist antes de atribuir ao Codex:**
- [ ] Spec tem link para arquivos de domínio existentes
- [ ] Comportamento esperado descrito passo a passo
- [ ] Casos de borda documentados
- [ ] DoD (Definition of Done) explícito

---

## Cursor

**Forte em:**
- Implementação de feature lendo diretamente um arquivo MD de spec
- Bugs pequenos a médios
- Lint e formatação

**Não usar para:**
- Features sem spec completa
- Decisões arquiteturais

**Input ideal:** "Implemente seguindo o arquivo `docs/...spec.md`" — sem mais explicações

---

## TRAE

**Forte em:**
- Frontend (80% do front é TRAE)
- Tasks spec-driven com validações de lint embutidas
- Mais confiável em padrões de projeto quando bem configurado

**Atenção:**
- Mais lento que Cursor (roda validações)
- Menos assertivo sem spec do projeto bem configurada
- Considerar melhorar o CLAUDE.md / context para TRAE futuramente

**Input ideal:** mesmo que Cursor — arquivo MD de spec completo

---

## Matriz de decisão rápida

| Tipo de task | Agente |
|---|---|
| "Qual a melhor abordagem para X?" | **Claude** |
| "Cria spec/doc da feature Y" | **Claude** |
| "Implementa toda a feature Y (backend + tests)" | **Codex** |
| "Implementa seguindo o arquivo spec.md" | **Cursor** |
| "Implementa componente/tela frontend" | **TRAE** |
| "Esse bug não faz sentido, o que pode ser?" | **Claude** |
| "Corrige esse bug simples / lint" | **Cursor** |
| "Faz review dessa PR" | **Claude** |

---

## Fluxo de atribuição ideal

```
Ideia/Requisito
     ↓
Claude: análise + criação da spec MD
     ↓
Codex (se feature grande) ou Cursor/TRAE (se feature média/pequena)
     ↓
Claude: code review
     ↓
Merge
```
