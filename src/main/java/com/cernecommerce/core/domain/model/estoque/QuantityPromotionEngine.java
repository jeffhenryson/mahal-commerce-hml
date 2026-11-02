package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Motor da promoção por quantidade. É uma função pura (sem I/O e sem relógio) usada pelo balcão, pela
 * comanda e pelo marketplace. A regra é a mesma nos três, e o front tem a gêmea ({@code aplicarPromocoes}
 * em {@code promocao-quantidade.models.ts}): os testes dos dois lados usam os mesmos números.
 *
 * <ol>
 *   <li>Regras na ordem crescente de {@code id}. Cada unidade entra em no máximo uma promoção, e as
 *       unidades presas a um kit montável ({@code unitsInKit}) não contam.</li>
 *   <li>As unidades elegíveis livres são ordenadas da mais cara para a mais barata (empate pelo SKU) e
 *       agrupadas de N em N. A sobra paga o preço normal.</li>
 *   <li>Um grupo só vale se a soma dos preços normais for <b>maior</b> que {@code bundlePrice}, porque a
 *       promoção nunca encarece. Como os grupos saem do mais caro, o primeiro que não vale encerra a
 *       regra.</li>
 *   <li>O desconto da regra é rateado entre os SKUs, proporcional ao preço normal, ao centavo
 *       ({@code HALF_UP}). A diferença do arredondamento vai para o último SKU, na ordem da lista
 *       ordenada.</li>
 * </ol>
 *
 * <p>Quem chama passa só as regras vigentes no canal e só os SKUs vendáveis. O motor não decide
 * vigência.</p>
 */
public final class QuantityPromotionEngine {

    private QuantityPromotionEngine() {
    }

    /** Uma linha do carrinho: SKU, quantidade e o preço real de venda da unidade hoje. */
    public record CartLine(String sku, BigDecimal quantity, BigDecimal unitPrice) {
    }

    /** Parte de uma regra aplicada que cai num SKU. */
    public record LineDiscount(String sku, int units, BigDecimal discount) {
    }

    /** Uma regra que fechou pelo menos um grupo. A soma de {@code lines.discount} é {@code discount}. */
    public record Applied(Long promotionId, String promotionName, int groups, BigDecimal regularPrice,
            BigDecimal discount, List<LineDiscount> lines) {
    }

    /** Por SKU: o desconto somado de todas as regras e a regra de maior desconto (que vai na linha). */
    public record SkuPromotion(Long promotionId, String promotionName, BigDecimal discount) {
    }

    public record Result(List<Applied> applied, Map<String, SkuPromotion> bySku, BigDecimal total) {

        public static final Result EMPTY = new Result(List.of(), Map.of(), zero());

        public BigDecimal discountFor(String sku) {
            SkuPromotion p = bySku.get(sku);
            return p == null ? zero() : p.discount();
        }

        public boolean isEmpty() {
            return applied.isEmpty();
        }
    }

    private record Unit(String sku, BigDecimal price) {
    }

    /**
     * @param lines linhas do carrinho. Linhas repetidas do mesmo SKU somam a quantidade e usam o preço da
     *              primeira. Quantidade fracionada conta só a parte inteira.
     * @param promotions regras vigentes no canal (a ordem não importa, o motor ordena por {@code id})
     * @param unitsInKit SKU → unidades já presas a kit montável, que não contam
     */
    public static Result apply(List<CartLine> lines, List<QuantityPromotion> promotions,
            Map<String, Integer> unitsInKit) {
        if (lines == null || lines.isEmpty() || promotions == null || promotions.isEmpty()) {
            return Result.EMPTY;
        }
        Map<String, Integer> kit = unitsInKit == null ? Map.of() : unitsInKit;

        Map<String, BigDecimal> priceBySku = new LinkedHashMap<>();
        Map<String, Integer> qtyBySku = new LinkedHashMap<>();
        for (CartLine line : lines) {
            if (line == null || line.sku() == null || line.quantity() == null) {
                continue;
            }
            int units = line.quantity().setScale(0, RoundingMode.DOWN).intValue();
            if (units <= 0) {
                continue;
            }
            qtyBySku.merge(line.sku(), units, Integer::sum);
            if (line.unitPrice() != null) {
                priceBySku.putIfAbsent(line.sku(), line.unitPrice());
            }
        }

        Map<String, Integer> free = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : qtyBySku.entrySet()) {
            int livre = Math.max(0, e.getValue() - kit.getOrDefault(e.getKey(), 0));
            if (livre > 0) {
                free.put(e.getKey(), livre);
            }
        }

