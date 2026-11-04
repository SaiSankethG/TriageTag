#!/bin/zsh -i
# Runs the packaged app with the real LLM. ANTHROPIC_API_KEY comes from ~/.zshrc (this is an interactive zsh so it is
# loaded); the key is never printed. Extra arguments are passed to the app, e.g. --triage.llm.daily-token-budget=1500000
cd "$(dirname "$0")/.." || exit 1
if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "ANTHROPIC_API_KEY is not set" >&2
  exit 1
fi
exec java -jar target/issue-triage-assistant-0.0.1-SNAPSHOT.jar --triage.llm.provider=anthropic "$@" > target/app.log 2>&1
