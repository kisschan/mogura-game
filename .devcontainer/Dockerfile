FROM node:20

ARG TZ=Asia/Tokyo
ENV TZ="$TZ"

ARG CLAUDE_CODE_VERSION=latest

# 基本ツール + ファイアウォール用パッケージ
RUN apt-get update && apt-get install -y --no-install-recommends \
    less git procps sudo fzf zsh man-db unzip gnupg2 gh \
    iptables ipset iproute2 dnsutils aggregate jq \
    openjdk-17-jdk \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

ARG USERNAME=node

# bash履歴の永続化
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && mkdir /commandhistory \
    && touch /commandhistory/.bash_history \
    && chown -R $USERNAME /commandhistory

ENV DEVCONTAINER=true

# ワークスペースとClaude設定ディレクトリ
RUN mkdir -p /workspace /home/node/.claude /home/node/.npm-global && \
    chown -R node:node /workspace /home/node/.claude /home/node/.npm-global

WORKDIR /workspace

USER node

# Claude Code インストール
ENV NPM_CONFIG_PREFIX=/home/node/.npm-global
ENV PATH=/home/node/.npm-global/bin:$PATH
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}

# ファイアウォールスクリプト
COPY init-firewall.sh /usr/local/bin/
USER root
RUN chmod +x /usr/local/bin/init-firewall.sh && \
    echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/node-firewall && \
    chmod 0440 /etc/sudoers.d/node-firewall

USER node
