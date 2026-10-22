package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.SalaoAcessoRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoCadastroRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoChamadoRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoCompletarRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoEntrarRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoIdentificacaoRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoMinhasComandasRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoPedidoRequest;
import com.cernecommerce.adapter.in.dtos.response.CardapioDaMesaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ChamadoDaMesaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.IdentificacaoResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MesaAbertaDoClienteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MesaAtualResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SolicitacaoPublicaResponseDTO;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Identificacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A página do QR da mesa — <b>pública</b>, sem login. O token da mesa é a única credencial; ver
 * {@code SecurityConfig} (permitAll) e {@code ResourceRateLimitingFilter} (limite por IP).
 *
 * <p>Nada aqui devolve id de cliente, e-mail, CPF ou telefone: identificar responde só se achou, o
 * primeiro nome, se é VIP e o que falta no cadastro.</p>
 *
 * <p>{@code dispositivo} é o token do aparelho, gerado no navegador. Vai na query (GET) ou no corpo
 * (POST) — não em cabeçalho próprio, que o CORS de produção não libera.</p>
 */
@RestController
@RequestMapping("/public/salao/{token}")
@Tag(name = "Salão (QR público)", description = "Página do QR da mesa: cardápio, identificação e pedido de sessão")
@Validated
public class SalaoPublicoController {

    private final SalaoAutoatendimentoUseCase useCase;

