FROM maven:3.9.9-eclipse-temurin-21

ARG RUNNER_VERSION
ARG RUNNER_SHA256

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        docker.io \
        git \
        jq \
        openssh-client \
        python3 \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/actions-runner \
    && curl --fail --location --show-error \
        "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-arm64-${RUNNER_VERSION}.tar.gz" \
        --output /tmp/actions-runner.tar.gz \
    && echo "${RUNNER_SHA256}  /tmp/actions-runner.tar.gz" | sha256sum --check --strict \
    && tar --extract --gzip --file /tmp/actions-runner.tar.gz --directory /opt/actions-runner \
    && rm /tmp/actions-runner.tar.gz \
    && /opt/actions-runner/bin/installdependencies.sh

RUN python3 -m venv /opt/ansible \
    && /opt/ansible/bin/python -m pip install --no-cache-dir \
        ansible-core==2.17.7 \
        docker==7.1.0 \
        requests

COPY requirements.yml /tmp/ansible-requirements.yml

RUN mkdir -p /opt/ansible/collections \
    && /opt/ansible/bin/ansible-galaxy collection install \
        --requirements-file /tmp/ansible-requirements.yml \
        --collections-path /opt/ansible/collections

COPY runner/entrypoint.sh /usr/local/bin/actions-runner-entrypoint

RUN chmod 0755 /usr/local/bin/actions-runner-entrypoint \
    && mkdir -p /runner

ENV PATH="/opt/ansible/bin:${PATH}" \
    ANSIBLE_COLLECTIONS_PATH="/opt/ansible/collections" \
    PYTHONDONTWRITEBYTECODE=1 \
    RUNNER_ALLOW_RUNASROOT=1

WORKDIR /runner

ENTRYPOINT ["/usr/local/bin/actions-runner-entrypoint"]
