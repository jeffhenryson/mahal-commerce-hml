# Salão — autoatendimento pelo QR da mesa

O cliente escaneia o QR impresso na mesa e, sem login:
- se identifica pelo CPF ou pelo celular, ou se cadastra;
- escolhe a sessão (a essência ou uma mescla com proporção, o vaso, o carvão e os adicionais) e pede;
- acompanha o consumo da mesa e chama a equipe.

O atendente aceita ou recusa o pedido no PDV. O aceite lança a sessão na comanda pelo caminho normal do `ComandaService`.

Frente consumidora: `frontend-admin`, telas `features/autoatendimento` (QR público) e `features/salao` (admin e PDV). Os contratos que o front escreveu estão nos prompts `prompt-backend-salao-qr.md` e `contrato-backend-salao-qr.md` do scratchpad do front.

| Rodada | Data | Migração | Conteúdo |
|---|---|---|---|
| 1 | 2026-10-20 | V167 | Mesas com QR, essências do dia, identificação e cadastro, solicitação → aceite no PDV |
| 1b | 2026-10-20 | V168 | Ocupação da mesa (código de 4 dígitos e acesso por aparelho), escolhas da sessão, carvão jumbo |
| 2 | 2026-10-21 | V169 | Proporção da mescla, perfis de sabor, chamados da mesa, comandas abertas do cliente, entrar pela identificação |
| 3 | 2026-10-21 | V170 | Pedido sem CPF de quem já está na mesa (acompanhante), pagar no final para todos, linha do tempo da sessão, `perfisConfigurados`, trava da confirmação |

