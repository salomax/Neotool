# =============================================================================
# Invistus DevPod — Dockerfile
# Base: Microsoft Playwright image (Ubuntu 24.04 Noble) with Chromium,
#       Firefox and WebKit pre-installed.  Everything else is layered on top.
# =============================================================================

FROM mcr.microsoft.com/playwright:v1.56.0-noble

ARG DEBIAN_FRONTEND=noninteractive

# -----------------------------------------------------------------------------
# 1. System packages
# -----------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        wget \
        git \
        ca-certificates \
        gnupg \
        lsb-release \
        build-essential \
        unzip \
        zip \
        jq \
        sudo \
        locales \
        procps \
        tmux \
        python3 \
        python3-pip \
        python3-venv \
    && locale-gen en_US.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# -----------------------------------------------------------------------------
# 2. JDK 21 (Eclipse Temurin via Adoptium)
# -----------------------------------------------------------------------------
RUN curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] \
        https://packages.adoptium.net/artifactory/deb \
        $(lsb_release -cs) main" \
        > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# -----------------------------------------------------------------------------
# 3. Node 22 LTS
# -----------------------------------------------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf "$(which node)" /usr/local/bin/node \
    && ln -sf "$(which npm)" /usr/local/bin/npm \
    && ln -sf "$(which npx)" /usr/local/bin/npx

# -----------------------------------------------------------------------------
# 4. GitHub CLI
# -----------------------------------------------------------------------------
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) \
        signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] \
        https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get install -y gh \
    && rm -rf /var/lib/apt/lists/*

# -----------------------------------------------------------------------------
# 5. AI agent CLIs via npm
# -----------------------------------------------------------------------------
RUN npm install -g         pnpm         @anthropic-ai/claude-code         @openai/codex         @google/gemini-cli     && npm cache clean --force

# -----------------------------------------------------------------------------
# 6. Cursor CLI (headless agent — cursor.com/docs/cli)
#    O script oficial instala o binário `agent` em /root/.local/bin durante
#    o build.  Copiamos para /usr/local/bin/cursor para ficar acessível ao
#    usuário não-root e ao PATH padrão do container.
# -----------------------------------------------------------------------------
RUN curl -fsSL https://cursor.com/install | bash \
    && cp /root/.local/bin/agent /usr/local/bin/cursor \
    && chmod 755 /usr/local/bin/cursor

# -----------------------------------------------------------------------------
# 7. uv — Python package manager (needed by Trae Agent)
# -----------------------------------------------------------------------------
RUN curl -LsSf https://astral.sh/uv/install.sh \
        | UV_INSTALL_DIR=/usr/local/bin sh

# -----------------------------------------------------------------------------
# 8. Trae Agent CLI (bytedance/trae-agent)
#    Python-based, multi-LLM software engineering agent.
#    Installed system-wide at /opt/trae-agent; trae-cli symlinked globally.
# -----------------------------------------------------------------------------
RUN git clone --depth 1 https://github.com/bytedance/trae-agent.git /opt/trae-agent \
    && cd /opt/trae-agent \
    && uv sync --all-extras \
    && cp /opt/trae-agent/.venv/bin/trae-cli /usr/local/bin/trae-cli \
    && chmod 755 /usr/local/bin/trae-cli \
    && chmod -R a+rX /opt/trae-agent

# -----------------------------------------------------------------------------
# 9. Non-root dev user
# -----------------------------------------------------------------------------
ARG USERNAME=dev
ARG USER_UID=1000
ARG USER_GID=1000

# A imagem base do Playwright tem pwuser:pwuser em UID/GID 1000.
# Renomeamos o grupo e o user existentes; se não existirem, criamos.
RUN EXISTING_GROUP="$(getent group ${USER_GID} | cut -d: -f1)" \
    && EXISTING_USER="$(getent passwd ${USER_UID} | cut -d: -f1)" \
    && if [ -n "${EXISTING_GROUP}" ]; then \
        groupmod -n "${USERNAME}" "${EXISTING_GROUP}"; \
    else \
        groupadd --gid "${USER_GID}" "${USERNAME}"; \
    fi \
    && if [ -n "${EXISTING_USER}" ]; then \
        usermod -l "${USERNAME}" -d "/home/${USERNAME}" -m -s /bin/bash "${EXISTING_USER}"; \
    else \
        useradd --uid "${USER_UID}" --gid "${USER_GID}" -m "${USERNAME}" -s /bin/bash; \
    fi \
    && echo "${USERNAME} ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/"${USERNAME}" \
    && chmod 0440 /etc/sudoers.d/"${USERNAME}"

# Playwright browsers (stored under /ms-playwright) must be readable by dev user
RUN chmod -R 755 /ms-playwright 2>/dev/null || true

# Gradle user-home inside the container (caches downloaded wrappers/deps)
ENV GRADLE_USER_HOME=/home/${USERNAME}/.gradle

# -----------------------------------------------------------------------------
# 10. Finalize
# -----------------------------------------------------------------------------
USER ${USERNAME}
WORKDIR /workspaces

# Smoke-test: ferramentas sem dependência de auth/rede
RUN java -version \
    && node --version \
    && npm --version \
    && git --version \
    && gh --version \
    && cursor --help > /dev/null 2>&1 || true \
    && trae-cli --help > /dev/null 2>&1 || true
