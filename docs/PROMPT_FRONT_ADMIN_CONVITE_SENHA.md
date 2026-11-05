# Prompt para o frontend-admin: tela de senha ao aceitar convite (05/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend`. O link do convite agora
termina em `&convite=1`. O endpoint `POST /auth/reset-password` e o contrato continuam os mesmos.

**O que muda para o usuário:** quem aceita um convite vê uma tela sobre criar a senha, e não sobre
redefinir uma senha que nunca teve.

## O que fazer

Em `features/auth/reset-password/reset-password.component.ts`, ler `convite` da query string. Com
`convite=1`, trocar estes textos:

| Onde | Hoje | Com `convite=1` |
|---|---|---|
| Título | Nova senha | Crie sua senha |
| Subtítulo | Defina sua nova senha | Falta só isso para entrar no painel |
| Sucesso | Senha redefinida com sucesso! | Senha criada! Agora é só entrar. |
| Link inválido | Link inválido ou expirado. Solicite uma nova recuperação de senha. | Este convite não é mais válido. Peça um novo a quem cuida dos acessos da loja. |

Com `convite=1`, a tela de link inválido não mostra o botão "Solicitar novamente".

Em `shared/audit-log.constants.ts`, incluir o evento novo `USER_INVITE_ACCEPTED` com
`cs-badge cs-badge--success`.

## Teste

Cobrir os textos com e sem `convite=1`.
