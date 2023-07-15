FROM debian:stable-slim AS build

RUN apt-get update && \
    apt-get install -y git curl unzip

WORKDIR /workspace/app
COPY . .

RUN git clone https://github.com/asdf-vm/asdf.git /root/.asdf --branch v0.12.0
ENV PATH="/root/.asdf/bin:/root/.asdf/shims:${PATH}"

RUN cat .tool-versions | cut -d' ' -f1 | grep "^[^\#]" | xargs -i asdf plugin add {}
RUN asdf install

RUN ./gradlew shadowJar

FROM itzg/minecraft-server:java17

COPY --from=build /workspace/app/build/libs/*.jar /data/plugins/
