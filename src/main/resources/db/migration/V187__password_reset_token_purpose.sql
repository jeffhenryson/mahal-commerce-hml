-- Convite e recuperação de senha usam o mesmo token, mas têm efeitos diferentes: aceitar um convite
-- não deve gerar o alerta de senha redefinida, e prova que o e-mail é da pessoa.
ALTER TABLE password_reset_tokens ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'RESET';
