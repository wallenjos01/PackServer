FROM gradle:8.12 AS build

COPY . /src

RUN set -eux; \
    cd /src; \
    gradle build


FROM eclipse-temurin:21

VOLUME /data
WORKDIR /data
STOPSIGNAL SIGINT

RUN set -eux; \
    mkdir -p /server; \
    mkdir -p /data

COPY --from=build /src/build/out/packserver.jar /server
CMD [ "java", "-jar", "/server/packserver.jar" ]
