# =============================================================================
# Invistus DevPod — Dockerfile
# Base: Microsoft Playwright image (Ubuntu 24.04 Noble) with Chromium,
#       Firefox and WebKit pre-installed.  Everything else is layered on top.
# =============================================================================

FROM mcr.microsoft.com/playwright:v1.58.2-noble

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
        vim \
        lsof \
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
    && ln -sf /usr/lib/jvm/temurin-21-jdk-$(dpkg --print-architecture) /usr/lib/jvm/temurin-21 \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21
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
# 4. Go
# -----------------------------------------------------------------------------
ARG GO_VERSION=1.23.4
RUN ARCH=$(dpkg --print-architecture)     && curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${ARCH}.tar.gz"         | tar -xz -C /usr/local     && ln -sf /usr/local/go/bin/go /usr/local/bin/go     && ln -sf /usr/local/go/bin/gofmt /usr/local/bin/gofmt

ENV GOPATH=/home/dev/go
ENV PATH="${PATH}:/usr/local/go/bin:/home/dev/go/bin"

# -----------------------------------------------------------------------------
# 5. GitHub CLI
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
# 6. AI agent CLIs via npm
# -----------------------------------------------------------------------------
RUN npm install -g         pnpm         @anthropic-ai/claude-code         @openai/codex         @google/gemini-cli     && npm cache clean --force

# -----------------------------------------------------------------------------
# 7. Cursor CLI (headless agent — cursor.com/docs/cli)
#    O script oficial instala o binário `agent` em /root/.local/bin durante
#    o build.  Copiamos para /usr/local/bin/cursor para ficar acessível ao
#    usuário não-root e ao PATH padrão do container.
# -----------------------------------------------------------------------------
RUN mkdir -p /root/.local/bin \
    && curl -fsSL https://cursor.com/install | bash \
    && AGENT_BIN=$(find /root/.local/bin -maxdepth 3 -type f -executable 2>/dev/null | head -1) \
    && if [ -n "$AGENT_BIN" ]; then \
        cp "$(readlink -f "$AGENT_BIN")" /usr/local/bin/cursor \
        && chmod 755 /usr/local/bin/cursor \
        && ln -sf /usr/local/bin/cursor /usr/local/bin/agent; \
    else \
        echo "Warning: cursor binary not found after install, skipping"; \
    fi



# -----------------------------------------------------------------------------
# 8. Python agent dependencies
# -----------------------------------------------------------------------------
RUN pip3 install --break-system-packages slack-bolt

# -----------------------------------------------------------------------------
# 9. Gum — Charm TUI toolkit (interactive menus in shell scripts)
# -----------------------------------------------------------------------------
RUN curl -fsSL https://repo.charm.sh/apt/gpg.key \
        | gpg --dearmor -o /etc/apt/keyrings/charm.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/charm.gpg] https://repo.charm.sh/apt/ * *" \
        > /etc/apt/sources.list.d/charm.list \
    && apt-get update \
    && apt-get install -y gum \
    && rm -rf /var/lib/apt/lists/*

# -----------------------------------------------------------------------------
# 10. cloudflared — Cloudflare Tunnel client
# -----------------------------------------------------------------------------
RUN ARCH=$(dpkg --print-architecture) \
    && case "$ARCH" in \
        amd64) CF_ARCH="amd64" ;; \
        arm64) CF_ARCH="arm64" ;; \
        *) echo "Unsupported arch: $ARCH" && exit 1 ;; \
    esac \
    && curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${CF_ARCH}" \
        -o /usr/local/bin/cloudflared \
    && chmod 755 /usr/local/bin/cloudflared

# -----------------------------------------------------------------------------
# 11. Zsh — productive shell (autosuggestions, syntax highlight, fzf)
# -----------------------------------------------------------------------------
RUN apt-get update \
    && apt-get install -y --no-install-recommends zsh fzf \
    && rm -rf /var/lib/apt/lists/* \
    && git clone --depth=1 https://github.com/zsh-users/zsh-autosuggestions \
        /usr/local/share/zsh-autosuggestions \
    && git clone --depth=1 https://github.com/zsh-users/zsh-syntax-highlighting \
        /usr/local/share/zsh-syntax-highlighting

# -----------------------------------------------------------------------------
# 12. Non-root dev user
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
        usermod -l "${USERNAME}" -d "/home/${USERNAME}" -m -s /bin/zsh "${EXISTING_USER}"; \
    else \
        useradd --uid "${USER_UID}" --gid "${USER_GID}" -m "${USERNAME}" -s /bin/zsh; \
    fi \
    && echo "${USERNAME} ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/"${USERNAME}" \
    && chmod 0440 /etc/sudoers.d/"${USERNAME}"

# Playwright browsers (stored under /ms-playwright) must be readable by dev user
RUN chmod -R 755 /ms-playwright 2>/dev/null || true

# Gradle user-home inside the container (caches downloaded wrappers/deps)
ENV GRADLE_USER_HOME=/home/${USERNAME}/.gradle

# -----------------------------------------------------------------------------
# 13. Finalize
# -----------------------------------------------------------------------------
USER ${USERNAME}

# .zshrc — history, autosuggestions, syntax highlighting, fzf
COPY --chown=dev:dev .devcontainer/zshrc /home/dev/.zshrc

WORKDIR /workspaces

# Smoke-test: ferramentas sem dependência de auth/rede
RUN java -version \
    && node --version \
    && npm --version \
    && git --version \
    && gh --version \
    && cursor --help > /dev/null 2>&1 || true \
    && cloudflared --version 2>&1 || true
