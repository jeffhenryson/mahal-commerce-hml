package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.CentralPedidosConfigInvalidaException;
import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Como a loja recebe pedidos sem passar pelo balcão (ECM-F013): app e QR da mesa.
 *
 * <p>Uma configuração só, substituída inteira pelo {@code PUT /central-pedidos/config}. As regras de
 * validação espelham as da tela (frontend-admin, {@code errosDaConfig}), para o erro do servidor
 * nunca pegar a loja de surpresa.</p>
 *
 * <h2>O que cada campo governa</h2>
 * <ul>
 *   <li>{@code appRecebendo}, {@code horarios}, {@code entrega}, {@code retiradaHabilitada}: o checkout do app.</li>
 *   <li>{@code qrRecebendo}, {@code expiracaoSolicitacaoMin}: a solicitação de sessão pelo QR da mesa.</li>
 *   <li>{@code tempoPreparoMin}, {@code tempoEntregaMin}: só estimativa mostrada no app.</li>
 *   <li>{@code aceiteAutomatico}: só gravado por ora — o aceite exige caixa aberto e, na entrega, a taxa.</li>
 *   <li>{@code entrega.raioKm}: só gravado — não há geocodificação do endereço.</li>
 *   <li>{@code pixChave}: vai no aviso de aceite da entrega, que o cliente paga por Pix.</li>
 * </ul>
 *
 * @param horarios vazio = sempre aberto. Um por dia da semana; {@code fecha} antes de {@code abre} vira a noite.
 */
