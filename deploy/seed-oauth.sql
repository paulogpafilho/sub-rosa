-- Explicit first-party registrations, after deploy/seed.sql.
-- Requires seed_redirect_uri; optionally seed_client_secret_hash for the machine client.
-- No plaintext secret is stored. Existing registrations are never overwritten.
\set ON_ERROR_STOP on
\if :{?seed_redirect_uri}
\else
DO $$ BEGIN RAISE EXCEPTION 'Set seed_redirect_uri to the exact demo callback URL.'; END $$;
\endif
BEGIN;
INSERT INTO oauth_clients(client_id, application_id, auth_method, access_token_ttl, refresh_token_ttl)
VALUES ('invoice-demo', '10000000-0000-0000-0000-000000000001', 'NONE', 600, 86400)
ON CONFLICT DO NOTHING;
INSERT INTO oauth_client_grants(client_id, grant_type) VALUES
('invoice-demo', 'authorization_code'), ('invoice-demo', 'refresh_token') ON CONFLICT DO NOTHING;
INSERT INTO oauth_client_scopes(client_id, scope) VALUES
('invoice-demo', 'openid'), ('invoice-demo', 'profile'), ('invoice-demo', 'email'),
('invoice-demo', 'groups'), ('invoice-demo', 'roles'), ('invoice-demo', 'offline_access') ON CONFLICT DO NOTHING;
-- A repeated seed must never widen the set of trusted callbacks.
INSERT INTO oauth_client_redirect_uris(client_id, uri)
SELECT 'invoice-demo', :'seed_redirect_uri'
WHERE NOT EXISTS (SELECT 1 FROM oauth_client_redirect_uris WHERE client_id = 'invoice-demo')
ON CONFLICT DO NOTHING;
\if :{?seed_client_secret_hash}
INSERT INTO oauth_clients(client_id, application_id, secret_hash, auth_method, access_token_ttl, refresh_token_ttl)
VALUES ('invoice-machine', '10000000-0000-0000-0000-000000000001', :'seed_client_secret_hash', 'CLIENT_SECRET_BASIC', 600, 86400)
ON CONFLICT DO NOTHING;
INSERT INTO oauth_client_grants VALUES ('invoice-machine', 'client_credentials') ON CONFLICT DO NOTHING;
INSERT INTO oauth_client_scopes VALUES ('invoice-machine', 'roles') ON CONFLICT DO NOTHING;
\endif
COMMIT;
