-- PDV-F045 — categoria de cigarros.
--
-- Os produtos de categoria de cigarros não aparecem na grade do PDV: são vendidos pela Central de
-- cigarros (PDV-F041), por carteira › maço › solto. Até aqui o frontend decidia pelo nome da
-- categoria ("cigarr"), o que falha com qualquer grafia nova. A marcação passa a ser do cadastro.
ALTER TABLE product_category ADD COLUMN cigarette BOOLEAN NOT NULL DEFAULT FALSE;

-- Dados existentes: as categorias que já são de cigarro pelo nome, com e sem acento.
UPDATE product_category SET cigarette = TRUE
 WHERE translate(lower(name), 'áàâãéêíóôõúç', 'aaaaeeiooouc') LIKE '%cigarr%';
