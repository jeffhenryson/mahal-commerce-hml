package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.MesaAbertaDoCliente;
import com.cernecommerce.core.domain.model.salao.MesaAtual;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.OrigemSelecao;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Autoatendimento do Salão: o lead escaneia o QR da mesa, se identifica pelo CPF ou celular (ou se
 * cadastra, com os dados completos), escolhe a essência entre as latas abertas, o vaso, o carvão e
 * os adicionais, e pede a sessão; o atendente aceita no PDV e a mesa abre com a sessão lançada.
 *
 * <p>O envio da 1ª solicitação de uma mesa livre a <b>ocupa</b> ({@link MesaOcupacao}): só o
 * aparelho que pediu — ou quem digitar o código de 4 dígitos — pede ou vê a mesa enquanto o pedido
 * estiver pendente e, depois do aceite, até a comanda ser encerrada. Recusado ou expirado sem nada
 * lançado, a mesa fica livre. O aparelho é um token opaco gerado no navegador ({@code deviceToken}).</p>
 *
 * <p>O lado público ({@code *PorToken}) nunca devolve id nem dado pessoal do cliente: identificar
 * responde só se achou, o primeiro nome, se é VIP e o que falta no cadastro; o pedido
 * resolve o cliente de novo no servidor pelo mesmo CPF/celular.</p>
 */
public interface SalaoAutoatendimentoUseCase {

    // ── Admin: mesas e destaques do dia ───────────────────────────────────────────────────────

    List<SalaoMesa> listMesas();

    /** @throws com.cernecommerce.core.domain.exception.salao.SalaoMesaNomeDuplicadoException */
    SalaoMesa createMesa(String nome);

    SalaoMesa updateMesa(Long id, String nome, boolean ativo);

    /** Token novo: o QR impresso da mesa deixa de funcionar. */
    SalaoMesa regenerarToken(Long id);

    /** As ocupações ainda abertas (comanda ABERTA), uma por mesa — o atendente vê o código. */
    List<MesaOcupacao> ocupacoesAbertas();

    /**
     * Todas as essências do Salão — lata aberta, só fechada ou sem faixa —, cada uma marcada se é
     * destaque do dia {@code data} (a seleção da data ou, sem ela, o modelo do dia da semana), com a
     * origem da seleção. As duas saem da mesma leitura (PDV-C063).
     */
    SelecaoDoDia essenciasDoDia(LocalDate data);

    void setEssenciasDoDia(LocalDate data, Collection<String> skus);

    /** Copia a seleção de {@code de} para {@code para}, substituindo a de {@code para}. */
    void copiarEssenciasDoDia(LocalDate de, LocalDate para);

    /** O modelo semanal: dia da semana (1 = segunda … 7 = domingo) → SKUs. Dia sem seleção não volta. */
    Map<Integer, Set<String>> modeloSemanal();

    /**
     * Substitui o modelo do dia da semana. Vazio limpa.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException dia fora de 1..7
     */
    void setModelo(int diaSemana, Collection<String> skus);

    /**
     * Copia o modelo de {@code de} para cada dia de {@code para}, substituindo o deles.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException dia fora de
     *         1..7 ou sem destino
     */
    void copiarModelo(int de, Collection<Integer> para);

    /**
     * Substitui os perfis de sabor de cada SKU listado (lista vazia limpa). Configurado o SKU, o QR
     * deixa de sugerir perfis pelo nome dele.
     */
    void salvarPerfis(List<PerfisDoSku> itens);

    /** Hoje, no fuso do salão. */
    LocalDate hoje();

    // ── Público: a página do QR ───────────────────────────────────────────────────────────────

    /** @throws com.cernecommerce.core.domain.exception.salao.SalaoMesaNotFoundException token inválido ou mesa inativa */
    CardapioDaMesa cardapioPorToken(String token, String deviceToken);

    /** Quem tem o CPF ou celular; vazio se ninguém. */
    Optional<Identificado> identificarPorToken(String token, Identificacao identificacao);

    /**
     * Cadastra o lead (NOVO_LEAD, origem "QR Salão"). Nome, celular, CPF e nascimento obrigatórios.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.ClienteJaCadastradoException CPF ou
     *         celular já usado — quem tem cadastro se identifica; o cadastro público nunca mexe em
     *         cliente existente
     */
    Identificado cadastrarPorToken(String token, Cadastro cadastro);

