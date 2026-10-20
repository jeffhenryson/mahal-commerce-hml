package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;
import com.cernecommerce.core.domain.model.salao.MesaAtual;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autoatendimento do Salão: o lead escaneia o QR da mesa, se identifica pelo CPF ou celular (ou se
 * cadastra, com os dados completos), escolhe a essência entre as latas abertas, o vaso, o carvão e
 * os adicionais, e pede a sessão; o atendente aceita no PDV e a mesa abre com a sessão lançada.
 *
 * <p>O aceite da 1ª solicitação de uma mesa livre a <b>ocupa</b> ({@link MesaOcupacao}): só o
 * aparelho que pediu — ou quem digitar o código de 4 dígitos — pede ou vê a mesa até a comanda ser
 * encerrada. O aparelho é um token opaco gerado no navegador ({@code deviceToken}).</p>
 *
 * <p>O lado público ({@code *PorToken}) nunca devolve id nem dado pessoal do cliente: identificar
 * responde só se achou, o primeiro nome mascarado, se é VIP e o que falta no cadastro; o pedido
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

    /** Todas as oferecidas agora (latas abertas), cada uma marcada se é destaque do dia {@code data}. */
    List<EssenciaDoDia> listEssenciasDoDia(LocalDate data);

    void setEssenciasDoDia(LocalDate data, Collection<String> skus);

    /** Copia a seleção de {@code de} para {@code para}, substituindo a de {@code para}. */
    void copiarEssenciasDoDia(LocalDate de, LocalDate para);

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
     * A mesa ocupada como o aparelho com acesso a vê.
     *
     * @throws com.cernecommerce.core.domain.exception.salao.MesaOcupadaException sem acesso, ou mesa livre
     */
    MesaAtual mesaAtualPorToken(String token, String deviceToken);

    // ── PDV: a fila de solicitações ───────────────────────────────────────────────────────────

    PageResult<SolicitacaoResumo> listSolicitacoes(SolicitacaoStatus status, int page, int size);

    /**
     * Abre a mesa com o cliente — ou usa a comanda ainda aberta da mesa — e lança a sessão com o que o
     * lead escolheu. Tudo numa transação: falhou o lançamento (sem lata, sem utensílio), a mesa não
     * fica aberta e o pedido segue pendente. Mesa livre fica ocupada pelo aparelho que pediu.
     *
     * @param podePagarNoFinal o atendente tem PDV_SESSION_PAY_LATER — exigido só se o lead escolheu
     *        pagar no final
     * @throws com.cernecommerce.core.domain.exception.salao.SolicitacaoNaoPendenteException
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException
     */
    SolicitacaoResumo aceitar(Long id, Long cashSessionId, String username, boolean podePagarNoFinal);

    SolicitacaoResumo recusar(Long id, String motivo, String username);

    record Identificacao(String cpf, String celular) {
    }

    record Cadastro(String nome, String celular, String cpf, LocalDate dataNascimento) {
    }

    record Completar(Identificacao identificacao, String celular, String cpf, LocalDate dataNascimento) {
    }

    /**
     * @param nome primeiro nome mascarado ("Ma***")
     * @param faltando campos que o cadastro ainda não tem: {@code celular}, {@code cpf},
     *        {@code dataNascimento}
     */
    record Identificado(String nome, boolean vip, List<String> faltando) {
        public Identificado {
            faltando = List.copyOf(faltando);
        }
    }

    record Pedido(Identificacao identificacao, String essenciaSku, String essenciaSku2, String observacao,
            EscolhasSessao escolhas, String deviceToken) {
    }
}
