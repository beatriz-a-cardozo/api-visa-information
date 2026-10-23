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

- [ ] `CollectionService`: roda os scrapers, compara hash, grava histórico e log
- [ ] Endpoint admin `POST /admin/collections` (protegido) e agendamento
- [ ] Alerta de falha por e-mail (Spring Mail + Brevo)
- [ ] Workflow agendado que chama a coleta no ambiente de produção

## Sprint 4 — API pública

- [ ] `GET /api/v1/visas?origin=BRA&destination=DEU`
- [ ] `GET /api/v1/visas/history?origin=…&destination=…` (até 1 ano)
- [ ] `GET /api/v1/countries`
- [ ] Custo convertido para a moeda de origem (Frankfurter, cache diário)
- [ ] Erros padronizados (RFC 9457 Problem Details), OpenAPI/Swagger
- [ ] Limite de requisições por IP (Bucket4j)

## Sprint 5 — Admin e segurança

- [ ] Spring Security para `/admin/**`
- [ ] CRUD de fontes oficiais e correção manual de requisitos (`dataOrigin = MANUAL`)
- [ ] Validação de URL cadastrada (https, sem IP privado) contra SSRF
- [ ] Cabeçalhos de segurança, CORS fechado

## Sprint 6+ — Scrapers por destino

Um card por destino, cada um com fixture e teste:
DEU, BRA, JPN, ZAF, AUS, KOR, EGY, ARG; e evoluir CAN e GBR para ler a lista de
nacionalidades (todas as origens), não só a página de requisitos.

## Deploy

- [ ] Atlas M0 + Render + secrets no GitHub
- [ ] Workflow de deploy disparado por push na `main`
- [ ] Monitor agendado dos scrapers contra os sites reais
