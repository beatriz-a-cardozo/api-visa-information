# api-visa-information

[![CI](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/ci.yml)
[![CodeQL](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/codeql.yml/badge.svg?branch=dev)](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/beatriz-a-cardozo/api-visa-information/badge)](https://scorecard.dev/viewer/?uri=github.com/beatriz-a-cardozo/api-visa-information)

API REST que coleta requisitos de visto diretamente de fontes governamentais oficiais,
normaliza tudo em um schema único e registra de onde e quando cada dado veio.

Trabalho de Conclusão de Curso — Engenharia de Computação, Inatel.

## Stack

Java 21 · Spring Boot 4 · MongoDB · Jsoup · Docker · GitHub Actions

## Rodando localmente

Pré-requisitos: Docker. Para desenvolver, também JDK 21.

```bash
cd api-visa
cp .env.example .env   # troque a senha
docker compose up --build
```

A API sobe em `http://localhost:8080` e o health em `/actuator/health`.

Sem Docker para a aplicação (o Mongo pode continuar no compose):

```bash
cd api-visa
./mvnw spring-boot:run
```

## Consultas públicas

Só `GET`, sem autenticação, com limite de 60 requisições por minuto por IP (`429` com
`Retry-After` acima disso). Países em código ISO alfa-3.

```bash
# países cobertos, com moeda e fontes oficiais
curl http://localhost:8080/api/v1/countries

# o que um brasileiro precisa para entrar no Reino Unido
curl "http://localhost:8080/api/v1/visas?origin=BRA&destination=GBR"

# mudanças do último ano para o mesmo par
curl "http://localhost:8080/api/v1/visas/history?origin=BRA&destination=GBR"
```

As taxas aparecem na moeda do destino e, quando o Banco Central Europeu publica a cotação,
também na moeda da origem (`costInOriginCurrency`, com a taxa e a data). A documentação
interativa fica em `/swagger-ui.html` (OpenAPI em `/v3/api-docs`).

## Administração

As rotas `/admin/**` exigem o cabeçalho `X-Admin-Key` com o valor de `ADMIN_API_KEY`
(defina no `.env`; gere com `openssl rand -hex 32`).

```bash
# inicia a coleta (todos os destinos, ou ?destination=GBR)
curl -X POST -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/admin/collections

# resultado de uma execução
curl -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/admin/collections/<runId>

# últimos logs
curl -H "X-Admin-Key: $ADMIN_API_KEY" "http://localhost:8080/admin/collections/logs?limit=20"

# fontes oficiais de um país (GET lista, POST adiciona, PUT/DELETE ?url= altera/remove)
curl -X POST -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" \
  -d '{"url":"https://www.gov.uk/check-uk-visa","description":"GOV.UK","language":"en","verified":true}' \
  http://localhost:8080/admin/countries/GBR/sources

# correção manual de um par (a coleta deixa de sobrescrever) e devolução ao scraper
curl -X PUT -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d @visa.json \
  "http://localhost:8080/admin/visas?origin=BRA&destination=GBR"
curl -X DELETE -H "X-Admin-Key: $ADMIN_API_KEY" \
  "http://localhost:8080/admin/visas/manual?origin=BRA&destination=GBR"
```

URLs cadastradas (fontes e correções manuais) precisam ser `https` e apontar para um endereço
público; o formato do corpo de cada rota está no Swagger UI.

Para liberar a API pública a um site no navegador, defina `CORS_ALLOWED_ORIGINS`
(por exemplo `https://app.example.org`). Vazio, nenhum site de outra origem lê as respostas.

## Testes

```bash
cd api-visa
./mvnw verify            # formatação, testes, cobertura mínima
./mvnw spotless:apply    # formata o código
```

Os testes de integração usam Testcontainers, então o Docker precisa estar rodando.
Os testes de scraper usam HTML salvo em `src/test/resources/fixtures`; para rodar
contra os sites reais:

```bash
./mvnw test -Dgroups=live -DexcludedGroups=
```

## Documentação

- [Arquitetura e decisões](docs/ARQUITETURA.md)
- [Roadmap](docs/ROADMAP.md)
- [Política de segurança](SECURITY.md)

## Fluxo de contribuição

`feature/*` → PR para `dev` → `hom` → `main`. O CI precisa passar e o PR precisa de revisão.
