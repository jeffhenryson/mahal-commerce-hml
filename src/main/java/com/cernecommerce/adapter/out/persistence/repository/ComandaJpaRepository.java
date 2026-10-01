package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComandaJpaRepository extends JpaRepository<ComandaEntity, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ComandaEntity> {

    /**
     * Fase 1 do padrão ID-first (PDV-C009): pagina só os ids, sem tocar na coleção de itens.
     * Misturar {@code LIMIT}/{@code OFFSET} com {@code JOIN FETCH} de coleção é o bug clássico de
     * paginação com JPA — o banco pagina as linhas do produto cartesiano, não as comandas.
     *
     * <p>Os dois filtros são opcionais (PDV-C007): sem {@code sessionId} a consulta devolve as mesas
     * abertas da <b>loja</b>, que é o que o salão precisa ver. O padrão
     * {@code (:param IS NULL OR ...)} é seguro aqui porque os dois parâmetros são {@code Long} e
     * {@code String} — ver o comentário de {@code OrderJpaRepository} sobre por que ele quebra com
     * {@code Instant} no Postgres real.</p>
     */
    @Query("SELECT c.id FROM ComandaEntity c WHERE c.status = :status "
            + "AND (:sessionId IS NULL OR c.sessionId = :sessionId) "
            + "AND (:warehouseCode IS NULL OR c.warehouseCode = :warehouseCode) "
            + "ORDER BY c.id DESC")
    Page<Long> findOpenIds(@Param("status") String status, @Param("sessionId") Long sessionId,
            @Param("warehouseCode") String warehouseCode, Pageable pageable);

    /**
     * Fase 2 do ID-first: carrega as comandas da página com os itens numa consulta só. Sem isto a
     * listagem paga <b>uma consulta por mesa aberta</b> ({@code items} é {@code LAZY}, e
     * {@code toDomain} toca a coleção).
     */
    @Query("SELECT DISTINCT c FROM ComandaEntity c LEFT JOIN FETCH c.items "
            + "WHERE c.id IN :ids ORDER BY c.id DESC")
    List<ComandaEntity> findAllByIdsWithItems(@Param("ids") List<Long> ids);

    /**
     * Ids das mesas abertas de uma sessão, para a guarda de fechamento de caixa (PDV-C005).
     *
     * <p>Separada de {@link #findOpenIds}, e sem paginação de propósito: a guarda precisa de
     * <b>todas</b> as mesas para decidir, e uma página cortaria a resposta em silêncio — um caixa
     * com mais mesas que o tamanho da página fecharia com mesa aberta, que é exatamente o bug que
     * PDV-C005 corrigiu. Projeta só o id porque é tudo que a exceção carrega: carregar o agregado
     * para depois jogar fora os itens era o que fazia esta guarda pagar o N+1 de PDV-C009.</p>
     */
    @Query("SELECT c.id FROM ComandaEntity c "
            + "WHERE c.sessionId = :sessionId AND c.status = :status ORDER BY c.id DESC")
    List<Long> findOpenIdsBySessionId(@Param("sessionId") Long sessionId, @Param("status") String status);

    /**
     * Trava a linha da comanda até o fim da transação (PDV-C008) — ver o javadoc de
     * {@code ComandaRepository.findByIdForUpdate} para o porquê de a trava ser pessimista e não
     * otimista. Molde: {@code RefreshTokenJpaRepository.findByTokenHashForUpdate}.
     *
     * <p>Trava só o cabeçalho, não a coleção de itens: a corrida que interessa é sobre o
     * <b>status</b> da mesa, e todo caminho de mutação passa por este mesmo id. Duas mesas
     * diferentes seguem em paralelo, que é o comportamento que o salão precisa.</p>
     */
    /**
     * Ids das mesas abertas antes de {@code cutoff} — a varredura de comanda esquecida (PDV-F013).
     *
     * <p>Ordem <b>crescente</b> de id, ao contrário das outras consultas daqui: a varredura processa
     * um lote por passada, e começar pelas mais antigas garante que a comanda mais presa sai
     * primeiro em vez de ficar no fim da fila para sempre.</p>
     */
    @Query("SELECT c.id FROM ComandaEntity c "
            + "WHERE c.status = :status AND c.openedAt < :cutoff ORDER BY c.id ASC")
    List<Long> findOpenIdsOlderThan(@Param("status") String status, @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ComandaEntity c WHERE c.id = :id")
    Optional<ComandaEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Move as linhas escolhidas de uma comanda para outra, preservando os ids (PDV-F016) — ver o
     * javadoc de {@code ComandaRepository.moveItems} para por que a preservação é o ponto todo.
     *
     * <p>A cláusula {@code comanda.id = :fromComandaId} garante que um id de outra mesa, vindo por
     * engano, não seja arrastado. Quais linhas vão (inclusive sessão paga ainda no salão, PDV-F031)
     * é decisão do domínio.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ComandaItemEntity i SET i.comanda.id = :toComandaId "
            + "WHERE i.comanda.id = :fromComandaId AND i.id IN :itemIds")
    int moveItems(@Param("fromComandaId") Long fromComandaId, @Param("toComandaId") Long toComandaId,
            @Param("itemIds") java.util.Collection<Long> itemIds);
}