## Arquitetura

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/salao/`: `SalaoMesa`, `SolicitacaoSessao`/`SolicitacaoStatus`/`SolicitacaoResumo`, `EscolhasSessao`, `MesaOcupacao`, `AcessoMesa`, `CardapioDaMesa`, `EssenciaOferecida`/`EssenciaDoDia`, `PerfilSabor`, `MesaAtual` (com `Item`, `Sessao`, `UltimaSessao`), `MesaAbertaDoCliente`, `ChamadoMesa`/`TipoChamado`/`FormaPagamentoChamado`/`StatusChamado`. `ProporcaoMistura` fica em `pdv`, porque o `ComandaItem` também a usa |
| exceptions | `core/domain/exception/salao/` |
| ports/in | `SalaoAutoatendimentoUseCase` |
| ports/out | `SalaoAutoatendimentoRepository`; `AuditLogRepository.countSince` (usado pela trava da confirmação) |
| service | `SalaoAutoatendimentoService`, ligado no `CoreBeanConfig` |
| adapter/in | `SalaoPublicoController` (`/public/salao/{token}`, sem login) e `SalaoAutoatendimentoController` (admin e PDV) |
| adapter/out | `SalaoAutoatendimentoRepositoryImpl`, com os JPA repos `SalaoMesa*`, `SalaoSolicitacao*`, `SalaoEssencia*` e `SalaoChamado*` |

## API

### Pública — `/public/salao/{token}` (sem login)

Rate limit por IP no `ResourceRateLimitingFilter`, com três baldes: leitura (120/min), escrita (30/min) e `/acesso` (10 a cada 10 min).

| Método | Rota | Corpo → resposta | Erros |
|---|---|---|---|
| `GET` | `` | `?dispositivo=`. Responde o cardápio: faixas ativas, essências com lata aberta (`destaque`, `perfis`), preços do vaso grande e do carvão jumbo, adicionais e `acesso` (`LIVRE`, `LIBERADO` ou `BLOQUEADO`) | 404 `SALAO_MESA_NOT_FOUND` |
| `POST` | `/identificar` | `{cpf?, celular?}` → `{encontrado, nome (mascarado), vip, faltando[], mesasAbertas[]}`. A lista vem **resumida**: `token` e `emAberto` só da mesa deste QR, sem itens nem chamados | 400 `SALAO_DADOS_INVALIDOS` |
| `POST` | `/cadastrar` | `{nome, celular, cpf, dataNascimento, aceite}` → 201 com a identificação | 409 `CLIENTE_JA_CADASTRADO` |
| `POST` | `/completar` | `{cpf?, celular?, novoCpf?, novoCelular?, dataNascimento?}`: preenche só o que está vazio no cadastro | 404 `CLIENTE_NAO_IDENTIFICADO`, 409 `CLIENTE_JA_CADASTRADO` |
| `POST` | `/solicitar` | `{cpf?, celular?, essenciaSku, essenciaSku2?, proporcao?, observacao?, vasoGrande, carvao?, adicionalIds[], pagarNoFinal, dispositivo}` → 201 `{codigo, status, …}` | 403 `MESA_OCUPADA`, 404 `CLIENTE_NAO_IDENTIFICADO`, 400 `SALAO_DADOS_INVALIDOS`, 409 `ESSENCIA_INDISPONIVEL` / `SOLICITACAO_JA_PENDENTE` |
| `GET` | `/solicitacoes/{codigo}` | `?dispositivo=` → `{codigo, status, motivo, criadoEm, codigoMesa}`. `codigoMesa` só vai para o aparelho com acesso | 404 `SOLICITACAO_NOT_FOUND` |
| `POST` | `/acesso` | `{codigo (4 dígitos), dispositivo}` → 204. O aparelho entra como **acompanhante** | 403 `CODIGO_MESA_INVALIDO` |
| `GET` | `/mesa-atual` | `?dispositivo=` → `{mesa, cliente, codigo, abertaEm, itens[], emAberto, chamados[], ultimaSessao}`. Cada item: `{nome, quantidade, valor, pago, lancadoEm, sessao}` | 403 `MESA_OCUPADA` |
| `POST` | `/chamados` | `{tipo, formaPagamento?, dispositivo}`, ou `{…, comandaRef, cpf?/celular?, confirmacao}` para comanda do PDV → 201 `{id, tipo, formaPagamento, status, criadoEm, atendidoEm}` | 400, 403 `MESA_OCUPADA` / `CONFIRMACAO_INVALIDA`, 404, 409 `CHAMADO_JA_ABERTO`, 429 `CONFIRMACAO_BLOQUEADA` |
| `POST` | `/entrar` | `{cpf?, celular?, confirmacao, dispositivo}` → 204. Só o titular da mesa ocupada deste QR entra por aqui | 403 `MESA_OCUPADA` / `CONFIRMACAO_INVALIDA`, 429 `CONFIRMACAO_BLOQUEADA` |
| `POST` | `/minhas-comandas` | `{cpf?, celular?, confirmacao}` → `MesaAbertaDoCliente[]` **completa**: token, valor, itens e chamados de todas as comandas | 403 `CONFIRMACAO_INVALIDA`, 404, 429 `CONFIRMACAO_BLOQUEADA` |

Campos novos nas respostas:
- **`sessao`**, nos itens de `/mesa-atual` e de `/minhas-comandas`: vem só nas linhas `SESSAO`/`ROSH_EXTRA` e é null nas demais. Formato: `{status, essencia, pagarNoFinal, lancadaEm, inicioEm, entregueEm, recolhidaEm, preparoMin, naMesaMin}`.
  - É tirada do `SessionTimeline` (PDV-F035).
  - `status` vai de `AGUARDANDO_PAGAMENTO` a `RECOLHIDO`.
  - `essencia` é o texto da linha sem o sufixo de vaso grande, o que inclui a proporção ("Zomo · Uva 70% + Zomo · Menta 30%"). É null no 2º rosh com sabor pendente.
  - Não leva custo, operador nem id da linha.
- **`ultimaSessao`** `{essenciaSku, essenciaSku2, proporcao, vasoGrande, carvao, adicionalIds}`: a última linha `SESSAO` com SKU da comanda, lançada pelo QR ou pelo PDV. Serve para o "repetir".
- **`MesaAbertaDoCliente`** `{ref, origem (QR | PDV), token, mesa, abertaEm, emAberto, estaMesa, itens, chamados}`.
  - `ref` é opaco: os 16 primeiros caracteres do SHA-256 de `comanda:id:openedAt`, em base64url. Nunca é o id sequencial.
  - A lista vem com esta mesa primeiro.

### Admin e PDV (autenticado)

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/salao/mesas` | `PDV_READ` | As mesas com token |
| `POST` | `/salao/mesas` | `PDV_SESSAO_MANAGE` | Cria `{nome, ativo?}`. Nome repetido, sem diferenciar maiúsculas → 409 `SALAO_MESA_NOME_DUPLICADO` |
| `PUT` | `/salao/mesas/{id}` | `PDV_SESSAO_MANAGE` | Renomeia e ativa ou desativa |
| `POST` | `/salao/mesas/{id}/regenerar-token` | `PDV_SESSAO_MANAGE` | QR novo: o impresso antes deixa de funcionar |
| `GET` | `/salao/ocupacoes` | `PDV_READ` | Mesas ocupadas: `{mesaId, comandaId, codigo, abertaEm}` |
| `GET` | `/salao/essencias-do-dia?data=` | `PDV_READ` | Essências disponíveis com `selecionada` (destaque do dia). Cada uma traz `perfis` e **`perfisConfigurados`**: true quando a casa já marcou os perfis do SKU; false quando `perfis` é a sugestão pelo nome |
| `PUT` | `/salao/essencias-do-dia?data=` | `PDV_SESSAO_MANAGE` | `{skus[]}`: substitui os destaques da data |
| `POST` | `/salao/essencias-do-dia/copiar?de=&para=` | `PDV_SESSAO_MANAGE` | O "copiar de ontem" |
| `PUT` | `/salao/essencias-perfis` | `PDV_SESSAO_MANAGE` | `{itens: [{sku, perfis[]}]}` → 204. Substitui os perfis de cada SKU; lista vazia limpa e desliga a sugestão |
| `GET` | `/pdv/solicitacoes-sessao?status=PENDENTE&page&size` | `PDV_READ` | `PageResult`. Cada solicitação traz os rótulos dos sabores, `proporcao`, as escolhas, o `total` estimado e **`acompanhante`**. Com `acompanhante`, `cliente` já vem como "Acompanhante de &lt;titular&gt;" |
| `POST` | `/pdv/solicitacoes-sessao/{id}/aceitar?sessionId=` | `PDV_COMANDA_MANAGE` | Lança a sessão (veja as regras). Pedido pago no final exige `PDV_SESSION_PAY_LATER` (403 `SESSION_PAY_LATER_NOT_ALLOWED`). 409 `SOLICITACAO_NAO_PENDENTE` |
| `POST` | `/pdv/solicitacoes-sessao/{id}/recusar` | `PDV_COMANDA_MANAGE` | `{motivo?}` |
| `GET` | `/pdv/chamados-mesa?status=ABERTO` | `PDV_READ` | `[{id, tipo, formaPagamento, status, mesa, cliente, criadoEm, atendidoEm, atendidoPor}]`, sem paginação |
| `POST` | `/pdv/chamados-mesa/{id}/atender` | `PDV_COMANDA_MANAGE` | Marca ATENDIDO. É idempotente; 404 `CHAMADO_NOT_FOUND` |

