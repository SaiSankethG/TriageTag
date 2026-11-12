#!/usr/bin/env bash
# One-command setup of TriageTag on the VM (Oracle Ubuntu 24.04, run as "ubuntu" from inside ~/triage-bundle).
# Safe to run again: every step checks first, and `kubectl apply` only changes what differs.
#
#   1. firewall: Oracle's images block ports 80/443 and reject forwarded traffic (which breaks k3s's pod network)
#   2. installs k3s (one-node Kubernetes) if it isn't there
#   3. loads the app's container image into k3s
#   4. creates the Kubernetes Secrets from YOUR environment variables (nothing secret is stored in a file), starts Postgres and Kafka
#   5. restores the database dump, only into an empty database, all or nothing
#   6. HTTPS (Let's Encrypt or a Cloudflare Tunnel), the app, the nightly backup
#   7. waits for https://$HOST, then runs the smoke test through it
#
# Environment variables (put them in your shell first, e.g. from your password manager; they are never printed):
#   HOST                    the public name: <ip-with-dashes>.sslip.io (no domain) or e.g. triage.your-domain.dev
#   POSTGRES_PASSWORD       database password. Keep the SAME value on every run: Postgres only reads it when the volume is new.
#   GITHUB_TOKEN            read-only token for public repos (backfill)
#   GITHUB_WEBHOOK_SECRET   the same value as in the GitHub App's webhook settings
#   TRIAGE_ADMIN_TOKEN      admin calls (openssl rand -hex 32)
#   ANTHROPIC_API_KEY       optional: without it the app uses the stand-in LLM
#   GITHUB_APP_ID           optional: comment mode only
#   GITHUB_APP_PRIVATE_KEY_FILE  optional: path to the App's .pem on the VM (comment mode only)
#   HTTPS_MODE              letsencrypt (default) or cloudflare
#   CLOUDFLARE_TUNNEL_TOKEN required when HTTPS_MODE=cloudflare (see https/cloudflare-tunnel.yaml)
#
# Usage:  HOST=129-146-1-2.sslip.io ./vm-setup.sh            (with the other variables already exported)
set -euo pipefail

