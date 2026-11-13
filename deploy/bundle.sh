#!/usr/bin/env bash
# Run on the Mac. Packs everything the VM needs into one folder, so the VM never has to build anything or backfill for an hour:
#   1. the container image (built with ./mvnw spring-boot:build-image, arm64 like the VM), saved as a .tar.gz
#   2. a dump of the local database (the 44k spring-boot issues, embeddings, docs, reports), in pg_dump's custom format
#   3. the k3s manifests, the HTTPS config, vm-setup.sh and the smoke test
# No secrets go into the bundle: vm-setup.sh creates the Kubernetes Secrets from environment variables on the VM.
#
# Usage:   deploy/bundle.sh            (writes ~/triage-bundle/)
# Then:    scp -i <key> -r ~/triage-bundle ubuntu@<vm-ip>:~
set -euo pipefail

cd "$(dirname "$0")/.."                                   # the repository root
out="${BUNDLE_DIR:-$HOME/triage-bundle}"
# The image name k3s will run (K3sManifestsTest checks it equals the pom's version).
image=$(grep -o 'docker.io/library/issue-triage-assistant:[^" ]*' deploy/k3s/30-app.yaml | head -1)
rm -rf "$out/k3s" "$out/https" "$out/scripts" "$out/fixtures"   # no leftovers from an earlier bundle
mkdir -p "$out/k3s" "$out/https" "$out/scripts" "$out/fixtures"

echo "1/3 Building the image $image (a few minutes)..."
./mvnw -q -DskipTests spring-boot:build-image
docker save "$image" | gzip > "$out/triage-image.tar.gz"

echo "2/3 Dumping the local database..."
docker-compose exec -T postgres pg_dump -U copilot -d triage --format=custom > "$out/triage.dump"
pg_list_ok=$(docker run --rm -i --entrypoint pg_restore pgvector/pgvector:pg16-trixie --list < "$out/triage.dump" | grep -c "TABLE DATA" || true)
if [ "$pg_list_ok" -lt 10 ]; then
  echo "The dump looks wrong (only $pg_list_ok tables with data). Is docker-compose's Postgres running?" >&2
  exit 1
fi

echo "3/3 Copying the manifests and scripts..."
cp deploy/k3s/*.yaml "$out/k3s/"
rm -f "$out/k3s/secret.yaml"                              # never ship a filled-in secret, even if one exists locally
cp deploy/https/*.yaml "$out/https/"
cp deploy/vm-setup.sh "$out/"
cp scripts/smoke-test.py "$out/scripts/"
cp src/test/resources/github/webhook-issues-opened.json "$out/fixtures/"
# Repositories kept out of the public copy (your test repos): vm-setup.sh deletes their data right after the restore.
# Your local database is not changed. Space-separated; override with HIDE_REPOS="owner/a owner/b" deploy/bundle.sh
printf '%s\n' ${HIDE_REPOS:-SaiSankethG/triage-sandbox} > "$out/hidden-repos.txt"

echo
du -sh "$out"/* | sed 's|'"$out"'/||'
echo
echo "Done: $out"
echo "Next: scp -i <your-key> -r $out ubuntu@<vm-ip>:~   then on the VM: cd triage-bundle && HOST=... ./vm-setup.sh"
echo "(vm-setup.sh lists the environment variables it needs at the top.)"
