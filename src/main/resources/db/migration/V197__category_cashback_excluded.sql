-- Categorias fora dos benefícios (cashback × desconto do chefe).
--
-- Os produtos destas categorias não geram cashback, o saldo de cashback não abate o valor deles e o
-- desconto do chefe não incide sobre eles. Separado de `cigarette` (V151) porque nem toda categoria
-- fora dos benefícios é vendida pela Central de cigarros (o palheiro, por exemplo).
ALTER TABLE product_category ADD COLUMN cashback_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- Dados existentes: as de cigarro e as que já são de cigarro/palheiro pelo nome, com e sem acento.
UPDATE product_category SET cashback_excluded = TRUE
 WHERE cigarette
    OR translate(lower(name), 'áàâãéêíóôõúç', 'aaaaeeiooouc') LIKE '%cigarr%'
    OR translate(lower(name), 'áàâãéêíóôõúç', 'aaaaeeiooouc') LIKE '%palheir%';
