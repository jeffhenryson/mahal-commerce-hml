-- Exclusão lógica de produto publicado (EST-F054). O status ARQUIVADO cabe no VARCHAR(20) de
-- product.status (V97, sem CHECK) — aqui só a auditoria de quando e por quem.
ALTER TABLE product ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE product ADD COLUMN archived_by VARCHAR(100) NULL;
