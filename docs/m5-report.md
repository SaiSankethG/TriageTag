# M5 report: ship it (STOP B)

4 Nov 2026. M5 steps 0 to 5 of `docs/prompts/m3-m6-build.md` are done, tested and pushed, plus three extras the coordinator
added (draft prompt v3, a blind v2/v3 comparison, demo pairs) and a friendlier demo page. **This is STOP B**: the next steps
need sai (a cloud VM, a GitHub App, a video). Nothing was deployed and no server was touched.

## What each step did

| Step | What it did | Commit |
|---|---|---|
| 0 | **Transactional outbox**: the webhook writes the delivery row + an outbox row in one transaction and answers without Kafka; a relay sends rows in order, stops at the first failure, marks them sent after the ack. Tested with Kafka down, then up (real Postgres + Kafka) | `f63f3c9` |
| 1 (check) | **Comment threshold**: one LLM pass (204 calls, ≈ $0.03) scored 0.825 / 0.85 / 0.90. Stricter alone doesn't help (≈ 6% → 4% right comments); 21 of 48 confirmed false alarms are forward-/back-port clones. Default now **0.90 + LLM "yes"**; all 48 listed for sai | `17e3507` |
| demo page | Verdict banner in words + colour, High/Medium/Low similarity, issue card, recent issues + title search, shareable `?repo=&number=`, ✓/✗ checklist, copy button, calm "AI paused" note; phone width, dark mode, keyboard. New `GET /settings`, `?q=` search | `2898c84` |
| 1 | **Container image**: `./mvnw spring-boot:build-image`, linux/arm64, buildpacks (44 s, 1.2 GB). Ran it: health UP, embeddings work inside the container | `ea7cdac` |
| 2 | **k3s manifests** in `deploy/k3s/`: Postgres + Kafka StatefulSets with volumes, the app with startup/liveness/readiness probes, an Ingress, `secret.example.yaml` only. Sized for 2 OCPU / 12 GB | `f631a81` |
| 3 | **GitHub App auth**: RS256 JWT with `java.security`, installation token cached until 5 min before expiry; reads GitHub's PKCS#1 keys. Tests use generated keys | `2872b9e` |
| 4 | **Comment mode**, off by default (`repo_setting`, SQL only), at most one comment per issue (reserved before posting); only open issues from outside the team, no clones, match ≥ 0.90 + LLM yes | `0e07df2` |
| A | **Draft prompt v3**: gets the top match (state, level, LLM verdict) and the missing-details result; writes as a maintainer; asks only for what's missing. v2 unchanged | `05800cb` |
| B | **Blind A/B**: the same 30 issues with v2 and v3 drafts in `docs/m4-scoring-v3.md` (91,588 tokens) | `7aafdcf` |
| C | **Demo script** + three duplicate pairs whose text never mentions the original (`docs/demo-script.md`) | `1ab9280` |
| 5 | **README**: problem, Mermaid diagram, results, run, design decisions, limits | the commit that adds this report |
| fix | **Admin endpoints protected** (review finding before deploying): every non-GET endpoint except the webhook needs `X-Admin-Token` = `TRIAGE_ADMIN_TOKEN` (unset → 503, wrong → 401); the Ingress routes only public paths; the demo page sends the token only if pasted | the commit after this report |

## Tests
**251 tests, 0 failures** (after the admin-token fix; 245 at the M5 report) (`./mvnw test`), 30 more than at STOP A. New: outbox (unit, real DB, real Kafka down/up), threshold
maths, settings/search/report-card APIs, demo page checks, k3s manifest checks (no kubectl here, so no `--dry-run`), PEM/JWT/
token cache, comment mode (every safety check, never twice, failure not retried), draft v3 inputs.

## Dependencies
None added in M5. (The image is built by the Spring Boot plugin that was already there.)

## LLM spend in M5
Threshold eval 248,173 tokens + 30 v3 reports 91,588 + a few test reports: about **345k tokens, ≈ $0.04–0.08**. All of 4 Nov
(M4 + M5): about 830k real tokens (the `llm_usage` counter also holds ~148k tokens from the fake LLM of the app sai ran on port 8080).

## STOP B: what sai needs to do

### 1. Create the VM (Oracle Cloud, Always Free, ~20 min)
Several reports say Oracle cut the Always Free Ampere A1 allowance to **2 OCPU / 12 GB** in June 2026; Oracle's own page still
showed 4 / 24 when I checked. **Check the limit in your console before creating the VM** (Governance → Limits, Quotas and Usage →
"Ampere A1"). Going over the free allowance can get instances stopped.
1. cloud.oracle.com → sign in → **Compute → Instances → Create instance**.
2. Name `triage`. **Image**: Change image → Canonical **Ubuntu 24.04** (aarch64). **Shape**: Change shape → Ampere →
   **VM.Standard.A1.Flex**, **2 OCPUs, 12 GB**.
3. Networking: keep the default VCN/public subnet, **Assign a public IPv4 address**: yes.
4. **Add SSH keys**: Generate a key pair → **Save private key** (keep it outside the repo) → Create.
5. Open ports 80/443: the instance's subnet → Security list → **Add Ingress Rules**: source `0.0.0.0/0`, TCP, ports `80` and
   `443`. Ubuntu images on Oracle also block them in iptables; on the VM run:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   ```
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   ```
   ```bash
   sudo netfilter-persistent save
   ```

