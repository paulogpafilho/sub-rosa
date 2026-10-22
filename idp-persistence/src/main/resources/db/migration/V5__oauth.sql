CREATE TABLE oauth_clients (
    client_id varchar(200) PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    secret_hash varchar(64),
    auth_method varchar(32) NOT NULL CHECK (auth_method IN ('NONE', 'CLIENT_SECRET_BASIC', 'CLIENT_SECRET_POST')),
    access_token_ttl bigint NOT NULL CHECK (access_token_ttl BETWEEN 1 AND 600),
    refresh_token_ttl bigint NOT NULL CHECK (refresh_token_ttl BETWEEN 1 AND 86400),
    version bigint NOT NULL DEFAULT 0,
    CHECK ((auth_method = 'NONE' AND secret_hash IS NULL) OR
           (auth_method <> 'NONE' AND secret_hash ~ '^[a-f0-9]{64}$'))
);
CREATE INDEX oauth_clients_application ON oauth_clients(application_id);
CREATE TABLE oauth_client_grants (
    client_id varchar(200) NOT NULL REFERENCES oauth_clients ON DELETE CASCADE,
    grant_type varchar(32) NOT NULL CHECK (grant_type IN ('authorization_code', 'client_credentials', 'refresh_token')),
    PRIMARY KEY (client_id, grant_type)
);
CREATE TABLE oauth_client_scopes (
    client_id varchar(200) NOT NULL REFERENCES oauth_clients ON DELETE CASCADE,
    scope varchar(32) NOT NULL CHECK (scope IN ('openid', 'profile', 'email', 'groups', 'roles', 'offline_access')),
    PRIMARY KEY (client_id, scope)
);
CREATE TABLE oauth_client_redirect_uris (
    client_id varchar(200) NOT NULL REFERENCES oauth_clients ON DELETE CASCADE,
    uri varchar(2000) NOT NULL,
    PRIMARY KEY (client_id, uri)
);
CREATE TABLE authorization_codes (
    code_hash varchar(64) PRIMARY KEY CHECK (code_hash ~ '^[a-f0-9]{64}$'),
    family_id uuid NOT NULL UNIQUE,
    client_id varchar(200) NOT NULL REFERENCES oauth_clients ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    redirect_uri varchar(2000) NOT NULL,
    scopes varchar(500) NOT NULL,
    nonce varchar(2000),
    code_challenge varchar(43) NOT NULL,
    auth_time timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE TABLE refresh_families (
    family_id uuid PRIMARY KEY REFERENCES authorization_codes(family_id) ON DELETE CASCADE,
    revoked_at timestamptz
);
CREATE TABLE refresh_tokens (
    token_hash varchar(64) PRIMARY KEY CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    family_id uuid NOT NULL REFERENCES refresh_families ON DELETE CASCADE,
    client_id varchar(200) NOT NULL REFERENCES oauth_clients ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    scopes varchar(500) NOT NULL,
    nonce varchar(2000),
    auth_time timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    rotated_at timestamptz
);
CREATE INDEX refresh_tokens_family ON refresh_tokens(family_id);
