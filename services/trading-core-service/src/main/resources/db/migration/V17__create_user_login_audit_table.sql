create table if not exists trading_core.user_login_audit (
    id                  uuid        primary key default gen_random_uuid(),
    user_id             uuid        not null references trading_core.users(id) on delete cascade,
    logged_in_at        timestamptz not null default now(),
    ip_address          text,
    user_agent          text,
    refresh_token_hash  text
);

create index ix_user_login_audit_user_loggedin
    on trading_core.user_login_audit (user_id, logged_in_at desc);
