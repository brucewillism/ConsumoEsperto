# =============================================================================
# DOCKERFILE - CONSUMO ESPERTO BACKEND
# =============================================================================
# Este arquivo define a imagem Docker para o backend da aplicação
# ConsumoEsperto. A imagem é baseada no OpenJDK 11 e usa Maven
# para compilar a aplicação Spring Boot.
# =============================================================================

# Imagem base: OpenJDK 11 com Debian slim (menor tamanho)
# Slim remove ferramentas desnecessárias para reduzir o tamanho da imagem
FROM openjdk:11-jdk-slim

# Define o diretório de trabalho dentro do container
# Todas as operações subsequentes serão executadas neste diretório
WORKDIR /app

# =============================================================================
# PREPARAÇÃO DO MAVEN
# =============================================================================

# Copia o Maven wrapper (mvnw) para o container
# O Maven wrapper permite executar Maven sem instalar globalmente
COPY mvnw .

# Copia o diretório .mvn que contém configurações do Maven wrapper
COPY .mvn .mvn

# Copia o arquivo pom.xml com as dependências do projeto
COPY pom.xml .

# Concede permissão de execução ao Maven wrapper
# Necessário para executar o comando ./mvnw
RUN chmod +x mvnw

# =============================================================================
# DOWNLOAD DE DEPENDÊNCIAS
# =============================================================================

# Baixa todas as dependências Maven offline
# -B: modo batch (não interativo)
# dependency:go-offline: baixa dependências para cache local
# Esta etapa é separada para aproveitar o cache de camadas do Docker
RUN ./mvnw dependency:go-offline -B

# =============================================================================
# COMPILAÇÃO DA APLICAÇÃO
# =============================================================================

# Copia todo o código fonte da aplicação
# Inclui controllers, services, models, etc.
COPY src src

# Compila a aplicação Spring Boot
# clean: remove builds anteriores
# package: cria o JAR executável
# -DskipTests: pula testes para acelerar o build
RUN ./mvnw clean package -DskipTests

# =============================================================================
# CONFIGURAÇÃO FINAL
# =============================================================================

# Expõe a porta 8080 onde a aplicação Spring Boot será executada
# Esta porta deve corresponder à configuração em application.properties
EXPOSE 8080

# Comando padrão para executar a aplicação quando o container iniciar
# java -jar: executa o JAR compilado
# O nome do JAR deve corresponder ao definido no pom.xml
CMD ["java", "-jar", "target/consumo-esperto-backend-0.0.1-SNAPSHOT.jar"]
