# Prompt para o frontend-admin: o que o backend entrega e o front ainda não usa (05/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend`. Front de referência: `frontend-admin`,
branch `feat/front-pendencias-2026-10`, commit `f801b88`, com o resgate de cashback do balcão ainda no working tree.
Os paths são relativos a `frontend-admin/src/app/`.

Este documento substitui `PROMPT_FRONT_PENDENCIAS_2026-10.md`, cujas partes 1 a 9 e 11 já foram entregues. Da parte 10,
falta só a 2ª etapa, que está aqui como parte 12. A lista saiu de uma busca de cada contrato no código, sem abrir tela
por tela.

**Como usar:**

1. Antes de cada parte, confira no código se ela ainda falta. Se já estiver feita, diga isso e passe para a próxima.
2. Faça uma parte por vez, com os seus testes.
3. Não ligue uma flag sem cumprir as dependências dela.
4. O contrato completo está no documento de origem de cada parte, em `backend/docs/`, e em `docs/api-reference.md`.
   "RESPOSTA" é `frontend-admin/Docs/RESPOSTA_BACKEND_PENDENCIAS.md`.

Os erros vêm no formato de sempre, `{ errorCode, message }`.

## Índice

| Parte | Assunto | Prioridade | Origem |
|---|---|---|---|
| 0 | Regenerar o client | antes de tudo | RESPOSTA |
| 1 | Central de Pedidos: ligar as flags | 🔴 | `PROMPT_FRONT_CENTRAL_PEDIDOS.md` |
| 2 | Cashback: configurações e fechamento da mesa | 🔴 | `PROMPT_FRONT_CASHBACK_RESGATE.md` |
| 3 | Tela de senha do convite | 🔴 | `PROMPT_FRONT_ADMIN_CONVITE_SENHA.md` |
| 4 | Amostras de e-mail | 🔴 | `PROMPT_FRONT_EMAIL_SAMPLES.md` |
| 5 | Análise de vendas e meta mensal | 🟡 | RESPOSTA, item 9 |
| 6 | Kit no balcão por `kits[]` | 🟡 | RESPOSTA, item 2 |
| 7 | Estoque: origem da movimentação, NF-e, rascunho | 🟡 | RESPOSTA, itens 4b e 5 e QA EST-020 |
| 8 | CRM: segmento, dashboard e avatar | 🟡 | RESPOSTA, item 8 |
| 9 | Estorno por item e data de nascimento | 🟡, conferir | `PROMPT_FRONT_VIP_ATIVIDADES_ESTORNO.md` §3 e §4 |
| 10 | Salão e QR no client gerado | 🟢 | `PROMPT_FRONT_SALAO_QR_MELHORIAS.md`, parte 4 |
| 11 | Texto "CRM › Marcados" | 🟢 | `PROMPT_FRONT_VENDAS_AJUSTES.md` |
| 12 | Venda offline, 2ª etapa | 🟢, trabalho separado | `PROMPT_FRONT_PDV_OFFLINE.md` |

**Não faça agora, porque dependem do backend:**

- Integrações por tela: `Docs/PROMPT_BACKEND_INTEGRACOES_V2.md` ainda não foi implementado.
- Compras: só há a proposta de contrato, em `backend/docs/dominios/compras/proposta-pedido-cotacao.md`.

O que o backend entregou depois deste documento está em `backend/docs/RESPOSTA_BACKEND_PENDENCIAS_2026-11.md`.

---

## Parte 0: Regenerar o client

O `openapi.json` é de 22/10 e não tem `/central-pedidos`.

- Rode `npm run fetch:spec` com o backend da branch rodando em `BACKEND_URL` e depois `npm run generate:api`.
- Troque pelos models gerados o que hoje é escrito à mão: `central-pedidos.contrato.ts`, `CashbackSettingsDto` em
  `features/crm/crm.models.ts` e os campos extras do `SaleRequest` em `features/pdv/pdv.service.ts`.

**Aceite:**
- [ ] `npm run generate:api` sem diff depois do ajuste, e `npm test` verde.

## Parte 1: Central de Pedidos

