# Política de segurança

## Como reportar uma vulnerabilidade

Não abra uma issue pública. Use o reporte privado do GitHub:
**Security → Report a vulnerability** neste repositório.

Inclua o que for possível: passos para reproduzir, impacto e versão/commit afetado.
Respondemos em até 7 dias.

## Escopo

- A API e o código deste repositório.
- A imagem Docker gerada a partir do `api-visa/Dockerfile`.

Os sites governamentais consultados pelos scrapers estão fora do escopo.

## Práticas adotadas

- Segredos apenas em variáveis de ambiente ou GitHub Secrets; `.env` fica fora do git.
- CodeQL, Gitleaks, revisão de dependências, scan da imagem e Dependabot no CI.
- Actions fixadas por SHA e com permissões mínimas.
- Container sem shell, executado como usuário sem privilégios.
- Scrapers só acessam `https`, respeitam `robots.txt` e não contornam bloqueios.