O lançamento de sessão do PDV (`POST /pdv/comandas/{id}/sessoes`) também aceita `proporcao`, que é opcional e só vale com `essenciaSku2`.

## Regras

**O que o QR oferece**
- Oferece as essências com **lata aberta** no depósito das sessões (o Salão, se houver um ativo; senão o padrão) e com faixa ativa.
- As essências do dia são **destaques**: aparecem no topo e não filtram nada (V168).

**Dados pessoais**
- O lado público nunca devolve id de cliente, CPF, telefone ou e-mail. O nome vai mascarado ("Ma***").
- O cadastro público não toca em cliente existente: CPF ou celular já usado → 409.
- O pedido não carrega id: o servidor resolve o cliente de novo.

**Mesa ocupada**
- O aceite da 1ª solicitação de uma mesa livre grava a `MesaOcupacao`, com um código de 4 dígitos, e dá acesso ao aparelho que pediu.
- Aparelho sem acesso não pede nem vê a mesa; entra com o código.
- A ocupação termina quando a comanda deixa de estar ABERTA. Isso é derivado na leitura, sem job.
- Comanda aberta pelo PDV **não** ocupa a mesa do QR.

**Acompanhante**
- O acesso guarda se veio do código (`salao_mesa_acesso.acompanhante`, V170). Os acessos que vêm do titular são o aceite do pedido dele e o `/entrar`.
- O acesso do titular prevalece: o upsert faz `acompanhante = antigo AND novo`.
- No aceite em mesa já ocupada, quem pediu com outro CPF entra como acompanhante.

**Pedido sem CPF (rodada 3)**
- `/solicitar` sem CPF nem celular, vindo de um aparelho com acesso à ocupação aberta: o cliente é o **titular**.
- Se o aparelho é acompanhante, a solicitação grava `acompanhante = true`. O PDV e o aviso mostram "Acompanhante de &lt;titular&gt;", e a cobrança vai para a comanda do titular.
- O limite de um pedido pendente por pessoa conta **pelo aparelho** no caso do acompanhante (`existsPendenteDoAparelho`). Assim, o pedido pendente do titular não segura o do amigo.
- Sem identificação e sem acesso → 404 `CLIENTE_NAO_IDENTIFICADO`.

**Pagar no final (rodada 3)**
- Qualquer cliente pode pedir; a trava de VIP saiu.
- Quem decide é o atendente, no aceite, pela permissão `PDV_SESSION_PAY_LATER`.

**Mescla com proporção**
- `MEIO_A_MEIO`, `SETENTA_TRINTA` ou `NOVENTA_DEZ`; o sabor 1 é o principal. Sem o 2º sabor, a proporção vira null.
- O preço não muda: continua a faixa mais cara (PDV-F051).
- No aceite, o texto "X 70% + Y 30%" vai como `essencia` do `AddSessionCommand`; acima de 150 caracteres, fica o texto padrão.
- A proporção é gravada também em `comanda_item.essence_ratio`.

