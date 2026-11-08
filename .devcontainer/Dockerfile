# Docker Official Image: Node.js 24 LTS, checked 2026-11-07.
FROM node:24.22.2-bookworm@sha256:8fa96264f302f58bbb13aa7339c541547b56975766ce956c875da149452a3e40

ARG TZ=Asia/Tokyo
ENV TZ="$TZ"

ARG CLAUDE_CODE_VERSION=2.1.319
ARG CODEX_CLI_VERSION=0.167.0

# 基本ツール + ファイアウォール用パッケージ
RUN apt-get update && apt-get install -y --no-install-recommends \
    less git procps sudo fzf zsh man-db unzip gnupg2 gh \
    iptables ipset iproute2 dnsutils aggregate jq \
    bubblewrap socat \
    openjdk-17-jdk \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

COPY certs/ /usr/local/share/ca-certificates/devcontainer/
RUN if find /usr/local/share/ca-certificates/devcontainer -type f -name '*.crt' | grep -q .; then \
        update-ca-certificates; \
    fi
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt

ARG USERNAME=node

# bash履歴の永続化
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && mkdir /commandhistory \
    && touch /commandhistory/.bash_history \
    && chown -R $USERNAME /commandhistory

ENV DEVCONTAINER=true

# ワークスペースとClaude設定ディレクトリ
RUN mkdir -p /workspace /home/node/.claude /home/node/.codex /home/node/.npm-global && \
    chown -R node:node /workspace /home/node/.claude /home/node/.codex /home/node/.npm-global

WORKDIR /workspace

USER node

# Claude Code / Codex CLI（上流の安定版を明示してインストール）
ENV NPM_CONFIG_PREFIX=/home/node/.npm-global
ENV PATH=/home/node/.npm-global/bin:$PATH
RUN npm install -g \
    @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION} \
    @openai/codex@${CODEX_CLI_VERSION}

# ファイアウォールスクリプト
COPY init-firewall.sh /usr/local/bin/
USER root
RUN sed -i 's/\r$//' /usr/local/bin/init-firewall.sh && \
    chmod +x /usr/local/bin/init-firewall.sh && \
    echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/node-firewall && \
    chmod 0440 /etc/sudoers.d/node-firewall

USER node
