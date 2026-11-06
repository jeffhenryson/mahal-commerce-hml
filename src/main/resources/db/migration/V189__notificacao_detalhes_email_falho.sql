-- Pares do alerta sem depender do texto do body, e ligações da notificação com outros registros.
ALTER TABLE notifications ADD COLUMN details TEXT;
ALTER TABLE notifications ADD COLUMN metadata TEXT;

-- E-mail que o provedor recusou. Tem dado de cliente: só a equipe técnica lê, e expira em 30 dias.
CREATE TABLE failed_emails (
    id                 BIGSERIAL    PRIMARY KEY,
    type               VARCHAR(80)  NOT NULL,
    recipient          VARCHAR(254) NOT NULL,
    subject            VARCHAR(255) NOT NULL,
    html               TEXT         NOT NULL,
    error              TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resend_count       INT          NOT NULL DEFAULT 0,
    last_resend_at     TIMESTAMPTZ,
    last_resend_status VARCHAR(10),
    last_resend_error  TEXT
);

CREATE INDEX idx_failed_emails_created_at ON failed_emails (created_at);
