# Roadmap

Cada item vira um card no board e, em geral, um PR para `dev`.

## Sprint 2 — Fundação (este PR)

- [x] Pacotes em camadas e modelo de dados novo (ISO alpha-3, dinheiro com moeda,
      rastreabilidade, histórico com TTL)
- [x] Catálogo inicial de 10 países com fontes oficiais
- [x] `HtmlFetcher` com User-Agent honesto, robots.txt, intervalo por host e só https
- [x] Classificador de requisitos compartilhado
- [x] Scrapers de Canadá e Reino Unido no contrato novo, testados com HTML salvo
- [x] Testes de integração com MongoDB real (Testcontainers)
- [x] Dockerfile corrigido (Java 21, sem root, distroless) e compose com Mongo autenticado
- [x] CI: build/testes/cobertura, imagem + smoke test, CodeQL, dependency review,
      Gitleaks, scan da imagem, Scorecard, Dependabot
- [x] Verificação agendada dos scrapers contra os sites reais, que abre issue ao falhar
      (workflows agendados só rodam a partir da `main`)

## Sprint 3 — Coleta e histórico

- [x] `CollectionService`: roda os scrapers, compara hash, grava histórico e log
- [x] Endpoints admin: `POST /admin/collections`, `GET /admin/collections/{runId}`,
      `GET /admin/collections/logs`, protegidos por `X-Admin-Key`
- [x] Spring Security (API sem sessão; `/admin` com chave; fora dele só `GET`)
- [x] Alerta de falha por e-mail (Spring Mail, qualquer SMTP; Brevo em produção)
- [x] Workflow agendado `collect.yml` que chama a coleta no ambiente de produção
- [x] Erros em Problem Details (RFC 9457)

## Sprint 4 — API pública

- [x] `GET /api/v1/visas?origin=BRA&destination=DEU`
- [x] `GET /api/v1/visas/history?origin=…&destination=…` (até 1 ano)
- [x] `GET /api/v1/countries`
- [x] Custo convertido para a moeda de origem (Frankfurter, cache de 12 h)
- [x] Erros padronizados (RFC 9457 Problem Details), OpenAPI/Swagger
- [x] Limite de requisições por IP (Bucket4j)

## Sprint 5 — Admin e segurança

- [x] CRUD de fontes oficiais e correção manual de requisitos (`dataOrigin = MANUAL`)
- [x] Validação de URL cadastrada (https, sem IP privado) contra SSRF, também em cada
      redirecionamento da coleta
- [x] Cabeçalhos de segurança, CORS fechado

## Sprint 6+ — Scrapers por destino

Um card por destino, cada um com fixture e teste:
DEU, BRA, JPN, ZAF, AUS, KOR, EGY, ARG; e evoluir CAN e GBR para ler a lista de
nacionalidades (todas as origens), não só a página de requisitos.

## Deploy

- [ ] Atlas M0 + Render + secrets no GitHub
- [ ] Workflow de deploy disparado por push na `main`
- [ ] Monitor agendado dos scrapers contra os sites reais
