package com.consumoesperto.integration;

import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.dto.MemoriaSemanticaSimilaridadeDTO;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaOrigem;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.CerebroSemanticoService;
import com.consumoesperto.service.MemoriaCicloVidaService;
import com.consumoesperto.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Memória semântica J.A.R.V.I.S. com pgvector real (Testcontainers):
 * dedupe com reforço (1.2), invalidação retroativa (1.1), provisão determinística (2.2),
 * score híbrido (4.1), contradição → SUPERADA (4.2), expiração/decaimento (5.2)
 * e isolamento multi-tenant.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("com.consumoesperto.integration.MemoriaSemanticaPostgresIntegrationTest#dockerDisponivel")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemoriaSemanticaPostgresIntegrationTest {

    private static final int DIM = 1536;

    static boolean dockerDisponivel() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("consumo_test")
        .withUsername("consumo")
        .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired private DataSource dataSource;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private OpenAiService openAi;
    private MemoriaJarvisProperties props;
    private CerebroSemanticoService cerebro;
    private MemoriaCicloVidaService cicloVida;
    private Long userA;
    private Long userB;

    /** Embeddings determinísticos por texto — sem chamadas de rede. */
    private final Map<String, float[]> embeddings = new HashMap<>();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(transactionManager);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("DROP TABLE IF EXISTS memoria_semantica_jarvis");
        jdbc.execute(
            "CREATE TABLE memoria_semantica_jarvis ("
                + "id BIGSERIAL PRIMARY KEY,"
                + "usuario_id BIGINT NOT NULL,"
                + "contexto TEXT NOT NULL,"
                + "embedding vector(" + DIM + "),"
                + "data_registro TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),"
                + "categoria_origem VARCHAR(32) NOT NULL,"
                + "tipo VARCHAR(24) NOT NULL DEFAULT 'FATO',"
                + "status VARCHAR(16) NOT NULL DEFAULT 'ATIVA',"
                + "origem VARCHAR(24) NOT NULL DEFAULT 'SISTEMA',"
                + "confianca NUMERIC(3,2) NOT NULL DEFAULT 0.50,"
                + "validade DATE,"
                + "valor NUMERIC(19,2),"
                + "categoria VARCHAR(120),"
                + "mes_alvo INTEGER,"
                + "ano_alvo INTEGER,"
                + "contador_reforco INTEGER NOT NULL DEFAULT 1,"
                + "ultimo_reforco_em TIMESTAMP WITHOUT TIME ZONE,"
                + "transacoes_evidencia TEXT,"
                + "confirmada_usuario BOOLEAN)");

        openAi = mock(OpenAiService.class);
        lenient().when(openAi.tryCreateEmbedding(anyString(), any()))
            .thenAnswer(inv -> Optional.of(embeddingPara(inv.getArgument(0))));
        lenient().when(openAi.gerarTexto(any(), anyString(), anyString(), anyString())).thenReturn("");

        props = new MemoriaJarvisProperties();
        cerebro = new CerebroSemanticoService(jdbc, openAi, transacaoRepository, props);
        cicloVida = new MemoriaCicloVidaService(
            jdbc, props, cerebro, openAi, usuarioRepository, transacaoRepository);

        userA = criarUsuario("memA");
        userB = criarUsuario("memB");
    }

    private Long criarUsuario(String prefixo) {
        return tx.execute(status -> {
            Usuario u = new Usuario();
            u.setUsername(prefixo + System.nanoTime());
            u.setEmail(prefixo + System.nanoTime() + "@t.com");
            u.setNome("Memória " + prefixo);
            entityManager.persist(u);
            return u.getId();
        });
    }

    /** Vetor unitário determinístico: componente 0 = cos, componente 1 = sen (resto zero). */
    private static float[] vetor(double cos) {
        float[] v = new float[DIM];
        v[0] = (float) cos;
        v[1] = (float) Math.sqrt(Math.max(0, 1 - cos * cos));
        return v;
    }

    private float[] embeddingPara(String texto) {
        return embeddings.computeIfAbsent(texto, t -> vetor(1.0));
    }

    private void registrarEmbedding(String texto, double cosVsBase) {
        embeddings.put(texto, vetor(cosVsBase));
    }

    // ------------------------------------------------------------------
    // 1.2 — Dedupe com reforço
    // ------------------------------------------------------------------

    @Test
    void gravarCincoVezesOMesmoContexto_geraUmRegistroComReforco5() {
        registrarEmbedding("Prefere mercado no atacado", 1.0);
        for (int i = 0; i < 5; i++) {
            cerebro.gravarMemoria(userA, "Prefere mercado no atacado",
                MemoriaCategoriaOrigem.FINANCAS, MemoriaMetadados.inferido(MemoriaTipo.FATO));
        }
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM memoria_semantica_jarvis WHERE usuario_id = ?", Integer.class, userA);
        Integer reforco = jdbc.queryForObject(
            "SELECT contador_reforco FROM memoria_semantica_jarvis WHERE usuario_id = ?", Integer.class, userA);
        assertEquals(1, total, "dedupe deve reforçar em vez de duplicar");
        assertEquals(5, reforco);
    }

    // ------------------------------------------------------------------
    // 4.2 — Contradição: a mais recente prevalece (SUPERADA)
    // ------------------------------------------------------------------

    @Test
    void preferenciaNovaContraditoria_superaAntiga() {
        registrarEmbedding("Quer economizar em delivery", 1.0);
        registrarEmbedding("Liberou delivery no fim de semana", 0.85); // parecida mas não idêntica
        cerebro.gravarMemoria(userA, "Quer economizar em delivery",
            MemoriaCategoriaOrigem.FINANCAS, MemoriaMetadados.inferido(MemoriaTipo.PREFERENCIA));
        cerebro.gravarMemoria(userA, "Liberou delivery no fim de semana",
            MemoriaCategoriaOrigem.FINANCAS, MemoriaMetadados.inferido(MemoriaTipo.PREFERENCIA));

        String statusAntiga = jdbc.queryForObject(
            "SELECT status FROM memoria_semantica_jarvis WHERE usuario_id = ? AND contexto = ?",
            String.class, userA, "Quer economizar em delivery");
        String statusNova = jdbc.queryForObject(
            "SELECT status FROM memoria_semantica_jarvis WHERE usuario_id = ? AND contexto = ?",
            String.class, userA, "Liberou delivery no fim de semana");
        assertEquals("SUPERADA", statusAntiga);
        assertEquals("ATIVA", statusNova);
    }

    // ------------------------------------------------------------------
    // 4.1 — Score híbrido: recente+reforçada vence antiga+isolada com mesma similaridade
    // ------------------------------------------------------------------

    @Test
    void buscaHibrida_recenteReforcadaVenceAntigaIsolada() {
        registrarEmbedding("consulta gastos com farmácia", 1.0);
        // Mesmo embedding para as duas memórias — similaridade idêntica à consulta
        jdbc.update(
            "INSERT INTO memoria_semantica_jarvis (usuario_id, contexto, embedding, categoria_origem, tipo, "
                + "contador_reforco, data_registro) VALUES (?,?,?::vector,'FINANCAS','FATO',1, NOW() - INTERVAL '700 days')",
            userA, "Memória antiga isolada sobre farmácia", literal(vetor(1.0)));
        jdbc.update(
            "INSERT INTO memoria_semantica_jarvis (usuario_id, contexto, embedding, categoria_origem, tipo, "
                + "contador_reforco, data_registro, ultimo_reforco_em) "
                + "VALUES (?,?,?::vector,'FINANCAS','FATO',10, NOW() - INTERVAL '10 days', NOW() - INTERVAL '2 days')",
            userA, "Memória recente reforçada sobre farmácia", literal(vetor(1.0)));

        List<MemoriaSemanticaSimilaridadeDTO> hits =
            cerebro.buscarSimilaresAtivas(userA, "consulta gastos com farmácia", 5);
        assertTrue(hits.size() >= 2);
        assertEquals("Memória recente reforçada sobre farmácia", hits.get(0).getContexto(),
            "score híbrido deve priorizar recência + reforço com similaridade igual");
    }

    // ------------------------------------------------------------------
    // 2.2 — Provisão determinística por PLANO_FUTURO (metadado, não similaridade)
    // ------------------------------------------------------------------

    @Test
    void planoFuturoComMesAlvo_apareceDeterministicamenteNoMes() {
        registrarEmbedding("Planeja gastar R$ 2000 em julho", 1.0);
        cerebro.gravarMemoria(userA, "Planeja gastar R$ 2000 em julho",
            MemoriaCategoriaOrigem.FINANCAS,
            MemoriaMetadados.inferido(MemoriaTipo.PLANO_FUTURO)
                .comValor(new BigDecimal("2000.00"))
                .comAlvo(7, 2026));

        var julho = cerebro.listarPlanosFuturosParaMes(userA, 7, 2026);
        var agosto = cerebro.listarPlanosFuturosParaMes(userA, 8, 2026);
        assertEquals(1, julho.size());
        assertEquals(0, new BigDecimal("2000.00").compareTo(julho.get(0).valor()));
        assertEquals(0, agosto.size());
        // Isolamento multi-tenant: o plano de A não vaza para B
        assertEquals(0, cerebro.listarPlanosFuturosParaMes(userB, 7, 2026).size());
    }

    // ------------------------------------------------------------------
    // 1.1 — Invalidação retroativa por evidência excluída
    // ------------------------------------------------------------------

    @Test
    void excluirTransacoesEvidencia_invalidaHabitoNaHora() {
        List<Long> ids = tx.execute(status -> {
            Usuario u = entityManager.find(Usuario.class, userA);
            List<Long> criadas = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Transacao t = new Transacao();
                t.setUsuario(u);
                t.setDescricao("gasto nubank " + i);
                t.setValor(new BigDecimal("100.00"));
                t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
                t.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
                t.setDataTransacao(LocalDateTime.now().minusDays(i));
                entityManager.persist(t);
                criadas.add(t.getId());
            }
            return criadas;
        });
        registrarEmbedding("Hábito: gasto nubank seguido de outro gasto", 1.0);
        cerebro.gravarMemoria(userA, "Hábito: gasto nubank seguido de outro gasto",
            MemoriaCategoriaOrigem.HABITO,
            MemoriaMetadados.inferido(MemoriaTipo.HABITO).comEvidencia(ids));

        // Exclui (soft delete) 3 das 5 evidências — suporte vivo cai para 2 < mínimo 5
        jdbc.update("UPDATE transacoes SET excluido = TRUE WHERE id IN (?,?,?)",
            ids.get(0), ids.get(1), ids.get(2));
        int invalidadas = cerebro.invalidarPorEvidencia(userA, ids.subList(0, 3));

        assertEquals(1, invalidadas);
        String statusMem = jdbc.queryForObject(
            "SELECT status FROM memoria_semantica_jarvis WHERE usuario_id = ? AND tipo = 'HABITO'",
            String.class, userA);
        assertEquals("INVALIDADA", statusMem, "hábito deve ser invalidado na hora, sem esperar expirar");
        // Fora do painel: timeline só devolve ATIVAS
        assertTrue(cerebro.listarRecentesParaUsuario(userA, 20).isEmpty());
    }

    // ------------------------------------------------------------------
    // 5.2 — Expiração de PLANO_FUTURO vencido e decaimento sem reforço
    // ------------------------------------------------------------------

    @Test
    void planoFuturoVencido_eArquivadoPeloJob() {
        registrarEmbedding("Cirurgia em julho passado", 1.0);
        cerebro.gravarMemoria(userA, "Cirurgia em julho passado",
            MemoriaCategoriaOrigem.FINANCAS,
            MemoriaMetadados.inferido(MemoriaTipo.PLANO_FUTURO)
                .comValidade(LocalDate.now().minusDays(1)));

        cicloVida.expirarPlanosVencidos();

        String statusMem = jdbc.queryForObject(
            "SELECT status FROM memoria_semantica_jarvis WHERE usuario_id = ?", String.class, userA);
        assertEquals("ARQUIVADA", statusMem);
    }

    @Test
    void memoriaInferidaSemReforco_decaiEAtingePisoArquivada() {
        jdbc.update(
            "INSERT INTO memoria_semantica_jarvis (usuario_id, contexto, categoria_origem, tipo, origem, "
                + "confianca, data_registro) "
                + "VALUES (?,?,'HABITO','HABITO','INFERIDO',0.28, NOW() - INTERVAL '90 days')",
            userA, "Hábito antigo sem reforço");

        cicloVida.decairConfiancaSemReforco();

        String statusMem = jdbc.queryForObject(
            "SELECT status FROM memoria_semantica_jarvis WHERE usuario_id = ?", String.class, userA);
        assertEquals("ARQUIVADA", statusMem, "confiança 0.28 − 0.05 = 0.23 < piso 0.25 → arquivar");
    }

    // ------------------------------------------------------------------
    // Multi-tenant nas consultas novas
    // ------------------------------------------------------------------

    @Test
    void memoriasDeUmUsuario_naoVazamParaOutro() {
        registrarEmbedding("Segredo financeiro do usuário A", 1.0);
        cerebro.gravarMemoria(userA, "Segredo financeiro do usuário A",
            MemoriaCategoriaOrigem.FINANCAS, MemoriaMetadados.inferido(MemoriaTipo.FATO));

        assertTrue(cerebro.listarInsightsRelevantes(userB, 5).isEmpty());
        assertTrue(cerebro.buscarSimilaresAtivas(userB, "Segredo financeiro do usuário A", 5).isEmpty());
        assertTrue(cerebro.refutarMemoria(userB,
            jdbc.queryForObject("SELECT id FROM memoria_semantica_jarvis WHERE usuario_id = ?", Long.class, userA))
            == false, "usuário B não pode refutar memória de A");
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
