# Shared controller image for the test, build, and deploy workflow jobs.
# Keep this image root-based: GitHub Actions mounts the checked-out workspace
# into job containers and requires the default user to be able to access it.
FROM maven:3.9.9-eclipse-temurin-21

LABEL org.opencontainers.image.source="https://github.com/UnstoppableWorm/tech-playground"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        docker.io \
        git \
        openssh-client \
        python3 \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*

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

ENV PATH="/opt/ansible/bin:${PATH}" \
    ANSIBLE_COLLECTIONS_PATH="/opt/ansible/collections" \
    PYTHONDONTWRITEBYTECODE=1
