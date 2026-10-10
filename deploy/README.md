# Deploy

Local dev and the server use the same `docker-compose.yml`. Locally, `deploy/.env` (gitignored) holds
`COMPOSE_FILE=docker-compose.yml;docker-compose.dev.yml`, which adds a code mount, `--reload`, the `samples/`
mount and the `expense_test` database. The server has no `deploy/.env`, so only `docker-compose.yml` is used.

## Local

```bash
cp .env.example .env                      # fill in real values
printf 'COMPOSE_FILE=docker-compose.yml;docker-compose.dev.yml\n' > deploy/.env   # use ':' on Linux/macOS
cd deploy && docker compose up -d --build
docker compose exec api alembic upgrade head
docker compose exec api pytest -m "not openai"
```

## Server (EC2 54.251.52.235) — first deploy

Run long steps inside `tmux new -s ev`. Steps marked ⚠ change shared server config: ask Loi first.

```bash
# 1. DNS
dig +short expense-voice.s-itpro.com          # must print 54.251.52.235

# 2. Code + secrets
git clone https://github.com/Sharko2025/expense-voice.git ~/expense-voice
cd ~/expense-voice
cp .env.example .env && chmod 600 .env        # set DEVICE_TOKEN, OPENAI_*, POSTGRES_*, DATABASE_URL
#   DEVICE_TOKEN:      python3 -c "import secrets;print(secrets.token_urlsafe(32))"
#   POSTGRES_PASSWORD: python3 -c "import secrets;print(secrets.token_urlsafe(24))"

# 3. Containers
cd deploy
docker compose up -d --build
docker compose exec api alembic upgrade head
curl -s 127.0.0.1:8000/api/v1/health          # ok

# 4. Exposure check: API only on 127.0.0.1:8000, nothing on 5432
sudo ss -ltnp | grep -E ':8000|:5432'

# 5. ⚠ nginx
sudo cp nginx/expense-voice.conf /etc/nginx/sites-available/expense-voice.conf
sudo ln -s /etc/nginx/sites-available/expense-voice.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 6. ⚠ SSL (adds the 443 block + 80 -> 443 redirect)
sudo certbot --nginx -d expense-voice.s-itpro.com
```

## Update

```bash
cd ~/expense-voice && git pull
cd deploy && docker compose up -d --build && docker compose exec api alembic upgrade head
```

## Smoke test from outside

```bash
curl https://expense-voice.s-itpro.com/api/v1/health
curl -H "Authorization: Bearer $DEVICE_TOKEN" \
  -F file=@samples/banh-mi.m4a -F entry_id=$(uuidgen) -F recorded_at=$(date -Iseconds) \
  https://expense-voice.s-itpro.com/api/v1/voice
```

## Backup / restore

`backup.sh` dumps Postgres, uploads to `$BACKUP_S3_URI`, keeps the newest 30. Cron is set up in Phase 4.

```bash
# restore
aws s3 cp "$BACKUP_S3_URI/expense-YYYYMMDD-HHMMSS.sql.gz" - | gunzip | \
  docker compose exec -T db psql -U expense -d expense
```
