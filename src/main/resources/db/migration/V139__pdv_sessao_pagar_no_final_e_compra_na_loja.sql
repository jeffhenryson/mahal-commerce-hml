-- PDV-F034 — sessão de narguilé paga no final.
--
-- Desde PDV-F027 toda sessão nasce AGUARDANDO_PAGAMENTO e só o pagamento a leva ao preparo. Algumas
-- mesas consomem antes de pagar; a casa aceita o risco, mas com dono: PDV_SESSION_PAY_LATER. A linha
-- marcada nasce direto em PREPARANDO e fica em aberto até a conta. O rosh extra de uma sessão paga no
-- final herda a marca, por isso o CHECK aceita ROSH_EXTRA.
ALTER TABLE comanda_item ADD COLUMN pay_later BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_pay_later_by_mode
    CHECK (pay_later = FALSE OR mode IN ('SESSAO','ROSH_EXTRA'));

-- PDV-F036 — "o cliente comprou algo na loja?", uma resposta por mesa. Nulo = não respondido, que é
-- diferente de "não comprou": a taxa de conversão só conta as respondidas.
ALTER TABLE comanda ADD COLUMN bought_in_store    BOOLEAN;
ALTER TABLE comanda ADD COLUMN bought_in_store_by VARCHAR(80);
ALTER TABLE comanda ADD COLUMN bought_in_store_at TIMESTAMP;

-- Concedida só ao ROLE_ADMIN, como PDV_COMANDA_COURTESY (V115): estender ao atendente é uma linha.
INSERT INTO permissions (name) VALUES ('PDV_SESSION_PAY_LATER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_SESSION_PAY_LATER'
ON CONFLICT DO NOTHING;