public record CentralPedidosConfig(
        boolean appRecebendo,
        boolean qrRecebendo,
        List<Horario> horarios,
        boolean aceiteAutomatico,
        int tempoPreparoMin,
        int tempoEntregaMin,
        int expiracaoSolicitacaoMin,
        Entrega entrega,
        boolean retiradaHabilitada,
        String pixChave) {

    public static final int EXPIRACAO_MINIMA_MIN = 5;
    public static final int PIX_CHAVE_MAX = 120;

    /** @param diaSemana ISO: 1 = segunda … 7 = domingo. */
    public record Horario(int diaSemana, LocalTime abre, LocalTime fecha) {

        boolean viraANoite() {
            return fecha.isBefore(abre);
        }
    }

    /** @param raioKm nulo = sem limite por raio. */
    public record Entrega(boolean habilitada, BigDecimal taxaFixa, BigDecimal pedidoMinimo, BigDecimal raioKm,
            List<TaxaBairro> bairros) {

        public Entrega {
            taxaFixa = Money.of(taxaFixa == null ? BigDecimal.ZERO : taxaFixa);
            pedidoMinimo = Money.of(pedidoMinimo == null ? BigDecimal.ZERO : pedidoMinimo);
            bairros = bairros == null ? List.of() : List.copyOf(bairros);
        }
    }

    /** Taxa própria de um bairro; sobrescreve a {@code taxaFixa}. */
    public record TaxaBairro(String nome, BigDecimal taxa) {

        public TaxaBairro {
            nome = nome == null ? null : nome.strip();
            taxa = Money.of(taxa);
        }
    }

    public CentralPedidosConfig {
        horarios = horarios == null ? List.of() : List.copyOf(horarios);
        if (entrega == null) {
            entrega = new Entrega(true, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of());
        }
        pixChave = pixChave == null || pixChave.isBlank() ? null : pixChave.strip();
        List<String> erros = erros(horarios, tempoPreparoMin, tempoEntregaMin, expiracaoSolicitacaoMin, entrega,
                appRecebendo, retiradaHabilitada, pixChave);
        if (!erros.isEmpty()) {
            throw new CentralPedidosConfigInvalidaException(String.join(" ", erros));
        }
    }

    /** O que vale enquanto a loja nunca salvou nada — igual ao {@code configPadrao()} da tela. */
    public static CentralPedidosConfig padrao() {
        return new CentralPedidosConfig(true, true, List.of(), false, 20, 40, 30,
                new Entrega(true, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of()), true, null);
    }

    /**
     * A loja está aberta neste instante, pelo horário local da loja? Sem horário, sempre. O horário que
     * vira a noite (18:00 → 02:00) vale no dia em que abre e na madrugada seguinte.
     */
    public boolean abertoEm(ZonedDateTime local) {
        if (horarios.isEmpty()) {
            return true;
        }
        int hoje = local.getDayOfWeek().getValue();
        int ontem = hoje == 1 ? 7 : hoje - 1;
        LocalTime agora = local.toLocalTime();
        return horarios.stream().anyMatch(h -> {
            if (h.diaSemana() == hoje) {
                return h.viraANoite() ? !agora.isBefore(h.abre())
                        : !agora.isBefore(h.abre()) && agora.isBefore(h.fecha());
            }
            return h.viraANoite() && h.diaSemana() == ontem && agora.isBefore(h.fecha());
        });
    }

    /** O app aceita pedido agora: recebendo e dentro do horário. */
    public boolean appAbertoEm(ZonedDateTime local) {
        return appRecebendo && abertoEm(local);
    }

    /** O bairro está na lista de bairros atendidos? Lista vazia = atende todos. Ignora acento e caixa. */
    public boolean atendeBairro(String bairro) {
        return entrega.bairros().isEmpty() || bairroDe(bairro).isPresent();
    }

    /** Taxa sugerida para o bairro: a dele, se listado; senão a fixa. */
    public BigDecimal taxaPara(String bairro) {
        return bairroDe(bairro).map(TaxaBairro::taxa).orElse(entrega.taxaFixa());
    }

    private Optional<TaxaBairro> bairroDe(String bairro) {
        if (bairro == null || bairro.isBlank()) {
            return Optional.empty();
        }
        String chave = normalizar(bairro);
        return entrega.bairros().stream().filter(b -> normalizar(b.nome()).equals(chave)).findFirst();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> erros(List<Horario> horarios, int tempoPreparoMin, int tempoEntregaMin,
            int expiracaoSolicitacaoMin, Entrega entrega, boolean appRecebendo, boolean retiradaHabilitada,
            String pixChave) {
        List<String> erros = new ArrayList<>();
        Set<Integer> dias = new HashSet<>();
        for (Horario h : horarios) {
            if (h == null || h.abre() == null || h.fecha() == null || h.diaSemana() < 1 || h.diaSemana() > 7) {
                erros.add("Horário inválido — dia de 1 a 7 e horas em HH:MM.");
                continue;
            }
            if (h.abre().equals(h.fecha())) {
                erros.add("Horário com abertura igual ao fechamento.");
            }
            if (!dias.add(h.diaSemana())) {
                erros.add("Dia da semana repetido nos horários.");
            }
        }
        if (tempoPreparoMin <= 0) {
            erros.add("Tempo de preparo precisa ser maior que zero.");
        }
        if (entrega.habilitada() && tempoEntregaMin <= 0) {
            erros.add("Tempo de entrega precisa ser maior que zero.");
        }
        if (expiracaoSolicitacaoMin < EXPIRACAO_MINIMA_MIN) {
            erros.add("A sessão pedida pelo QR precisa valer pelo menos " + EXPIRACAO_MINIMA_MIN + " min.");
        }
        if (entrega.taxaFixa().signum() < 0 || entrega.pedidoMinimo().signum() < 0) {
            erros.add("Taxa e pedido mínimo não podem ser negativos.");
        }
        if (entrega.raioKm() != null && entrega.raioKm().signum() <= 0) {
            erros.add("Raio de entrega precisa ser maior que zero.");
        }
        Set<String> nomes = new HashSet<>();
        for (TaxaBairro b : entrega.bairros()) {
            if (b == null || b.nome() == null || b.nome().isEmpty()) {
                erros.add("Bairro sem nome.");
                continue;
            }
            if (!nomes.add(normalizar(b.nome()))) {
                erros.add("Bairro repetido.");
            }
            if (b.taxa() == null || b.taxa().signum() < 0) {
                erros.add("Taxa de bairro não pode ser negativa.");
            }
        }
        if (appRecebendo && !entrega.habilitada() && !retiradaHabilitada) {
            erros.add("Com o app recebendo, deixe a entrega ou a retirada ligada.");
        }
        if (pixChave != null && pixChave.length() > PIX_CHAVE_MAX) {
            erros.add("Chave Pix com mais de " + PIX_CHAVE_MAX + " caracteres.");
        }
        return erros.stream().distinct().toList();
    }
}
