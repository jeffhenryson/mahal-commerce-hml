# Prompt para o frontend-admin: Salão e QR da mesa: polling, requisições repetidas e padronização (22/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes`, migrations até **V172**. Front de referência:
`frontend-admin` no commit `34ce2f1` (22/10/2026). Os paths são relativos a `frontend-admin/src/app/`.

**Contexto.** O backend acabou de corrigir os N+1 do autoatendimento (PDV-C056..C065): os GETs públicos não
carregam mais a comanda inteira para saber se a mesa está aberta, e as filas do PDV leem tudo em lote. Falta
o lado do front, que hoje **multiplica** as leituras:
- o QR relê o status a cada 10 s e a mesa a cada 12 s, por aparelho, sem trava de requisição em andamento;
- o cardápio, que é o endpoint mais caro, é relido depois de quase toda ação;
- a lista de mesas e o detalhe da mesa releem a mesma comanda ao mesmo tempo.

Este documento junta a análise do backend com a revisão feita no próprio front. Cada item foi conferido no
código, e os que não procediam estão marcados.

**Como usar:** uma parte por vez, cada uma com o seu commit e os seus testes. Erros no formato de sempre,
`{ errorCode, message }`.

## Índice

| Parte | Assunto | Prioridade | Depende de |
|---|---|---|---|
| 0 | Contrato: o que mudou no backend (quase nada) | leitura | — |
| 1 | Utilitário de polling único (`pollEnquantoVisivel`) | 🔴 | — |
| 2 | QR: polling sem empilhar e sem reler o cardápio | 🔴 | 1 |
| 3 | Admin/PDV: lista × detalhe da mesa, fila por SSE | 🟡 | 1 |
| 4 | Client gerado no lugar do `HttpClient` à mão | 🟡 | 0 |
| 5 | Quebrar `mesa-autoatendimento.page.ts` (2676 linhas) | 🟢 | 1, 2 |
| 6 | Componentes do admin: impressão, QR em paralelo, erros | 🟢 | — |
| 7 | Checklist de aceite | — | todas |

---

## Parte 0 — Contrato

**Nenhum DTO mudou.** As mesmas rotas, com os mesmos campos, respondem mais rápido. A única diferença visível:

- `GET /pdv/solicitacoes-sessao?status=EXPIRADA` agora pagina certo. Antes vinha uma página curta, e o
  `totalElements` contava as pendentes que ainda valiam. Se alguma tela compensava isso, a compensação sai.

O que ficou mais barato e, por isso, **pode** ser relido sem culpa:
- `GET /public/salao/{token}/mesa-atual`;
- `GET /public/salao/{token}/solicitacoes/{codigo}`;
- `GET /salao/ocupacoes`;
- `GET /pdv/chamados-mesa`;
- `GET /pdv/solicitacoes-sessao`.

`GET /public/salao/{token}` (o cardápio) continua caro: ele pagina os produtos de sessão. Não deve ser relido à toa (Parte 2).

---

## Parte 1 — Utilitário de polling único

Hoje cada tela reimplementa intervalo, aba visível e limpeza do timer, cada uma de um jeito:

| Onde | Intervalo | Confere aba oculta? | Trava de requisição em andamento? |
|---|---|---|---|
| `features/autoatendimento/mesa-autoatendimento.page.ts:2523` (`acompanhar`, status do pedido) | 10 s | ❌ | ❌ `setInterval(async …)` empilha com rede lenta |
| `…/mesa-autoatendimento.page.ts:2590` (`acompanharComandas`) | 12 s | ✅ | ⚠️ só `ocupado()`, que não cobre a própria releitura |
| `…/mesa-autoatendimento.page.ts:2600` (`acompanharMesa`) | 12 s | ✅ | ⚠️ idem |
| `features/salao/components/salao-mesas/salao-mesas.component.ts:348` | 30 s | ✅ (`document.hidden`) | ❌ |
| `features/salao/dialogs/mesa-detalhe.dialog.ts:313` (`atualizar`) | 30 s | ❌ | ✅ `atualizando()` |
| `features/pdv/pdv.component.ts:4954` (fila de solicitações) | 20 s | ✅ | ✅ (`seq` no store) |

Crie `shared/utils/poll-enquanto-visivel.ts`:

```ts
/**
 * Roda `fn` a cada `ms` enquanto a aba está visível. Não sobrepõe chamadas: a próxima só começa depois
 * que a anterior termina. Relê na hora ao voltar para a aba e se desliga sozinho no DestroyRef.
 * Com erro, espera em dobro, até `maxMs`; o primeiro sucesso volta ao normal.
 */
export function pollEnquantoVisivel(
  fn: () => Promise<unknown>,
  ms: number,
  opts: { destroyRef: DestroyRef; imediato?: boolean; maxMs?: number },
): { parar(): void; agora(): Promise<void> };
```

