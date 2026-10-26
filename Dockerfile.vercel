FROM maven:3.9.9-eclipse-temurin-21 AS maven
FROM eclipse-temurin:25-jdk AS build
COPY --from=maven /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /build
COPY pom.xml .
COPY src src
COPY database/migrations database/migrations
COPY database/schema.sql database/schema.sql
RUN mvn -B -DskipTests package
FROM tomcat:10.1-jdk25-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /build/target/clinic-manager.war /usr/local/tomcat/webapps/clinic-manager.war
COPY deploy/container-start.sh /usr/local/bin/clinic-start
RUN chmod +x /usr/local/bin/clinic-start
ENV DOCUMENT_STORAGE_MODE=mysql SCHEDULER_ENABLED=false DB_POOL_SIZE=4 COOKIE_SECURE=true PORT=8080
EXPOSE 8080
CMD ["/usr/local/bin/clinic-start"]
