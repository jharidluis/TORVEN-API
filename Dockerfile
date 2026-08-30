# Imagen para desplegar la API movil de Torven en Render (o cualquier host con Docker).
# La app de escritorio no usa este Dockerfile, solo la API.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -Papi -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/sistema-venta-tienda-*-all.jar app.jar

# Render (y Railway) inyectan la variable PORT en tiempo de ejecucion; ApiMain la lee sola.
CMD ["java", "-jar", "app.jar"]
