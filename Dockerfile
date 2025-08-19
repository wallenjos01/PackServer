FROM gradle:8.12 AS build

COPY . /src

RUN set -eux; \
    cd /src; \
    gradle build

FROM eclipse-temurin:21-jre-jammy

ARG UID=920
ARG GID=920

VOLUME /data
WORKDIR /data
STOPSIGNAL SIGINT

RUN set -eux; \
	apt-get update; \ 
	apt-get install -y sudo

RUN set -eux; \
    mkdir -p /data; \
    groupadd -g "${GID}" packserver; \
    useradd --create-home --no-log-init -s /bin/bash -d /server -u "${GID}" -g "${GID}" packserver; \
    chown packserver:packserver /data; \
    chmod -R 775 /data

COPY --from=build --chmod=770 --chown=packserver:packserver /src/build/out/packserver.jar /server
COPY --chmod=775 docker-entrypoint.sh /
COPY --chmod=775 wrapper.sh /

USER root
ENTRYPOINT [ "/docker-entrypoint.sh" ]
CMD [ "/wrapper.sh" ]
