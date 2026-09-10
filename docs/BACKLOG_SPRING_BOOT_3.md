# Backlog — actualizar Spring Boot 2.7.18 (fora do suporte comunitário)

**Registado em:** 2026-09-09  
**Estado:** dívida de plataforma; **não** bloqueia o monitor WhatsApp nem o commit desta rodada.

## Versão real

O parent Maven é `spring-boot-starter-parent` **2.7.18** (`backend/pom.xml`). Java **17** (compatível com 2.7; 2.7 aceita Java 8–21).

README e `docs/VISAO_GERAL.md` diziam “Spring Boot 3” — isso estava **errado**. O código usa `javax.persistence` / `javax.servlet` / `javax.validation` em todo o backend. Não há `jakarta.*`. `springdoc` está em **1.7.0** (linha Boot 2; a linha 2.x é Boot 3).

## Suporte

- Último patch **público** (Maven Central): **2.7.18**, 23 Nov 2023.
- Suporte OSS da linha 2.7 **terminou** (referência: [endoflife.date/spring-boot](https://endoflife.date/spring-boot) — OSS 2.7 em 30 Jun 2023; o 2.7.18 foi patch de cortesia).
- Este repo **não** aponta para o repositório Tanzu/Spring Enterprise. Sem essa subscrição **não há** patches de segurança novos no Maven Central.
- **Implicação:** o backend é uma app **financeira** (JWT, dados `FINANCIAL`/`PERSONAL`, Postgres de transacções). Correr 2.7.18 sem patches significa CVEs posteriores a Nov 2023 **não** entram por actualização do parent. Isto é dívida de segurança, não só de conveniência.
- Comercial Tanzu para 2.7 ainda aparece listado até ~30 Jun 2029 — irrelevante enquanto os artefactos vêm só do Maven Central.

Rodadas anteriores do ecossistema (E.D.I.T.H., capabilities, WhatsApp) **assumiram Boot 3 / Jakarta**. Essa assunção estava errada; o código gerado **compila e corre em 2.7.18**.

## API exclusiva do Spring Boot 3 / Spring 6

Procura no `backend/` (2026-09-09): **nenhum** uso de

- namespace `jakarta.*`
- `org.springframework.web.client.RestClient` (cliente HTTP novo; o que existe é `RestClientException` / `HttpStatusCodeException` do Spring 5)
- `org.springframework.http.ProblemDetail`
- `org.springframework.http.HttpStatusCode`
- `@EnableMethodSecurity` (Boot 3; aqui continua o modelo Boot 2 / Spring Security 5)

`@ConfigurationProperties` nas classes novas (ex. `WhatsappConexaoMonitorProperties`) é o modelo Boot 2.7 (`@Component` + prefixo). Entidades JPA novas usam `javax.persistence`.

**Conclusão:** não há código destas rodadas que exija Boot 3. A dívida é de **ciclo de vida**, não de API quebrada.

## O que a actualização implica (quando for priorizada)

Não é bump de parent. Esboço de escopo:

1. `javax.*` → `jakarta.*` (persistência, servlet, validação, `javax.annotation.PostConstruct`).
2. Spring Security 6: filtros, CSRF, matchers; `@EnableMethodSecurity` no lugar do modelo Boot 2 / Security 5; rever `SecurityFilterChain`.
3. Hibernate 6 / dialectos; Flyway 10+ se o parent puxar.
4. `springdoc` **1.7.0 → 2.x**; Resilience4j **1.7 → 2.x**.
5. Actuator / Micrometer Observation (API de métricas Boot 3).
6. Revalidar Testcontainers, H2, perfil `integracao` (`ddl-auto=validate`) e o contrato do ecossistema (envelope `X-Eco-*` intacto).

Alvo natural: linha **3.5.x** (Java 17) ou a linha 3.x ainda com patches públicos na data do trabalho — **não** saltar para Boot 4 sem decisão explícita.

## Critério de saída

Parent ≥ linha Boot 3 suportada no Maven Central; CI verde; contrato do ecossistema (`docs/ECOSYSTEM_CONTRACT.md`) intacto (envelope, capabilities, dono do Postgres financeiro).
