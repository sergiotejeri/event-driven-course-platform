create index outbox_claim_idx
    on outbox_events(available_at, occurred_at)
    where published_at is null;

create index processed_events_time_idx
    on processed_events(processed_at);
