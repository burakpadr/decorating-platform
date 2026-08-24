# Deployment

Single VPS, Docker Compose, Caddy for TLS. Five services: `caddy`, `web`, `api`, `postgres`,
`minio`.

## First run

```sh
cp infra/.env.example infra/.env
chmod 600 infra/.env
$EDITOR infra/.env            # DOMAIN and every change-me

docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
```

Flyway runs on API startup, so there is no separate migration step.

## Bucket CORS is a server setting, not a bucket setting

The capture flow uploads straight from the browser to MinIO. Without `PUT` allowed from the web
origin the preflight fails, the upload silently does nothing, and no quote can be produced.

It is set on the `minio` service, from `DOMAIN`:

```yaml
MINIO_API_CORS_ALLOW_ORIGIN: https://${DOMAIN}
```

**Not `mc cors set`.** MinIO answers `PutBucketCors` with "a header you provided implies
functionality that is not implemented", so the per-bucket rule this file documented until BOYA-40
never worked — and it failed in a way nobody would notice from the server side, because the request
that breaks is a preflight in somebody else's browser. `MinioPhotoStorageTest` performs that
preflight against a real MinIO and reads this setting out of both compose files, so the two cannot
drift apart again.

Leaving it unset is not neutral: the default is `*`, which lets a page on any domain use a
presigned URL the customer's own session was handed.

## One thing `minio-init` does not do

**ILM expiry.** A backstop to the `PhotoPurge` scheduled job. Run both — a retention policy that
depends on one scheduler is a policy with a single point of failure.

```sh
docker compose -f infra/docker-compose.yml exec minio \
  mc ilm rule add --expire-days 60 local/$MINIO_BUCKET
```

60 days, not 30: `PhotoPurge` deletes 30 days after a quote closes, and ILM must not race it.

## Backups

The database is the irreplaceable asset. Photos are ephemeral by design, but price book versions
and calibration history cannot be regenerated.

```sh
docker compose -f infra/docker-compose.yml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "decorating-$(date +%F).sql.gz"
```

Ship it **off the server** — a dump sitting on the disk that failed is not a backup. Photos need a
lifecycle separate from database backups: a 30-day deletion policy alongside 90-day backups is a
policy that exists only on paper.

Enable weekly provider snapshots as well.

## Disk

The likeliest failure on a single VPS is MinIO, Postgres WAL and container logs filling the disk at
the same time. Log rotation is configured in the compose file; set a usage alarm at 75%.

```sh
df -h /
docker system df
```