cd "$(dirname "$0")"
kubectl() { sudo k3s kubectl "$@"; }
step() { echo; echo "== $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

HOST="${HOST:-}"
HTTPS_MODE="${HTTPS_MODE:-letsencrypt}"
[[ "$HOST" =~ ^[a-z0-9.-]+$ && "$HOST" == *[a-z]* && "$HOST" == *.* ]] \
  || fail "set HOST to a DNS name in lower case, e.g. HOST=129-146-1-2.sslip.io (not a bare IP)"
[[ "$HTTPS_MODE" == letsencrypt || "$HTTPS_MODE" == cloudflare ]] || fail "HTTPS_MODE must be letsencrypt or cloudflare"
for var in POSTGRES_PASSWORD GITHUB_TOKEN GITHUB_WEBHOOK_SECRET TRIAGE_ADMIN_TOKEN; do
  [ -n "${!var:-}" ] || fail "the environment variable $var is not set (see the top of this script)"
done
[ "$HTTPS_MODE" != cloudflare ] || [ -n "${CLOUDFLARE_TUNNEL_TOKEN:-}" ] || fail "HTTPS_MODE=cloudflare needs CLOUDFLARE_TUNNEL_TOKEN"
[ -z "${GITHUB_APP_PRIVATE_KEY_FILE:-}" ] || [ -r "$GITHUB_APP_PRIVATE_KEY_FILE" ] || fail "cannot read $GITHUB_APP_PRIVATE_KEY_FILE"

step "1/7 Firewall"
changed=0
# Oracle's Ubuntu images end the FORWARD chain with a REJECT rule. k3s routes pod-to-pod traffic through FORWARD, so with that
# rule in place DNS inside the cluster and the app's connections to Postgres and Kafka fail. Remove it.
if sudo iptables -C FORWARD -j REJECT --reject-with icmp-host-prohibited 2>/dev/null; then
  sudo iptables -D FORWARD -j REJECT --reject-with icmp-host-prohibited
  changed=1
fi
if [ "$HTTPS_MODE" = letsencrypt ]; then   # a Cloudflare Tunnel connects outwards: no inbound ports needed
  for port in 80 443; do
    if ! sudo iptables -C INPUT -m state --state NEW -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
      sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport "$port" -j ACCEPT
      changed=1
    fi
  done
  echo "Also needed once, in the Oracle console: the subnet's security list allows TCP 80 and 443 from 0.0.0.0/0."
fi
# Save only when this script changed something (normally the first run, before k3s adds its own rules, which must not be saved).
[ "$changed" = 0 ] || sudo netfilter-persistent save >/dev/null
echo "Firewall ready."

step "2/7 k3s"
if ! command -v k3s >/dev/null; then
  curl -sfL https://get.k3s.io | sh -
fi
until kubectl get nodes 2>/dev/null | grep -q " Ready"; do sleep 3; done
kubectl get nodes

step "3/7 Loading the app image"
image=$(grep -o 'docker.io/library/issue-triage-assistant:[^" ]*' k3s/30-app.yaml | head -1)
gunzip -c triage-image.tar.gz | sudo k3s ctr images import -
sudo k3s ctr images ls -q > /tmp/k3s-images.txt
grep -Fx "$image" /tmp/k3s-images.txt >/dev/null || fail "the image $image is not in k3s after the import"
echo "Loaded $image"

step "4/7 Secrets (from your environment), Postgres and Kafka"
kubectl apply -f k3s/00-namespace.yaml
# Built in memory and sent through stdin: the values never touch the disk or the command line (where `ps` could show them).
printf '%s\n' \
  "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
  "GITHUB_TOKEN=$GITHUB_TOKEN" \
  "GITHUB_WEBHOOK_SECRET=$GITHUB_WEBHOOK_SECRET" \
  "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}" \
  "GITHUB_APP_ID=${GITHUB_APP_ID:-}" \
  "TRIAGE_ADMIN_TOKEN=$TRIAGE_ADMIN_TOKEN" \
  | kubectl -n triage create secret generic triage-secrets --from-env-file=/dev/stdin --dry-run=client -o yaml \
  | kubectl apply -f -
if [ -n "${GITHUB_APP_PRIVATE_KEY_FILE:-}" ]; then
  sudo cat "$GITHUB_APP_PRIVATE_KEY_FILE" \
    | kubectl -n triage create secret generic github-app-key --from-file=private-key.pem=/dev/stdin --dry-run=client -o yaml \
    | kubectl apply -f -
fi
kubectl apply -f k3s/10-postgres.yaml -f k3s/20-kafka.yaml
kubectl -n triage rollout status statefulset/postgres --timeout=300s
# Over TCP on purpose: on a new volume Postgres first runs a temporary set-up server on the Unix socket only, then restarts.
until kubectl -n triage exec postgres-0 -- pg_isready -h 127.0.0.1 -U copilot -d triage >/dev/null 2>&1; do sleep 3; done

step "5/7 Database"
has_data=$(kubectl -n triage exec postgres-0 -- psql -U copilot -d triage -tAc "SELECT to_regclass('public.issue') IS NOT NULL")
if [ "$has_data" = "t" ]; then
  echo "The database already has the app's tables: not restoring (this is a re-run)."
else
  echo "Restoring triage.dump (a minute or two)..."
  # One transaction, stop at the first error: either everything is restored or nothing is (so a re-run can try again).
  kubectl -n triage exec -i postgres-0 -- pg_restore -U copilot -d triage --no-owner --single-transaction --exit-on-error \
    < triage.dump || fail "the restore failed and was rolled back; the database is still empty"
  issues=$(kubectl -n triage exec postgres-0 -- psql -U copilot -d triage -tAc "SELECT count(*) FROM issue")
  echo "Restored: $issues issues."
fi

step "6/7 HTTPS ($HTTPS_MODE), the app and the nightly backup (host: $HOST)"
until kubectl get crd middlewares.traefik.io >/dev/null 2>&1; do sleep 3; done   # k3s installs it; only a timing question
kubectl wait --for=condition=established crd/middlewares.traefik.io --timeout=300s
sed "s/triage\.example\.com/$HOST/g" k3s/30-app.yaml > /tmp/30-app.yaml
if [ "$HTTPS_MODE" = letsencrypt ]; then
  kubectl apply -f https/letsencrypt.yaml
  sleep 5   # k3s re-runs its Traefik install job with the new settings
  until kubectl -n kube-system get job/helm-install-traefik >/dev/null 2>&1; do sleep 3; done
  kubectl -n kube-system wait --for=condition=complete job/helm-install-traefik --timeout=300s
  kubectl -n kube-system rollout status deployment/traefik --timeout=300s
else
  printf 'token=%s\n' "$CLOUDFLARE_TUNNEL_TOKEN" \
    | kubectl -n triage create secret generic cloudflare-tunnel --from-env-file=/dev/stdin --dry-run=client -o yaml \
    | kubectl apply -f -
  sed -i '/router.tls.certresolver/d' /tmp/30-app.yaml   # Cloudflare holds the public certificate
  kubectl apply -f https/cloudflare-tunnel.yaml
  kubectl -n triage rollout status deployment/cloudflared --timeout=300s
fi
kubectl apply -f /tmp/30-app.yaml
kubectl apply -f k3s/40-backup.yaml
kubectl -n triage rollout status deployment/triage-app --timeout=600s

step "7/7 Checks"
code=""
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://$HOST/actuator/health" || true)
  [ "$code" = "200" ] && break
  sleep 5   # with Let's Encrypt, the first HTTPS request also fetches the certificate
done
if [ "$code" != "200" ]; then
  echo "https://$HOST/actuator/health did not answer 200 (last: ${code:-none}). Check:" >&2
  if [ "$HTTPS_MODE" = letsencrypt ]; then
    echo "  the Oracle security list (80/443), that $HOST points at this VM, and" >&2
    echo "  sudo k3s kubectl -n kube-system logs deploy/traefik | tail -30" >&2
    echo "  (Also try https://$HOST from your Mac: some networks don't route a VM's own public IP back to itself.)" >&2
  else
    echo "  the tunnel's public hostname settings, and: sudo k3s kubectl -n triage logs deploy/cloudflared | tail -30" >&2
    echo "  (If Cloudflare's Bot Fight Mode is on for the zone, it can block the smoke test's requests: turn it off for this host.)" >&2
  fi
  exit 1
fi
echo "https://$HOST is up with a valid certificate."
mkdir -p src/test/resources/github && cp fixtures/webhook-issues-opened.json src/test/resources/github/
PUBLIC=1 BASE_URL="https://$HOST" python3 -I scripts/smoke-test.py

echo
echo "Done. Open https://$HOST. Next: set the GitHub App's webhook URL to https://$HOST/webhooks/github"
