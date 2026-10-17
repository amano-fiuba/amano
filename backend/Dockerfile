# ---- Etapa 1: compilar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Primero solo lo necesario para descargar dependencias (se cachea esta capa)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Después el código y la compilación
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- Etapa 2: ejecutar ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