Regras:
- **`setTimeout` encadeado, não `setInterval`.** É isso que impede o empilhamento.
- **Aba oculta não agenda a próxima.** O `visibilitychange` para `visible` chama `fn` na hora e reagenda. O padrão já existe em `pdv.component.ts:4958-4964` (`aoVoltar`); aproveite-o.
- `agora()` relê fora de hora, por exemplo depois de uma ação do usuário, respeitando a trava.
- O relógio de tela (`agora.set(Date.now())`) **não** é polling e fica fora: é um `setInterval` síncrono e barato.
- O teste unitário usa fake timers e cobre quatro casos: não sobrepõe, pausa oculta, relê ao voltar e faz backoff.

---

## Parte 2 — QR: polling sem empilhar e sem reler o cardápio

### 2.1 Um agendador por etapa

Em `mesa-autoatendimento.page.ts`, troque `releitura` e `releituraMesa` (os dois `setInterval`, `:1664-1665`)
por `pollEnquantoVisivel`:
- **Status do pedido** (`acompanhar`, `:2521-2555`): a 10 s, parando quando `status !== 'PENDENTE'`.
- **Mesa** (`acompanharMesa`): a 12 s.
- **Minhas comandas** (`acompanharComandas`): a **30 s**, e não a 12. Cada ciclo reenvia o CPF e a
  `confirmacao` num POST (`/minhas-comandas`), que confere a trava da confirmação toda vez. Para nos
  códigos `CONFIRMACAO_INVALIDA` e `CLIENTE_NAO_IDENTIFICADO`, como já faz.

> **Conferido e não procede:** "dois pedidos seguidos quando o pedido é aceito" (`:2528-2552`). Os ramos
> são exclusivos. Com `etapa() === 'mesa'`, sai por `lerMesa()` e dá `return`. Senão vai por `verMesa()`,
> que chama `lerMesa()` uma única vez antes de ligar o `acompanharMesa`. Não mexa.

### 2.2 Não reler o cardápio quando só o acesso muda

O cardápio é relido depois de entrar com código, entrar pela identificação, pedir e começar um pedido novo:
`:1791`, `:1954`, `:2093`, `:2437`, `:2456` e `:2490`. Em todos esses casos, o que muda é **só** o campo `acesso`
(`LIVRE` → `LIBERADO`). Essências, faixas e preços não mudam em segundos.

- Depois de `acessar`, `entrar` e de um `solicitar` aceito pelo servidor, faça `this.cardapio.update(c => c && { ...c, acesso: 'LIBERADO' })`.
- **Reler de verdade** só em dois casos. O primeiro é um `ESSENCIA_INDISPONIVEL` no `solicitar`: a lata acabou e o cardápio está velho. O segundo é o "novo pedido" (`:2490`), e mesmo assim só se a última leitura passou de 5 minutos. Guarde `cardapioLidoEm`.
- `MESA_OCUPADA` no `solicitar` continua relendo, porque o acesso mudou para `BLOQUEADO`.

### 2.3 Uma leitura por ação

- `verMesa()` já relê a mesa. Ao entrar nela pelo aceite, não chame `lerMesa()` de novo no mesmo tick.
- Depois de `chamar`, a resposta (`ChamadoDaMesa`) já basta para atualizar `chamadosLocais`. Hoje a
  próxima releitura de 12 s confirma, e isso está certo: não chame `lerMesa()` logo depois do POST.

---

## Parte 3 — Admin/PDV: lista × detalhe da mesa, fila por SSE

### 3.1 Lista e detalhe não releem juntos

`salao-mesas.component.ts:348` relê todas as comandas a cada 30 s. Com o detalhe aberto,
`mesa-detalhe.dialog.ts:313` relê a mesma comanda (`getComanda`), também a cada 30 s.

- Enquanto o diálogo está aberto, **pause** a lista: o `afterOpened`/`afterClosed` do `MatDialogRef` liga e desliga.
- Ao fechar o diálogo, a lista relê uma vez (`agora()`). Ela pode ter mudado lá dentro.
- O diálogo passa a usar `pollEnquantoVisivel`. Hoje ele relê com a aba oculta.

### 3.2 Código da mesa sem baixar todas as ocupações

`mesa-detalhe.dialog.ts:~322` (`carregarCodigoMesa`) chama `listOcupacoes()` e faz `.find()` para achar **uma**.
Isso acontece em toda abertura do diálogo.

- A tela de mesas lê `GET /salao/ocupacoes` uma vez por ciclo e passa o `codigo` ao diálogo pelo `data`.
- O diálogo só lê as ocupações se abrir sem esse dado.

### 3.3 Fila de pedidos e chamados por SSE, com o polling de reserva

O backend já envia `SOLICITACAO_SESSAO` e `CHAMADO_MESA` por SSE para quem tem `PDV_COMANDA_MANAGE`, depois do
commit: o pedido já está na fila quando o aviso chega. O front recebe isso em `core/notifications/notification.service.ts`
(`:244`), mas a fila do PDV ignora: `pdv.component.ts:4954` relê a cada 20 s.

- No `PedidosAppPendentesStore` (ou no `pdv.component`), crie um `effect` que observe a notificação nova do
  tipo `SOLICITACAO_SESSAO` ou `CHAMADO_MESA` e chame `carregarSolicitacoes()` na hora.
