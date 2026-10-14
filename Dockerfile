FROM maven:3.9.15-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY config ./config
COPY idp-domain ./idp-domain
COPY idp-oidc ./idp-oidc
COPY idp-saml ./idp-saml
COPY idp-persistence ./idp-persistence
COPY idp-web ./idp-web
COPY demo ./demo
RUN mvn -B -ntp -pl idp-web -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system subrosa && useradd --system --gid subrosa --home-dir /app subrosa
WORKDIR /app
COPY --from=build --chown=subrosa:subrosa /workspace/idp-web/target/idp-web-0.1.0-SNAPSHOT.jar app.jar
USER subrosa
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
