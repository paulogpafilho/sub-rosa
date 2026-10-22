ARG BUILD_IMAGE=maven:3.9.15-eclipse-temurin-21
FROM ${BUILD_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml ./
COPY config ./config
COPY idp-domain ./idp-domain
COPY idp-oidc ./idp-oidc
COPY idp-saml ./idp-saml
COPY idp-persistence ./idp-persistence
COPY idp-web ./idp-web
COPY demo ./demo
ARG MODULE=idp-web
RUN mvn -B -ntp -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system subrosa && useradd --system --gid subrosa --home-dir /app subrosa
WORKDIR /app
ARG MODULE=idp-web
COPY --from=build --chown=subrosa:subrosa /workspace/${MODULE}/target/*.jar app.jar
USER subrosa
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
