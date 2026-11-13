package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Valor recebido por fora contra banco real: mapeamento, ordem e exclusão lógica (V203). */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OffSystemReceiptRepositoryIT {

    @Autowired OffSystemReceiptRepositoryImpl receiptRepository;
    @Autowired CashRegisterRepositoryImpl cashRegisterRepository;

    @PersistenceContext EntityManager em;

    private Long openSession() {
        return cashRegisterRepository.save(CashRegisterSession.open("it-off-system", BigDecimal.TEN, "LOJA-01")).id();
    }

    private static OffSystemReceipt receipt(Long sessionId, String amount, PaymentMethod method,
            PaymentProvider provider, Instant at) {
        return new OffSystemReceipt(null, sessionId, new BigDecimal(amount), method, provider, "nota", "it-off-system",
                at.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void save_list_eDeleteLogico() {
        Long sessionId = openSession();
        Instant now = Instant.now();
        OffSystemReceipt older = receiptRepository.save(
                receipt(sessionId, "35.00", PaymentMethod.PIX, null, now.minusSeconds(60)));
        OffSystemReceipt newer = receiptRepository.save(
                receipt(sessionId, "80.50", PaymentMethod.CREDITO, PaymentProvider.CIELO, now));
        em.flush();
        em.clear();

        assertThat(receiptRepository.findBySessionId(sessionId))
                .extracting(OffSystemReceipt::id).containsExactly(newer.id(), older.id());
        OffSystemReceipt reloaded = receiptRepository.findActive(sessionId, newer.id()).orElseThrow();
        assertThat(reloaded.amount()).isEqualByComparingTo("80.50");
        assertThat(reloaded.method()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(reloaded.provider()).isEqualTo(PaymentProvider.CIELO);

        receiptRepository.markDeleted(newer.id(), "gerente", Instant.now());
        em.flush();
        em.clear();

        assertThat(receiptRepository.findBySessionId(sessionId))
                .extracting(OffSystemReceipt::id).containsExactly(older.id());
        assertThat(receiptRepository.findActive(sessionId, newer.id())).isEmpty();
        Object deletedBy = em.createNativeQuery(
                        "SELECT deleted_by FROM cash_session_off_system_receipt WHERE id = :id")
                .setParameter("id", newer.id()).getSingleResult();
        assertThat(deletedBy).isEqualTo("gerente");
    }

    @Test
    void findActive_deOutraSessao_naoAcha() {
        Long sessionId = openSession();
        OffSystemReceipt saved = receiptRepository.save(
                receipt(sessionId, "10.00", PaymentMethod.DEBITO, PaymentProvider.INFINITYPAY, Instant.now()));
        em.flush();

        assertThat(receiptRepository.findActive(sessionId + 999_999, saved.id())).isEmpty();
    }
}
