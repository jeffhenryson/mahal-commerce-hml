# Desenho: motor de transição automática do funil (CRM)

> Status: **só desenho**, ainda sem implementação (pendência 8 do admin, 02/11/2026). Para revisar antes de
> codar.

## O que existe hoje

- `customers.estagio` (`NOVO_LEAD → EM_ATENDIMENTO → QUALIFICADO → CLIENTE_ATIVO → INATIVO`) muda só à mão,
  por `PATCH /crm/customers/{id}/estagio`, que grava a trilha em `stage_transition`.
- Desde a V183 o backend já sabe, em lote, a **última compra paga** e a **quantidade de pedidos pagos** de
  cada cliente (`CustomerInsightsRepository.statsFor`). Também já existe um job diário que recalcula o
  `segmento` (`CustomerSegmentJob`, 03:30).

## Regras de negócio (do front, `BACKEND_TODO.md`)

| Estágio | Regra |
|---|---|
| Novo Lead | Compra registrada com nome e telefone (e-mail opcional). |
| Em Atendimento | Cliente sendo atendido no PDV. |
| Qualificado | Comprou nos últimos 20 dias. |
| Cliente Ativo | Comprou mais de uma vez no mês, ou é recorrente. |
| Inativo | Sem compras há mais de 45 dias. |

## Proposta

### 1. Regra pura no domínio
`CustomerStageRule.next(atual, stats, emAtendimento, agora) → Optional<CustomerStage>`, no mesmo molde de
`CustomerSegmentRule`. Testável sem banco. Avaliação, em ordem:

1. `emAtendimento` (comanda aberta ou venda em andamento com o cliente identificado) → `EM_ATENDIMENTO`.
2. Última compra há mais de 45 dias → `INATIVO`.
3. 2 ou mais pedidos pagos nos últimos 30 dias, ou segmento `Recorrente` → `CLIENTE_ATIVO`.
4. Compra nos últimos 20 dias → `QUALIFICADO`.
5. Nenhuma compra → fica onde está (`NOVO_LEAD` é o estado de entrada).

Se o resultado for igual ao atual, não acontece nada. Se for diferente, o motor grava `stage_transition`
com `autor = "sistema"`, para a trilha mostrar o que foi automático.

### 2. Gatilhos
- **Evento de venda paga** (`OrderPaidEvent`, publicado depois do commit por PDV, comanda e
  webhook/liquidação do app): reavalia só aquele cliente. É o que faz QUALIFICADO e CLIENTE_ATIVO
  aparecerem na hora.
- **Abertura e fechamento de comanda com cliente**: EM_ATENDIMENTO ao abrir. Ao fechar, a reavaliação da
  venda decide o próximo estágio.
- **Job diário** junto do recálculo de segmento: INATIVO só acontece com o passar do tempo, sem evento.

### 3. Convivência com a mudança manual
- Nova coluna `customers.estagio_manual_ate TIMESTAMP`. Quando o operador move o cliente à mão, o motor
  respeita a escolha por **7 dias** (configurável em `crm.funil.trava-manual-dias`). Depois disso volta a
  valer a regra.
- Opção de desligar tudo: `crm.funil.automatico.enabled=false` (padrão `false` na primeira entrega, ligado
  pelo dono depois de ver a simulação, item 4).

### 4. Simulação antes de ligar
`GET /crm/funil/simulacao` devolve quantos clientes iriam para cada estágio e uma amostra de 20, sem gravar
nada. O dono confere antes de ligar a flag.

### 5. Automação
A transição automática publica o mesmo `CUSTOMER_STAGE_CHANGED` da manual (com `autor=sistema`). As
automações de campanha que reagem a estágio passam a disparar sozinhas. Por isso a simulação (item 4) vem
antes de ligar a flag.

## Contrato para o front (quando implementado)
- `CustomerResponseDTO.ultimaCompraEm` já existe (V183).
- Novo `CustomerResponseDTO.estagioAutomatico` (boolean): false enquanto a trava manual vale.
- `StageTransitionResponseDTO.autor = "sistema"` nas automáticas.
- O tooltip do kanban pode trocar "critério informativo" por "movido automaticamente em <data>".

## Fora de escopo
Pontuação (lead scoring) e regras configuráveis pelo dono. As regras ficam fixas no código, como os cortes
de 20 e 45 dias, até haver pedido explícito.