- O intervalo de 20 s vira **reserva**, de 60 s: cobre a reconexão do SSE.
- **Não há** evento de "comanda mudou" para a lista de mesas. Ela continua no polling (3.1).

---

## Parte 4 — Client gerado no lugar do `HttpClient` à mão

`features/salao/salao-autoatendimento.service.ts:174-281` e `features/autoatendimento/autoatendimento-publico.service.ts`
montam URL e tipos à mão. Se o contrato mudar, o TypeScript não avisa. A causa está no backend: o `openapi.json` do
front **não tem** `/public/salao/**`, `/salao/**` nem `/pdv/chamados-mesa`. Está desatualizado.

1. `npm run fetch:spec` com o backend da branch rodando, depois `npm run generate:api`.
2. Migre os dois services para o client gerado e mantenha a assinatura pública deles. Os componentes não mudam.
3. Nos models gerados, apague os tipos duplicados em `autoatendimento.models.ts` e `salao/*.models.ts`. Fica só o que é de tela (rótulos, filtros, ordenação).
4. `silencioso()` (`autoatendimento-publico.service.ts:24`) cria um `HttpContext` novo por chamada. Vira uma constante do módulo.

---

## Parte 5 — Quebrar `mesa-autoatendimento.page.ts`

São 2676 linhas: template inline de cerca de 950, estilos de cerca de 360 e cerca de 60 signals. Identificação, pedido,
mesa, comandas e polling estão juntos.

- **Store** `autoatendimento/mesa-qr.store.ts` (signals): token, aparelho, cardápio, identificação, confirmação, pedido, mesa atual e os três pollers da Parte 2.
- **Componentes por etapa**, cada um com template e estilos em arquivo próprio:
  - `qr-identificar` (com cadastro e completar);
  - `qr-codigo`;
  - `qr-wizard` (sabores → montagem → revisão; o `qr-stepper` já existe);
  - `qr-mesa` (consumo, histórico, chamados, conta);
  - `qr-minhas-mesas`.
- A página fica só com o roteamento entre etapas.
- **Rede de segurança:** o `mesa-autoatendimento.page.spec.ts` (971 linhas) passa **sem alteração de
  comportamento** antes de cada extração. Mova os testes junto com o código.
- **Repetição nos models** (`autoatendimento.models.ts`, `salao/mesas.models.ts`, `latas-abertas.models.ts`,
  `pdv/cobranca-*.ts`): a revisão do front levantou a suspeita, mas **não confirmou**. Antes de unificar, prove
  que é a mesma regra: totais, `formatBrl` e tipos de comanda. Regra parecida com exceção diferente não é repetição.

---

## Parte 6 — Componentes do admin

- **QR gerado um por vez.** Em `salao-autoatendimento.component.ts:~717` (`gerarQrs`), o `await` está dentro do `for`.
  Troque por `Promise.all(this.mesas().map(...))`, mantendo o `try/catch` por mesa, porque uma falha não derruba as outras.
- **HTML de impressão em string.** `imprimirTodos()` (`:689-715`) monta a página com template literal,
  `document.write` e `window.print()`. O nome já passa por `escapar`. O ganho de extrair para um componente
  de impressão, ou um `<ng-template>`, é poder testar e manter o layout. É baixa prioridade.
- `salao-autoatendimento.component.ts` (963 linhas) e `salao-essencias.component.ts` (925) misturam carregamento,
  regra e impressão. Extraia pelo menos a regra pura para `*.models.ts`, com teste.
- **Erros:** centralize o mapa de código de erro → mensagem do QR. Hoje há `mensagemErroChamado` e `extractApiErrorCode`
  espalhados. Use um `mensagemDoQr(error, padrao)`, como o `mensagemErroAceite` do PDV.

> **Já está bom; não mexa:**
> - os timers de `salao-mesas` e `mesa-detalhe` são limpos no `DestroyRef`;
> - `carregarMesas` (`:624`) e as essências (`:546`) carregam em paralelo com `Promise.all`;
> - `mesa-detalhe` tem trava de requisição em andamento;
> - não há `*ngIf`/`*ngFor` antigos.

---

## Parte 7 — Checklist de aceite

- [ ] Com o DevTools em *Slow 3G*, o QR na tela da mesa **nunca** tem duas requisições de `/mesa-atual` ao mesmo tempo, nem duas de `/solicitacoes/{codigo}`.
- [ ] Com a aba oculta por 2 min, nenhuma requisição sai do QR, da lista de mesas nem do detalhe; ao voltar, cada um relê uma vez.
- [ ] Entrar com o código não relê `GET /public/salao/{token}` (o cardápio); a tela passa a `LIBERADO` na hora.
- [ ] Com o detalhe da mesa aberto, a lista de mesas não relê.
- [ ] Abrir o detalhe não chama `GET /salao/ocupacoes`, que já veio da lista.
- [ ] Um pedido novo pelo QR aparece na fila do PDV em menos de 2 s (SSE), sem esperar o ciclo.
- [ ] `npm run generate:api` sem diff depois de migrar os services; `npm test` e `npm run lint` verdes.
- [ ] `mesa-autoatendimento.page.spec.ts` verde em cada etapa da Parte 5.