    /**
     * Completa o cadastro de quem se identificou: só preenche o que está vazio, nunca troca um dado
     * gravado.
     */
    Identificado completarPorToken(String token, Completar completar);

    /**
     * Pede a sessão. Mesa livre fica ocupada por este aparelho já no envio, com o código de 4 dígitos.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.ClienteNaoIdentificadoException
     * @throws com.cernecommerce.core.domain.exception.salao.EssenciaIndisponivelException
     * @throws com.cernecommerce.core.domain.exception.salao.SolicitacaoJaPendenteException
     * @throws com.cernecommerce.core.domain.exception.salao.MesaOcupadaException mesa de outro aparelho
     */
    SolicitacaoSessao solicitarPorToken(String token, Pedido pedido);

    /** Status visto pelo lead. Só acha a solicitação da própria mesa. */
    SolicitacaoSessao solicitacaoPorToken(String token, UUID codigo);

    /** Status já derivado (EXPIRADA na leitura). */
    SolicitacaoStatus statusPublico(SolicitacaoSessao solicitacao);

    /** O código da mesa ocupada, se este aparelho tem acesso a ela. */
    Optional<String> codigoDaMesa(String token, String deviceToken);

    /**
     * Libera a mesa ocupada para este aparelho.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.CodigoMesaInvalidoException
     */
    void acessarPorToken(String token, String codigo, String deviceToken);

    /**
     * A mesa ocupada como o aparelho com acesso a vê. Com o pedido ainda pendente, sem consumo — só o
     * código.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.MesaOcupadaException sem acesso, ou mesa livre
     */
    MesaAtual mesaAtualPorToken(String token, String deviceToken);

    /**
     * Chamado à equipe. Sem {@code comandaRef}, na mesa deste QR, pelo aparelho com acesso; com ela,
     * na comanda do cliente (a do PDV, sem QR), pela identificação confirmada. Avisa quem tem
     * PDV_COMANDA_MANAGE depois do commit.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.MesaOcupadaException sem acesso, mesa livre,
     *         ou comanda que não é do cliente
     * @throws com.cernecommerce.core.domain.exception.salao.ConfirmacaoInvalidaException
     * @throws com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException conta
     *         sem forma de pagamento
     * @throws com.cernecommerce.core.domain.exception.salao.ChamadoJaAbertoException já há um aberto do tipo
     */
    ChamadoMesa chamarPorToken(String token, NovoChamado chamado);

    /**
     * Libera este aparelho na mesa de quem se identificou, sem o código de 4 dígitos — mas com a
     * confirmação: os 4 últimos dígitos do celular cadastrado (quem entra pelo CPF) ou os 3 primeiros
     * do CPF (quem entra pelo celular). Cada tentativa vai para a auditoria.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.MesaOcupadaException mesa livre ou de outro cliente
     * @throws com.cernecommerce.core.domain.exception.salao.ConfirmacaoInvalidaException
     */
    void entrarPorToken(String token, Identificacao identificacao, String confirmacao, String deviceToken);

    /**
     * As comandas abertas do cliente, completas (tokens, consumo, valor e chamados), pela
     * identificação confirmada como no {@link #entrarPorToken}.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.ClienteNaoIdentificadoException
     * @throws com.cernecommerce.core.domain.exception.salao.ConfirmacaoInvalidaException
     */
    List<MesaAbertaDoCliente> minhasComandasPorToken(String token, Identificacao identificacao, String confirmacao);

    // ── PDV: a fila de solicitações ───────────────────────────────────────────────────────────

    PageResult<SolicitacaoResumo> listSolicitacoes(SolicitacaoStatus status, int page, int size);

