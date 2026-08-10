alter table payments drop constraint payments_idempotency_key_unique;

create index payments_idempotency_key_idx on payments(idempotency_key);