Todas as flags de `features/central-pedidos/central-pedidos.flag.ts` estão `false`, e o backend já entregou. Faça os
ajustes da seção 7 do documento de origem e ligue na ordem da tabela que está nela. O que falta, conferido no código:

| Ajuste | Onde |
|---|---|
| `ORDER_FULFILLMENT_CHANGED` nos tipos de evento | `central-pedidos.contrato.ts` |
| Assinar `SOLICITACAO_*` e `CHAMADO_*` | `central-pedidos-avisos.service.ts` |
| Ramo próprio do `429`, com `Retry-After`, sem renovar token e sem contar em `MAX_AUTH_RETRIES` | `core/notifications/notification.service.ts` |
| Ler `delivery.trackingUrl ?? delivery.trackingCode` | `central-pedidos-fluxo.models.ts`, `dialogs/pedido-app.dialog.ts` |
| Campo `pixChave` (até 120 caracteres) | `components/configuracoes/recebimento-config.component.ts` |
| Ler a chave Pix da config, com o `localStorage` só de fallback | `dialogs/pedido-app.dialog.ts` e a mensagem de WhatsApp |
| Pré-preencher a taxa com `atendimento.taxaSugerida` | modal da taxa no aceite |
| Erros `ORDER_NOT_MARKETPLACE`, `INVALID_TRACKING_URL`, `INVALID_CENTRAL_CONFIG` | `mensagemErroFluxo` e tela de configuração |

Depois, ligue também as flags do App v2 (RESPOSTA, item 10):

- `PAGINACAO_COLUNA_SERVIDOR_ENABLED`: `GET /pdv/app-orders?coluna=&page=&size=`.
- `MARCAR_PEDIDO_APP_ENABLED`: `settle` com MARCADO, que conta no limite do BALCÃO.

⚠️ Antes de `CENTRAL_PEDIDOS_PERMISSOES_ENABLED`, o perfil de gerente precisa receber as três permissões `ONLINE_*` pela
tela de perfis, porque esse perfil não vem seedado.

**Aceite:**
- [ ] Aceite, separação e saída feitos em outra aba aparecem sem esperar o polling.
- [ ] A 11ª conexão SSE não derruba a sessão.
- [ ] O link da viagem aparece no painel e a chave Pix vem da configuração.

## Parte 2: Cashback

**2.1 Balcão.** Conclua o que está no working tree (`features/pdv/components/cashback-resgate/` e `pdv.component.ts`).
Não sobrescreva esse trabalho. Confira a linha "Cashback usado" no cupom e o campo escondido no caixa offline.

**2.2 CRM › Cashback › Configurações (tela nova).**
- Hoje `crm.service.ts` só tem o `GET /cashback/settings`. Falta o `PUT` e a tela.
- Inclua `CASHBACK_RATE_MANAGE` em `core/rbac/permissions.constants.ts`. Ela libera a escrita.
- Faixas dos campos e aviso da tela: documento de origem, §1.

**2.3 Mesas › Fechar conta.**
- `POST /pdv/comandas/{id}/close` aceita `cashbackToRedeem`, com o cliente vinculado à comanda. O front ainda não manda.
- Reaproveite `CashbackResgateComponent` e a prévia `redemption-quote`, com `amount` = mercadoria do fechamento.
- O resgate entra depois da taxa de serviço. Na conta dividida, cada fechamento tem o seu teto.

**Aceite:**
- [ ] Salvar as configurações e ver o efeito no teto e no mínimo.
- [ ] Fechar uma mesa com cashback, com os pagamentos somando o total já abatido.

## Parte 3: Tela de senha do convite

Siga o documento de origem: textos com `convite=1` em `features/auth/reset-password/reset-password.component.ts` e o
evento `USER_INVITE_ACCEPTED` em `shared/audit-log.constants.ts`.

**Aceite:**
- [ ] Textos cobertos com e sem `convite=1`.

## Parte 4: Amostras de e-mail

Siga o documento de origem: `core/integrations/email-integration.service.ts` passa para 34 amostras, com rótulos e
grupos, e o spec troca a contagem de 19 para 34.

**Aceite:**
- [ ] "Enviar todos" não devolve amostra sem rótulo.