    public SalaoPublicoController(SalaoAutoatendimentoUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Cardápio da mesa", description = "Faixas ativas e essências oferecidas: com faixa e "
            + "lata aberta ou só fechada (disponibilidade ABERTA/FECHADA). Destaques = a seleção do dia ou, sem "
            + "ela, o modelo do dia da semana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "QR inválido ou mesa desativada (SALAO_MESA_NOT_FOUND)", content = @Content)
    })
    @GetMapping
    public ResponseEntity<CardapioDaMesaResponseDTO> cardapio(@PathVariable @Size(max = 64) String token,
            @RequestParam(required = false) @Size(max = 64) String dispositivo) {
        return ResponseEntity.ok(CardapioDaMesaResponseDTO.of(useCase.cardapioPorToken(token, dispositivo)));
    }

    @Operation(summary = "Identifica pelo CPF ou celular", description = "Se achou: o primeiro nome, VIP, o que falta "
            + "no cadastro e as comandas abertas do cliente — como aviso: token e valor só da mesa deste QR.")
    @PostMapping("/identificar")
    public ResponseEntity<IdentificacaoResponseDTO> identificar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoIdentificacaoRequest request) {
        return ResponseEntity.ok(useCase.identificarPorToken(token, new Identificacao(request.cpf(), request.celular()))
                .map(IdentificacaoResponseDTO::of)
                .orElseGet(IdentificacaoResponseDTO::naoEncontrado));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cadastrado"),
            @ApiResponse(responseCode = "409", description = "CPF ou celular já cadastrado (CLIENTE_JA_CADASTRADO)", content = @Content)
    })
    @PostMapping("/cadastrar")
    public ResponseEntity<IdentificacaoResponseDTO> cadastrar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoCadastroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(IdentificacaoResponseDTO.of(useCase.cadastrarPorToken(
                token, new SalaoAutoatendimentoUseCase.Cadastro(request.nome(), request.celular(), request.cpf(),
                        request.dataNascimento()))));
    }

    @Operation(summary = "Completa o cadastro", description = "Só preenche o que está vazio no cadastro de quem se "
            + "identificou; nunca troca um dado gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cadastro completo"),
            @ApiResponse(responseCode = "404", description = "Cliente não identificado (CLIENTE_NAO_IDENTIFICADO)", content = @Content),
            @ApiResponse(responseCode = "409", description = "CPF ou celular de outro cadastro (CLIENTE_JA_CADASTRADO)", content = @Content)
    })
    @PostMapping("/completar")
    public ResponseEntity<IdentificacaoResponseDTO> completar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoCompletarRequest request) {
        return ResponseEntity.ok(IdentificacaoResponseDTO.of(useCase.completarPorToken(token,
                new SalaoAutoatendimentoUseCase.Completar(new Identificacao(request.cpf(), request.celular()),
                        request.novoCelular(), request.novoCpf(), request.dataNascimento()))));
    }

    @Operation(summary = "Pede uma sessão", description = "Com CPF ou celular; sem eles, só o aparelho com acesso à "
            + "mesa ocupada, e o cliente é o titular dela (quem entrou pelo código pede como acompanhante). "
            + "Pagar no final vale para todos — o lead pede, o atendente confirma ou cobra agora no aceite. Mesa livre fica ocupada por este "
            + "aparelho já no envio: codigoMesa vem com o pedido pendente. Recusado ou expirado sem nada lançado, "
            + "a mesa fica livre.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido enviado ao atendente"),
            @ApiResponse(responseCode = "403", description = "Mesa com outro cliente (MESA_OCUPADA)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não identificado, ou sem CPF/celular e sem acesso à mesa (CLIENTE_NAO_IDENTIFICADO)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Essência indisponível (ESSENCIA_INDISPONIVEL) ou pedido já pendente (SOLICITACAO_JA_PENDENTE)", content = @Content)
    })
    @PostMapping("/solicitar")
    public ResponseEntity<SolicitacaoPublicaResponseDTO> solicitar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoPedidoRequest request) {
        SolicitacaoSessao s = useCase.solicitarPorToken(token, new SalaoAutoatendimentoUseCase.Pedido(
                new Identificacao(request.cpf(), request.celular()), request.essenciaSku(), request.essenciaSku2(),
                request.observacao(), new EscolhasSessao(request.vasoGrande(), request.carvao(), request.adicionalIds(),
                        request.pagarNoFinal(), request.proporcao()), request.dispositivo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toPublico(token, s, request.dispositivo()));
    }

    @GetMapping("/solicitacoes/{codigo}")
    public ResponseEntity<SolicitacaoPublicaResponseDTO> solicitacao(@PathVariable @Size(max = 64) String token,
            @PathVariable UUID codigo, @RequestParam(required = false) @Size(max = 64) String dispositivo) {
        return ResponseEntity.ok(toPublico(token, useCase.solicitacaoPorToken(token, codigo), dispositivo));
    }

    @Operation(summary = "Entra na mesa ocupada com o código", description = "Libera este aparelho para ver a mesa "
            + "e pedir. Mesa livre: nada a fazer, 204 do mesmo jeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aparelho liberado"),
            @ApiResponse(responseCode = "403", description = "Código incorreto (CODIGO_MESA_INVALIDO)", content = @Content)
    })
    @PostMapping("/acesso")
    public ResponseEntity<Void> acessar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoAcessoRequest request) {
        useCase.acessarPorToken(token, request.codigo(), request.dispositivo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "A mesa ocupada", description = "O código, o que foi lançado (toda linha de sessão com "
            + "a linha do tempo em itens[].sessao), quanto falta pagar, os chamados, a última sessão e as compras "
            + "da loja ligadas à mesa (comprasLoja). Com o pedido ainda pendente, só o código. Só para aparelho "
            + "com acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem acesso ou mesa livre (MESA_OCUPADA)", content = @Content)
    })
    @GetMapping("/mesa-atual")
    public ResponseEntity<MesaAtualResponseDTO> mesaAtual(@PathVariable @Size(max = 64) String token,
            @RequestParam @Size(max = 64) String dispositivo) {
        return ResponseEntity.ok(MesaAtualResponseDTO.of(useCase.mesaAtualPorToken(token, dispositivo)));
    }

    @Operation(summary = "Chama a equipe", description = "Conta (com a forma de pagamento), recolher, carvão ou "
            + "atendente. Sem comandaRef, na mesa deste QR, pelo aparelho com acesso. Com comandaRef, na comanda do "
            + "cliente aberta pelo PDV, com CPF/celular e a confirmação. Avisa quem opera mesa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipe avisada"),
            @ApiResponse(responseCode = "400", description = "Conta sem forma de pagamento (SALAO_DADOS_INVALIDOS)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem acesso à mesa, pedido ainda pendente ou comanda de outro cliente (MESA_OCUPADA); confirmação errada (CONFIRMACAO_INVALIDA)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não identificado (CLIENTE_NAO_IDENTIFICADO)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já há um chamado aberto do mesmo tipo (CHAMADO_JA_ABERTO)", content = @Content),
            @ApiResponse(responseCode = "429", description = "5 confirmações erradas em 15 min (CONFIRMACAO_BLOQUEADA)", content = @Content)
    })
    @PostMapping("/chamados")
    public ResponseEntity<ChamadoDaMesaResponseDTO> chamar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoChamadoRequest request) {
        Identificacao identificacao = request.cpf() == null && request.celular() == null ? null
                : new Identificacao(request.cpf(), request.celular());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChamadoDaMesaResponseDTO.of(useCase.chamarPorToken(token,
                new SalaoAutoatendimentoUseCase.NovoChamado(request.tipo(), request.formaPagamento(),
                        request.dispositivo(), request.comandaRef(), identificacao, request.confirmacao()))));
    }

    @Operation(summary = "Entra na própria mesa pelo CPF/celular", description = "Para o titular da mesa ocupada: "
            + "libera este aparelho sem o código, com a confirmação — os 4 últimos dígitos do celular (veio pelo CPF) "
            + "ou os 3 primeiros do CPF (veio pelo celular). Cada tentativa vai para a auditoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aparelho liberado"),
            @ApiResponse(responseCode = "403", description = "Mesa livre ou de outro cliente (MESA_OCUPADA); confirmação errada (CONFIRMACAO_INVALIDA)", content = @Content),
            @ApiResponse(responseCode = "429", description = "5 confirmações erradas em 15 min (CONFIRMACAO_BLOQUEADA)", content = @Content)
    })
    @PostMapping("/entrar")
    public ResponseEntity<Void> entrar(@PathVariable @Size(max = 64) String token,
            @Valid @RequestBody SalaoEntrarRequest request) {
        useCase.entrarPorToken(token, new Identificacao(request.cpf(), request.celular()), request.confirmacao(),
                request.dispositivo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "As comandas abertas do cliente", description = "Completas — token, consumo (com "
            + "itens[].sessao), valor, chamados e compras da loja —, com a mesma confirmação do /entrar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK (vazia sem comanda aberta)"),
            @ApiResponse(responseCode = "403", description = "Confirmação errada (CONFIRMACAO_INVALIDA)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não identificado (CLIENTE_NAO_IDENTIFICADO)", content = @Content),
            @ApiResponse(responseCode = "429", description = "5 confirmações erradas em 15 min (CONFIRMACAO_BLOQUEADA)", content = @Content)
    })
    @PostMapping("/minhas-comandas")
    public ResponseEntity<List<MesaAbertaDoClienteResponseDTO>> minhasComandas(
            @PathVariable @Size(max = 64) String token, @Valid @RequestBody SalaoMinhasComandasRequest request) {
        return ResponseEntity.ok(useCase.minhasComandasPorToken(token,
                        new Identificacao(request.cpf(), request.celular()), request.confirmacao()).stream()
                .map(MesaAbertaDoClienteResponseDTO::of).toList());
    }

    private SolicitacaoPublicaResponseDTO toPublico(String token, SolicitacaoSessao s, String dispositivo) {
        SolicitacaoStatus status = useCase.statusPublico(s);
        // A mesa fica com quem pediu já no envio: o código vale desde o pedido pendente.
        String codigoMesa = (status == SolicitacaoStatus.PENDENTE || status == SolicitacaoStatus.ACEITA)
                && dispositivo != null
                ? useCase.codigoDaMesa(token, dispositivo).orElse(null) : null;
        return new SolicitacaoPublicaResponseDTO(s.codigo(), status, s.motivo(), s.criadoEm(), codigoMesa,
                status == SolicitacaoStatus.ACEITA ? s.pagarNoFinalAceito() : null);
    }
}
