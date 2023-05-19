FROM maven:3.9.2-eclipse-temurin-17-alpine AS BUILDER

WORKDIR /builder

COPY lombok.config .
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src/main ./src/main
RUN mvn package -DskipTests


FROM eclipse-temurin:17-jre-alpine

RUN adduser -D service -S -g "First"
USER service

WORKDIR /app

COPY --from=BUILDER /builder/target/ets-rosetta-stone-2.0.0.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8081