### 2. Install k3s and load the app (~20 min)
On the VM (`ssh -i <key> ubuntu@<public-ip>`):
```bash
curl -sfL https://get.k3s.io | sh -
```
```bash
sudo k3s kubectl get nodes
```
On the Mac, build the image and copy it plus the database (so the VM doesn't need a 40-minute backfill):
```bash
./mvnw -q -DskipTests spring-boot:build-image
```
```bash
docker save issue-triage-assistant:0.0.1-SNAPSHOT | gzip > ~/triage-image.tar.gz
```
```bash
docker-compose exec -T postgres pg_dump -U copilot -Fc triage > ~/triage.dump
```
```bash
scp -i <key> ~/triage-image.tar.gz ~/triage.dump deploy/k3s/*.yaml ubuntu@<public-ip>:~
```
On the VM:
```bash
gunzip -c triage-image.tar.gz | sudo k3s ctr images import -
```
```bash
cp secret.example.yaml secret.yaml && nano secret.yaml
```
(fill in the real values, including `TRIAGE_ADMIN_TOKEN`: generate it with `openssl rand -hex 32` and keep a copy in your
password manager; never copy `secret.yaml` back into the repo)
```bash
sudo k3s kubectl apply -f 00-namespace.yaml -f secret.yaml -f 10-postgres.yaml -f 20-kafka.yaml
```
Wait until `sudo k3s kubectl -n triage get pods` shows postgres-0 Running, then restore the data **before** the app starts:
```bash
sudo k3s kubectl -n triage exec -i postgres-0 -- pg_restore -U copilot -d triage --no-owner < triage.dump
```
```bash
sudo k3s kubectl apply -f 30-app.yaml
```
Check: `sudo k3s kubectl -n triage get pods` (triage-app Ready after ~1 min), then `curl -H 'Host: triage.example.com' localhost/actuator/health`.

**Admin calls on the VM** (backfill, embeddings, docs, eval, creating reports) are not routed by the Ingress at all (only
`/webhooks/github`, `/`, `/index.html`, `/reports`, `/repos`, `/settings` and `/actuator/health` are public). Reach the app
directly through a port-forward, with the header:
```bash
sudo k3s kubectl -n triage port-forward svc/triage-app 8080:80
```
then, in a second SSH session:
```bash
curl -X POST -H "X-Admin-Token: <your TRIAGE_ADMIN_TOKEN>" localhost:8080/docs/spring-projects/spring-boot
```
`POST /reports/...` is reachable through the Ingress (a Kubernetes Ingress can't filter by HTTP method), but the app refuses it
without the token; on the public demo page, paste the token into the "Admin" box to see **Create report**.

### 3. HTTPS and a hostname (your choice)
GitHub can send webhooks to plain http, but it shouldn't. The simplest options: a **Cloudflare Tunnel** (free, HTTPS, no open
ports needed) or Traefik's built-in Let's Encrypt on a domain you own. Then replace `triage.example.com` in `30-app.yaml`.
Tell me which, and I'll write the exact config.

### 4. Create the GitHub App (~10 min)
GitHub → **Settings → Developer settings → GitHub Apps → New GitHub App**:
- Name e.g. `triage-assistant-sai`; Homepage URL: the repo URL.
- **Webhook URL**: `https://<your-host>/webhooks/github`; **Webhook secret**: the same `GITHUB_WEBHOOK_SECRET` as in secret.yaml.
- **Permissions**: Repository → **Issues: Read and write**; Metadata: Read-only (automatic).
- **Subscribe to events**: **Issues**.
- **Where can this GitHub App be installed?** "Any account" (public), so maintainers can try it.
- Create → note the **App ID** → **Generate a private key** (downloads a `.pem`; keep it outside the repo).
- **Install App** → only on `SaiSankethG/triage-sandbox` for now.
On the VM:
```bash
sudo k3s kubectl -n triage create secret generic github-app-key --from-file=private-key.pem=<path-to.pem>
```
Put the App ID in `secret.yaml` (`GITHUB_APP_ID`), apply it again, and restart the app:
```bash
sudo k3s kubectl -n triage rollout restart deployment/triage-app
```
Comment mode stays off until you turn it on for the sandbox only:
```bash
sudo k3s kubectl -n triage exec -it postgres-0 -- psql -U copilot -d triage -c "INSERT INTO repo_setting VALUES ('saisankethg/triage-sandbox', 'SUGGEST')"
```
The App can only ever comment where it is installed.

### 5. Record the 2-minute demo
Script and three duplicate pairs: `docs/demo-script.md`.

### Still waiting for sai's hand scores
- `docs/m4-scoring.md`: 30 reports (useful / partly / wrong).
- `docs/m4-scoring-v3.md`: Draft A vs Draft B for the same 30 issues (key at the end; score before opening it).
- `docs/m4-results.md` section 4: the 48 confirmed "false alarms": real duplicate, yes or no?

## Open questions
1. **HTTPS option** (Cloudflare Tunnel vs Let's Encrypt on a domain), see STOP B step 3.
2. **Prompt v3 is now the default** for drafts; if sai's blind scores prefer v2, switching back is a one-line change.
3. **Comment mode on a real repo** needs a maintainer to install the App; until then only the sandbox.
4. ~~The fake LLM's estimated tokens counted in `llm_usage`.~~ Fixed in M6: only real-model tokens use up the daily budget (the
   fake's still show in the metrics, tagged `model="fake"`).