## Parte 5: Análise de vendas e meta mensal

- Ligue `META_VENDAS_ENABLED` em `features/vendas/components/vendas-analise/vendas-analise.component.ts`.
- Inclua `SALES_GOAL_MANAGE` nas permissões. Só ela libera editar a meta.
- Integre o que falta na análise, em `vendas.service.ts`:
  - `GET /orders/analytics/heatmap`;
  - `GET /orders/analytics/payment-methods`;
  - `GET /orders/analytics/customers`;
  - `GET /orders/analytics/slow-movers`.

Confira antes se a aba já calcula algum desses dados no cliente. Se calcular, troque pelo endpoint.

**Aceite:**
- [ ] Salvar e reler a meta do mês.
- [ ] A soma das formas de pagamento bate com o líquido.

## Parte 6: Kit no balcão

Hoje o kit vai como `items[]` com `discountAmount` e `kitQuantity`, caminho que ainda exige a permissão de desconto.

- Mande `SaleRequest.kits[]` com `{ templateId, picks: [{ stepId, sku }] }`, até 20 por venda.
- Não repita as linhas do kit em `items`.
- Trate os `422 KIT_*` da RESPOSTA, item 2.

**Aceite:**
- [ ] Um atendente sem `PDV_SALE_DISCOUNT` vende um kit.

## Parte 7: Estoque

- **Movimentação:** `features/estoque/movimentacao-historico.ts` ainda estima o saldo e diz que o backend não o grava.
  Use `balanceBefore` e `balanceAfter`, e mantenha a estimativa só quando vierem nulos (registros anteriores à V181).
- **Origem:** mostre `originType` e `originId`, com link para o pedido, a comanda ou o recebimento.
- **NF-e:** use `accessKey`, `unit`, `taxUnit`, `taxQuantity` e `lines[].unitsPerPackage`, envie
  `overrides[].unitsPerPackage` e trate `409 NFE_ALREADY_RECEIVED`.
- **Rascunho (EST-020):** confira se a lista de produtos tem "Descartar rascunho" com `DELETE /estoque/products/{sku}`.
  A busca achou a confirmação só em `kit-form.page.ts` e `excluir-produto.service.ts`.

**Aceite:**
- [ ] Movimentação nova mostra o saldo real e a origem.
- [ ] Nota repetida mostra a mensagem própria.

## Parte 8: CRM

- **Segmento manual:** `PATCH /crm/customers/{id}/segmento`, com o indicador `segmentoManual`. `segmento: null` volta ao
  cálculo automático.
- **Recalcular:** `POST /crm/segmentos/recalcular`.
- **Dashboard:** mostre `cashbackEmAberto`, se a Visão Geral voltar a aparecer. Ela está escondida hoje.
- **Avatar:** troque o `CrmAvatarService` (localStorage) por `avatarUrl`.

**Aceite:**
- [ ] Segmento manual sobrevive ao recálculo e volta ao automático com `null`.
- [ ] O avatar aparece em outro navegador.

## Parte 9: Estorno e data de nascimento (conferir)

A busca não achou estes contratos fora do client gerado. Confira e, se faltarem:

- `refundedQuantity` em cada item do detalhe do pedido e no limite do diálogo de estorno.
- `403 REFUND_NOT_OWN_SESSION` e `422 REFUND_QUANTITY_EXCEEDED` com mensagem própria.
- `400 CUSTOMER_UNDERAGE` no cadastro do cliente.

## Parte 10: Salão e QR no client gerado

Migre `features/salao/salao-autoatendimento.service.ts` e `features/autoatendimento/autoatendimento-publico.service.ts`
para o client gerado, mantendo a assinatura pública. As demais partes daquele documento já foram entregues.

## Parte 11: Texto

Em `features/pdv/components/resumo-caixa/resumo-caixa.component.ts`, troque "CRM › Marcados" por "VENDAS › Marcados".

## Parte 12: Venda offline, 2ª etapa

Trabalho grande, para um ciclo próprio: PWA, fila em IndexedDB, `POST /pdv/sessions/{id}/sales/sync` e tela de revisão.
Siga o documento de origem. **Pergunte antes de começar.**