        List<QuantityPromotion> ordered = promotions.stream()
                .sorted(Comparator.comparing(QuantityPromotion::id,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Applied> applied = new ArrayList<>();
        for (QuantityPromotion promo : ordered) {
            int n = promo.quantity();
            BigDecimal bundle = promo.bundlePrice();
            if (n < 2 || bundle == null || bundle.signum() <= 0) {
                continue;
            }

            List<Unit> units = new ArrayList<>();
            for (String sku : new LinkedHashSet<>(promo.skus())) {
                int qtd = free.getOrDefault(sku, 0);
                BigDecimal price = priceBySku.get(sku);
                if (qtd <= 0 || price == null || price.signum() <= 0) {
                    continue;
                }
                for (int i = 0; i < qtd; i++) {
                    units.add(new Unit(sku, price));
                }
            }
            if (units.isEmpty()) {
                continue;
            }
            units.sort(Comparator.comparing(Unit::price).reversed().thenComparing(Unit::sku));

            int groups = 0;
            while ((groups + 1) * n <= units.size()) {
                BigDecimal sum = money(units.subList(groups * n, (groups + 1) * n).stream()
                        .map(Unit::price).reduce(BigDecimal.ZERO, BigDecimal::add));
                if (sum.compareTo(bundle) <= 0) {
                    break;
                }
                groups++;
            }
            if (groups == 0) {
                continue;
            }

            List<Unit> used = units.subList(0, groups * n);
            Map<String, int[]> unitsPerSku = new LinkedHashMap<>();
            Map<String, BigDecimal> valuePerSku = new LinkedHashMap<>();
            for (Unit u : used) {
                unitsPerSku.computeIfAbsent(u.sku(), k -> new int[1])[0]++;
                valuePerSku.merge(u.sku(), u.price(), BigDecimal::add);
                free.merge(u.sku(), -1, Integer::sum);
            }
            BigDecimal regular = money(used.stream().map(Unit::price).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal discount = money(regular.subtract(bundle.multiply(BigDecimal.valueOf(groups))));

            List<LineDiscount> parts = new ArrayList<>();
            BigDecimal allotted = zero();
            List<String> skus = new ArrayList<>(valuePerSku.keySet());
            for (int i = 0; i < skus.size(); i++) {
                String sku = skus.get(i);
                BigDecimal part = i == skus.size() - 1
                        ? money(discount.subtract(allotted))
                        : discount.multiply(valuePerSku.get(sku)).divide(regular, Money.MONEY_SCALE, Money.ROUNDING);
                allotted = money(allotted.add(part));
                parts.add(new LineDiscount(sku, unitsPerSku.get(sku)[0], part));
            }
            applied.add(new Applied(promo.id(), promo.name(), groups, regular, discount, List.copyOf(parts)));
        }

        if (applied.isEmpty()) {
            return Result.EMPTY;
        }

        Map<String, BigDecimal> totalBySku = new LinkedHashMap<>();
        Map<String, LineDiscount> bestPart = new LinkedHashMap<>();
        Map<String, Applied> bestRule = new LinkedHashMap<>();
        for (Applied a : applied) {
            for (LineDiscount l : a.lines()) {
                totalBySku.merge(l.sku(), l.discount(), BigDecimal::add);
                LineDiscount atual = bestPart.get(l.sku());
                if (atual == null || l.discount().compareTo(atual.discount()) > 0) {
                    bestPart.put(l.sku(), l);
                    bestRule.put(l.sku(), a);
                }
            }
        }
        Map<String, SkuPromotion> bySku = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : totalBySku.entrySet()) {
            Applied rule = bestRule.get(e.getKey());
            bySku.put(e.getKey(), new SkuPromotion(rule.promotionId(), rule.promotionName(), money(e.getValue())));
        }
        BigDecimal total = money(applied.stream().map(Applied::discount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Result(List.copyOf(applied), Map.copyOf(bySku), total);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
    }
}
