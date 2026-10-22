-- Autoatendimento do Salão: o atendente confirma o "pagar no final".
--
-- Decisões:
--  * Qualquer cliente pede pagar no final pelo QR (pagar_no_final, o que o lead pediu). No aceite o
--    atendente confirma (precisa de PDV_SESSION_PAY_LATER) ou aceita a sessão cobrando agora — nunca
--    impõe pagar no final a quem não pediu.
--  * pagar_no_final_aceito guarda o que valeu no aceite; nulo enquanto pendente e na recusada.

ALTER TABLE salao_solicitacao ADD COLUMN pagar_no_final_aceito BOOLEAN;
