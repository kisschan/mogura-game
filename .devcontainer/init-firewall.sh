#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

echo "=== Initializing firewall ==="

IPV6_ENABLED=false
if command -v ip6tables >/dev/null 2>&1 && [ "$(cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || echo 1)" != "1" ]; then
    IPV6_ENABLED=true
fi

DOCKER_DNS_RULES=$(iptables-save | grep "127.0.0.11" || true)

iptables -F
iptables -X

if [ -n "$DOCKER_DNS_RULES" ]; then
    echo "$DOCKER_DNS_RULES" | while read -r rule; do
        iptables-restore -n <<< "*filter
$rule
COMMIT" 2>/dev/null || true
    done
fi

iptables -A INPUT -i lo -j ACCEPT
iptables -A OUTPUT -o lo -j ACCEPT
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
iptables -A INPUT -p udp --sport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 53 -j ACCEPT
iptables -A INPUT -p tcp --sport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -p tcp --sport 22 -j ACCEPT

HOST_IP=$(ip route | awk '/default/ {print $3; exit}')
if [ -n "$HOST_IP" ]; then
    iptables -A OUTPUT -d "$HOST_IP" -j ACCEPT
    iptables -A INPUT -s "$HOST_IP" -j ACCEPT
fi

ipset create allowed-domains hash:ip -exist
ipset flush allowed-domains

if [ "$IPV6_ENABLED" = true ]; then
    ip6tables -F
    ip6tables -X
    ip6tables -A INPUT -i lo -j ACCEPT
    ip6tables -A OUTPUT -o lo -j ACCEPT
    ip6tables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
    ip6tables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
    ip6tables -A OUTPUT -p udp --dport 53 -j ACCEPT
    ip6tables -A INPUT -p udp --sport 53 -j ACCEPT
    ip6tables -A OUTPUT -p tcp --dport 53 -j ACCEPT
    ip6tables -A INPUT -p tcp --sport 53 -j ACCEPT
    ip6tables -A OUTPUT -p tcp --dport 22 -j ACCEPT
    ip6tables -A INPUT -p tcp --sport 22 -j ACCEPT

    HOST_IP_V6=$(ip -6 route | awk '/default/ {print $3; exit}')
    if [ -n "$HOST_IP_V6" ]; then
        ip6tables -A OUTPUT -d "$HOST_IP_V6" -j ACCEPT
        ip6tables -A INPUT -s "$HOST_IP_V6" -j ACCEPT
    fi

    ipset create allowed-domains-v6 hash:ip family inet6 -exist
    ipset flush allowed-domains-v6
else
    echo "IPv6 disabled; skipping ip6tables policy"
fi

ALLOWED_DOMAINS=(
    "api.anthropic.com"
    "statsig.anthropic.com"
    "sentry.io"
    "registry.npmjs.org"
)

for domain in "${ALLOWED_DOMAINS[@]}"; do
    ips=$(dig +short A "$domain" 2>/dev/null | grep -E '^[0-9]+\.' || true)
    for ip in $ips; do
        ipset add allowed-domains "$ip" -exist
        echo "  Allowed IPv4: $domain -> $ip"
    done

    if [ "$IPV6_ENABLED" = true ]; then
        ips_v6=$(dig +short AAAA "$domain" 2>/dev/null | grep ':' || true)
        for ip in $ips_v6; do
            ipset add allowed-domains-v6 "$ip" -exist
            echo "  Allowed IPv6: $domain -> $ip"
        done
    fi
done

GITHUB_META=$(curl -s --max-time 10 https://api.github.com/meta 2>/dev/null || echo "{}")
for range in $(echo "$GITHUB_META" | jq -r '(.web // [])[]' 2>/dev/null || true); do
    iptables -A OUTPUT -d "$range" -j ACCEPT 2>/dev/null || true
    if [ "$IPV6_ENABLED" = true ]; then
        ip6tables -A OUTPUT -d "$range" -j ACCEPT 2>/dev/null || true
    fi
done

iptables -A OUTPUT -m set --match-set allowed-domains dst -j ACCEPT
if [ "$IPV6_ENABLED" = true ]; then
    ip6tables -A OUTPUT -m set --match-set allowed-domains-v6 dst -j ACCEPT
fi

iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT DROP

if [ "$IPV6_ENABLED" = true ]; then
    ip6tables -P INPUT DROP
    ip6tables -P FORWARD DROP
    ip6tables -P OUTPUT DROP
fi

echo "=== Firewall initialized ==="
echo "--- Verification ---"

if curl -s --max-time 5 https://example.com > /dev/null 2>&1; then
    echo "WARNING: example.com is reachable (should be blocked)"
else
    echo "OK: example.com blocked"
fi

if curl -s --max-time 5 https://api.anthropic.com > /dev/null 2>&1; then
    echo "OK: api.anthropic.com reachable"
else
    echo "WARNING: api.anthropic.com not reachable"
fi
