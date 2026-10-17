-- Explicit local development seed. No passwords, clients or administrator grants.
-- psql -v ON_ERROR_STOP=1 -v seed_username=<existing-enabled-user> -f deploy/seed.sql
\set ON_ERROR_STOP on
\if :{?seed_username}
\else
DO $$ BEGIN RAISE EXCEPTION 'Set seed_username to an existing enabled user.'; END $$;
\endif

BEGIN;
-- gset fails if the selected user does not exist or is disabled.
SELECT id AS seed_user_id FROM users WHERE username = :'seed_username' AND enabled \gset

INSERT INTO applications (id, name, audience, description) VALUES
('10000000-0000-0000-0000-000000000001', 'Invoice Demo', 'urn:subrosa:invoice-demo', 'Local access-model demonstration')
ON CONFLICT (id) DO NOTHING;

INSERT INTO groups (id, name) VALUES
('20000000-0000-0000-0000-000000000001', 'Finance')
ON CONFLICT (id) DO NOTHING;

INSERT INTO roles (id, application_id, name) VALUES
('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Reader'),
('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Approver')
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_entitlements (role_id, entitlement) VALUES
('30000000-0000-0000-0000-000000000001', 'invoices:read'),
('30000000-0000-0000-0000-000000000002', 'invoices:read'),
('30000000-0000-0000-0000-000000000002', 'invoices:approve')
ON CONFLICT DO NOTHING;

INSERT INTO user_groups (user_id, group_id) VALUES
(:'seed_user_id', '20000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id) VALUES
(:'seed_user_id', '30000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO group_roles (group_id, role_id) VALUES
('20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002') ON CONFLICT DO NOTHING;
COMMIT;
