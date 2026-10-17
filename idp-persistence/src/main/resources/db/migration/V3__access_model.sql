CREATE TABLE applications (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL UNIQUE CHECK (btrim(name) <> ''),
    audience varchar(500) NOT NULL UNIQUE CHECK (btrim(audience) <> ''),
    description varchar(2000),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE groups (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL UNIQUE CHECK (btrim(name) <> ''),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (application_id, name)
);

CREATE TABLE role_entitlements (
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    entitlement varchar(200) NOT NULL CHECK (entitlement ~ '^[a-z0-9_.:-]+$'),
    PRIMARY KEY (role_id, entitlement)
);

CREATE TABLE user_groups (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id uuid NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);
CREATE INDEX user_groups_group_idx ON user_groups(group_id);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX user_roles_role_idx ON user_roles(role_id);

CREATE TABLE group_roles (
    group_id uuid NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);
CREATE INDEX group_roles_role_idx ON group_roles(role_id);
