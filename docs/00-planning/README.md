# Dev Planning System

Sistema de liderança técnica para coordenar os agentes de desenvolvimento.

## Como funciona

**Todo manhã:** abra uma sessão com Claude Code e peça:
> "Gera o plano de hoje"

Claude vai:
1. Ler o `backlog.md` e verificar prioridades
2. Ler o diário do dia anterior (`daily/YYYY-MM-DD.md`)
3. Gerar o arquivo `daily/YYYY-MM-DD.md` de hoje com tarefas distribuídas por agente
4. Atualizar status no `backlog.md` se necessário

**Durante o dia:** cada agente recebe o link para sua seção no diário do dia.

**No final do dia:** abra Claude Code e diga:
> "Registra o que foi feito hoje: [resumo rápido]"

---

## Estrutura dos arquivos

```
docs/00-planning/
  README.md              — este arquivo
  backlog.md             — backlog priorizado master
  agent-roster.md        — capacidades e regras de atribuição por agente
  daily/
    template.md          — template do diário diário
    YYYY-MM-DD.md        — plano de cada dia
```

---

## Regras de ouro

1. **Cada task no backlog tem um `agent:` definido** — sem ambiguidade de quem faz o quê
2. **Codex precisa de spec completa** — nunca atribuir task sem link para o arquivo de spec
3. **Cursor/TRAE recebem só o arquivo MD** — a spec é o contrato, não o dev lead
4. **Claude não implementa o que Codex pode fazer** — Claude foca em qualidade e design
5. **Tasks bloqueadas são documentadas com o motivo** — não ficam em limbo silencioso
