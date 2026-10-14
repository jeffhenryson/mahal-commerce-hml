package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Cliente do módulo CRM. Fundação sobre a qual as demais features de CRM
 * (listagem/RFM, perfil 360, kanban de estágio) são construídas.
 *
 * <h2>Identificação: CPF é oficial, email e contato são alternativos (CRM-C005)</h2>
 * <p>{@code cpf} é o identificador <b>oficial</b> do cadastro. Um cliente sem CPF — "cliente
 * leve", identificado só por {@code email} e/ou {@code contato} — é válido e pesquisável, mas não
 * é elegível a cashback quando o programa existir (Fatia 4): ver {@link #isOfficiallyRegistered()}.
 * A venda anônima de balcão continua não exigindo nenhum dos três — o gatilho para identificar é o
 * operador querer o "CPF na nota?", não a venda em si.</p>
 *
 * <p>Pelo menos um entre {@code cpf}, {@code email} e {@code contato} é obrigatório: um cliente
 * sem nenhum identificador não é encontrável de novo, e deixaria de existir na prática.</p>
 *
 * <p>{@code dataNascimento} (CRM-F012) é opcional e base da automação de aniversário. A regra de
 * idade (18 a 120 anos) é do cadastro, no serviço — não daqui: um registro lido do banco não pode
 * deixar de ser válido só porque o tempo passou.</p>
 */
public record Customer(
    Long id,
    String nome,
    String contato,
    String email,
    String cpf,
    String origem,
    Instant cadastradoEm,
    CustomerStage estagio,
    LocalDate dataNascimento
) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Customer {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        boolean hasContato = contato != null && !contato.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasCpf = cpf != null && !cpf.isBlank();
        if (!hasContato && !hasEmail && !hasCpf) {
            throw new IllegalArgumentException(
                    "cliente precisa de ao menos um identificador: cpf, email ou contato");
        }
        if (hasEmail && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email possui formato inválido");
        }
        if (estagio == null) {
            throw new IllegalArgumentException("estagio é obrigatório");
        }
    }

    /** Cliente sem data de nascimento — a forma anterior a CRM-F012. */
    public Customer(Long id, String nome, String contato, String email, String cpf, String origem,
            Instant cadastradoEm, CustomerStage estagio) {
        this(id, nome, contato, email, cpf, origem, cadastradoEm, estagio, null);
    }

    /** Cria um novo cliente (sem id, cadastradoEm no momento atual, estágio inicial NOVO_LEAD). */
    public static Customer create(String nome, String contato, String email, String cpf, String origem) {
        return new Customer(null, nome, contato, email, cpf, origem, Instant.now(), CustomerStage.NOVO_LEAD);
    }

    /** Reconstitui um cliente a partir de persistência. */
    public static Customer of(Long id, String nome, String contato, String email, String cpf, String origem,
            Instant cadastradoEm, CustomerStage estagio) {
        return of(id, nome, contato, email, cpf, origem, cadastradoEm, estagio, null);
    }

    /** Reconstitui um cliente a partir de persistência, com a data de nascimento (CRM-F012). */
    public static Customer of(Long id, String nome, String contato, String email, String cpf, String origem,
            Instant cadastradoEm, CustomerStage estagio, LocalDate dataNascimento) {
        return new Customer(id, nome, contato, email, cpf, origem, cadastradoEm, estagio, dataNascimento);
    }

    /**
     * Retorna uma cópia com os dados cadastrais trocados (CRM-C006) — id, data de cadastro e
     * estágio são preservados; estágio muda só pelo fluxo do kanban.
     */
    public Customer withProfile(String novoNome, String novoContato, String novoEmail, String novoCpf,
            String novaOrigem) {
        return new Customer(id, novoNome, novoContato, novoEmail, novoCpf, novaOrigem, cadastradoEm, estagio,
                dataNascimento);
    }

    /** Retorna uma cópia com a data de nascimento trocada (CRM-F012). */
    public Customer withDataNascimento(LocalDate novaDataNascimento) {
        return new Customer(id, nome, contato, email, cpf, origem, cadastradoEm, estagio, novaDataNascimento);
    }

    /** Retorna uma cópia deste cliente com o estágio atualizado. */
    public Customer withEstagio(CustomerStage novoEstagio) {
        return new Customer(id, nome, contato, email, cpf, origem, cadastradoEm, novoEstagio, dataNascimento);
    }

    /**
     * Indica se este é um cadastro "oficial" (tem CPF) em vez de "leve" (só email/contato).
     *
     * <p>Existe para a Fatia 4 (cashback) usar como porta de elegibilidade — cliente leve não
     * acumula pontos — sem cada consumidor futuro reimplementar a checagem de {@code cpf}.</p>
     */
    public boolean isOfficiallyRegistered() {
        return cpf != null && !cpf.isBlank();
    }
}