**Perfis de sabor**
- São por SKU: há essência como produto simples e como variação.
- SKU que nunca passou pelo PUT recebe `PerfilSabor.sugerir(sabor + produto)`, por palavra-chave, sem acento nem caixa. A sugestão é calculada na leitura e não grava nada.
- Depois do PUT, mesmo com lista vazia, vale só o que a casa marcou.

**Chamados**
- `CONTA` (exige a forma de pagamento), `RECOLHER`, `CARVAO` e `ATENDENTE`.
- Pertencem à **comanda**: a da mesa do QR, pelo aparelho, ou uma comanda do PDV, pela `comandaRef` com a identificação confirmada.
- Um ABERTO por tipo e comanda (409). O ABERTO vence em 30 min na leitura. Na fila do PDV ficam os ABERTOS de comandas abertas e os ATENDIDOS das últimas 12 h.
- O aviso `CHAMADO_MESA` vai para todos com `PDV_COMANDA_MANAGE`, depois do commit.

**Confirmação da identidade** (`/entrar`, `/minhas-comandas` e `/chamados` com `comandaRef`)
- Quem se identificou pelo CPF confirma com os **4 últimos dígitos do celular** do cadastro. Quem se identificou pelo celular confirma com os **3 primeiros do CPF**.
- A comparação é em tempo constante.
- Sem o outro dado no cadastro, não há fallback: a pessoa entra com o código da mesa. É decisão da rodada 3, porque os dígitos do próprio documento digitado não confirmariam nada.
- Cada recusa vai para a auditoria (`SALAO_CONFIRMACAO_RECUSADA`), sem CPF nem celular no payload. A entrada aceita gera `SALAO_MESA_ENTRADA`.

**Trava da confirmação (rodada 3)**
- **5 erros em 15 min** por cliente travam a confirmação até a janela passar, mesmo com a resposta certa: 429 `CONFIRMACAO_BLOQUEADA`, com `Retry-After`.
- A contagem lê as recusas da auditoria (`audit_logs.target = customer:<id>`, índice parcial da V170); não há tabela própria.
- A tentativa já travada é auditada **sem** o cliente, para não estender a trava.
- A auditoria é gravada de forma assíncrona. Rajadas em paralelo podem passar de 5 por alguns milissegundos; o rate limit por IP cobre isso.

## Schema

- **V167** — tabelas:
  - `salao_mesa` (token único; nome único sem diferenciar maiúsculas);
  - `salao_essencia_dia (data, sku)`;
  - `salao_solicitacao` (código UUID público; status `PENDENTE`/`ACEITA`/`RECUSADA`; EXPIRADA é derivada).
- **V168**:
  - tabelas `salao_mesa_ocupacao` e `salao_mesa_acesso (ocupacao_id, device_token)`;
  - na solicitação: `vaso_grande`, `carvao`, `adicional_ids`, `pagar_no_final` e `device_token`;
  - `session_settings.upgrade_carvao_jumbo_preco`.
- **V169**:
  - `salao_solicitacao.proporcao` e `comanda_item.essence_ratio`, ambos com CHECK que exige o 2º sabor;
  - tabelas `salao_essencia_perfil (sku, perfil)` e `salao_essencia_perfil_config (sku)`;
  - tabela `salao_chamado`: `comanda_id NOT NULL`, mais `mesa_id` e `ocupacao_id`, que são nulos juntos; CHECKs de tipo, forma, conta e atendido.
- **V170**:
  - `salao_mesa_acesso.acompanhante` e `salao_solicitacao.acompanhante`;
  - índice parcial `idx_audit_logs_salao_confirmacao_recusada`.

## Testes

- `SalaoAutoatendimentoServiceTest` cobre todas as regras acima: cardápio, identificação, pedido (com e sem CPF, acompanhante), aceite, código, mesa atual, timeline, perfis, chamados, mesas abertas, entrar e trava.
- `ComandaSessaoServiceTest` cobre a proporção na linha da comanda.
- Os ITs `SalaoEssenciasIT` e `SessaoMesclaIT` cobrem o banco.

## Lacunas conhecidas

- A sugestão de perfis é só por palavra-chave (a lista portada do front): sabor de nome fantasia fica sem perfil até a casa marcar.
- Não há som nem prioridade diferente para o chamado de CONTA. O aviso é o mesmo `CHAMADO_MESA`.
- A forma de pagamento escolhida no chamado de CONTA ainda não pré-seleciona nada no fechamento da comanda.