    /**
     * Abre a mesa com o cliente — ou usa a comanda ainda aberta da mesa — e lança a sessão com o que o
     * lead escolheu. Tudo numa transação: falhou o lançamento (sem lata, sem utensílio), a mesa não
     * fica aberta e o pedido segue pendente. A ocupação feita no envio ganha a comanda (o código não
     * muda); mesa livre sem ocupação fica ocupada pelo aparelho que pediu.
     *
     * <p>Pagar no final: qualquer cliente pede, o atendente confirma. {@code pagarNoFinal} falso aceita a
     * sessão cobrando agora; nulo mantém o que o lead pediu. O atendente nunca impõe pagar no final a
     * quem não pediu.</p>
     *
     * @param podePagarNoFinal o atendente tem PDV_SESSION_PAY_LATER — exigido só se a sessão vai mesmo
     *        ser paga no final
     * @param pagarNoFinal a decisão do atendente; nula = como o lead pediu
     * @throws com.cernecommerce.core.domain.exception.salao.SolicitacaoNaoPendenteException
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException
     */
    SolicitacaoResumo aceitar(Long id, Long cashSessionId, String username, boolean podePagarNoFinal,
            Boolean pagarNoFinal);

    /** Aceita como o lead pediu — ver {@link #aceitar(Long, Long, String, boolean, Boolean)}. */
    default SolicitacaoResumo aceitar(Long id, Long cashSessionId, String username, boolean podePagarNoFinal) {
        return aceitar(id, cashSessionId, username, podePagarNoFinal, null);
    }

    SolicitacaoResumo recusar(Long id, String motivo, String username);

    // ── PDV: chamados da mesa ─────────────────────────────────────────────────────────────────

    /**
     * ABERTO: os que ainda valem (menos de 30 min) das mesas ainda abertas, mais antigos primeiro.
     * ATENDIDO: os atendidos nas últimas 12 h.
     */
    List<ChamadoResumo> listChamados(StatusChamado status);

    /**
     * Marca o chamado como atendido. Idempotente: o já atendido volta como está.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.ChamadoNotFoundException
     */
    ChamadoResumo atenderChamado(Long id, String username);

    record Identificacao(String cpf, String celular) {
    }

    record Cadastro(String nome, String celular, String cpf, LocalDate dataNascimento) {
    }

    record Completar(Identificacao identificacao, String celular, String cpf, LocalDate dataNascimento) {
    }

    /**
     * @param nome o primeiro nome ("Maria"), sem sobrenome
     * @param faltando campos que o cadastro ainda não tem: {@code celular}, {@code cpf},
     *        {@code dataNascimento}
     * @param mesasAbertas as comandas abertas do cliente; token e valor só da mesa deste QR
     */
    record Identificado(String nome, boolean vip, List<String> faltando, List<MesaAbertaDoCliente> mesasAbertas) {
        public Identificado {
            faltando = List.copyOf(faltando);
            mesasAbertas = mesasAbertas == null ? List.of() : List.copyOf(mesasAbertas);
        }

        public Identificado(String nome, boolean vip, List<String> faltando) {
            this(nome, vip, faltando, List.of());
        }
    }

    /** {@code origem} DIA: a data tem seleção própria. MODELO: vale o modelo do dia da semana. */
    record SelecaoDoDia(OrigemSelecao origem, List<EssenciaDoDia> essencias) {
    }

    record PerfisDoSku(String sku, List<PerfilSabor> perfis) {
        public PerfisDoSku {
            perfis = perfis == null ? List.of() : List.copyOf(perfis);
        }
    }

    /**
     * @param formaPagamento obrigatória em CONTA, ignorada nos outros tipos
     * @param comandaRef nula: a mesa deste QR, pelo aparelho. Com ela, a comanda do cliente, e aí
     *        {@code identificacao} e {@code confirmacao} são obrigatórias
     */
    record NovoChamado(TipoChamado tipo, FormaPagamentoChamado formaPagamento, String deviceToken,
            String comandaRef, Identificacao identificacao, String confirmacao) {

        public NovoChamado(TipoChamado tipo, FormaPagamentoChamado formaPagamento, String deviceToken) {
            this(tipo, formaPagamento, deviceToken, null, null, null);
        }
    }

    /** O chamado como o PDV o vê: a mesa e o cliente por nome. */
    record ChamadoResumo(ChamadoMesa chamado, String mesa, String cliente) {
    }

    record Pedido(Identificacao identificacao, String essenciaSku, String essenciaSku2, String observacao,
            EscolhasSessao escolhas, String deviceToken) {
    }
}
