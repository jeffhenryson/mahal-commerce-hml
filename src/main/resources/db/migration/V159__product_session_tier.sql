-- PDV-F048 — faixa da sessão como vínculo de verdade no produto (essência → Tradicional, Premium...).
--
-- Até aqui o front guardava o nome da faixa no atributo "Faixa da sessão" do produto. O nome
-- solto quebra quando a faixa é renomeada; a FK não.
ALTER TABLE product
    ADD COLUMN session_tier_id BIGINT NULL REFERENCES session_tier (id);

-- Migração do atributo: casa pelo nome sem acento e sem caixa. Havendo duas faixas com o mesmo
-- nome normalizado, fica a de menor id. Atributo que não casa com faixa nenhuma é descartado
-- junto: era só o rótulo que o front sugeriu, e a faixa continua escolhível no cadastro.
UPDATE product p
   SET session_tier_id = (
        SELECT t.id
          FROM product_root_attribute a
          JOIN session_tier t
            ON lower(unaccent(trim(t.nome))) = lower(unaccent(trim(a.attr_value)))
         WHERE a.product_id = p.id
           AND a.attr_type = 'Faixa da sessão'
         ORDER BY t.id
         LIMIT 1)
 WHERE EXISTS (SELECT 1 FROM product_root_attribute a
                WHERE a.product_id = p.id AND a.attr_type = 'Faixa da sessão');

DELETE FROM product_root_attribute WHERE attr_type = 'Faixa da sessão';

COMMENT ON COLUMN product.session_tier_id IS
    'Faixa sugerida no PDV para a sessão com esta essência (PDV-F048). O operador pode trocar na hora.';
