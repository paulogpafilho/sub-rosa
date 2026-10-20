CREATE TABLE signing_keys (
    kid varchar(100) PRIMARY KEY,
    private_key_enc text NOT NULL,
    public_pem text NOT NULL,
    cert_pem text NOT NULL,
    status varchar(10) NOT NULL CHECK (status IN ('ACTIVE', 'RETIRING', 'RETIRED')),
    created_at timestamptz NOT NULL,
    retire_at timestamptz,
    CHECK ((status = 'ACTIVE' AND retire_at IS NULL)
        OR (status IN ('RETIRING', 'RETIRED') AND retire_at > created_at AND retire_at IS NOT NULL))
);
CREATE UNIQUE INDEX signing_keys_one_active ON signing_keys (status) WHERE status = 'ACTIVE';
