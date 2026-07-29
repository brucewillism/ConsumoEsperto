package com.consumoesperto.flyway;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.persistence.Entity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * Exporta DDL PostgreSQL a partir das entidades JPA (sem banco).
 * Executar: {@code mvn test -Dtest=HibernateBaselineExporterTest -DGENERATE_FLYWAY_BASELINE=true}
 */
class HibernateBaselineExporterTest {

    static boolean habilitado() {
        return "true".equalsIgnoreCase(System.getProperty("GENERATE_FLYWAY_BASELINE", System.getenv("GENERATE_FLYWAY_BASELINE")));
    }

    @Test
    @EnabledIf("com.consumoesperto.flyway.HibernateBaselineExporterTest#habilitado")
    void exportarBaselineInicial() throws Exception {
        Properties props = new Properties();
        props.put(Environment.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
        props.put(Environment.FORMAT_SQL, "true");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
            .applySettings(props)
            .build();

        MetadataSources sources = new MetadataSources(registry);
        List<Class<?>> entities = scanEntities("com.consumoesperto.model");
        entities.forEach(sources::addAnnotatedClass);

        Metadata metadata = sources.buildMetadata();
        Path out = Path.of("src/main/resources/db/migration/V1__baseline_inicial.sql");
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);

        SchemaExport export = new SchemaExport();
        export.setFormat(true);
        export.setDelimiter(";");
        export.setOutputFile(out.toAbsolutePath().toString());
        export.execute(EnumSet.of(TargetType.SCRIPT), SchemaExport.Action.CREATE, metadata);

        String body = Files.readString(out);
        if (body.contains("\\restrict") || body.contains("pg_dump")) {
            throw new IllegalStateException("Baseline contaminada com artefatos pg_dump/psql — limpe o arquivo e regenere.");
        }
        String header = "-- Baseline Hibernate — entidades JPA: " + entities.size() + System.lineSeparator() + System.lineSeparator();
        Files.writeString(out, header + body);

        long createTables = body.lines().filter(l -> l.toLowerCase().contains("create table")).count();
        if (createTables < 20) {
            throw new IllegalStateException("Baseline incompleta: apenas " + createTables + " CREATE TABLE");
        }
    }

    private static List<Class<?>> scanEntities(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            result.add(Class.forName(bd.getBeanClassName()));
        }
        return result;
    }
}
