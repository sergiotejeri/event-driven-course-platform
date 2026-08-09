create table users (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint users_email_normalized_check check (email = lower(trim(email))),
    constraint users_email_unique unique (email)
);

create table roles (
    name varchar(32) primary key,
    constraint roles_name_check check (name in ('ADMIN', 'INSTRUCTOR', 'STUDENT'))
);

insert into roles(name) values ('ADMIN'), ('INSTRUCTOR'), ('STUDENT');

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_name varchar(32) not null references roles(name),
    primary key (user_id, role_name)
);

create table categories (
    id uuid primary key,
    name varchar(160) not null,
    description varchar(2000) not null default '',
    status varchar(16) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint categories_name_unique unique (name),
    constraint categories_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create table instructors (
    id uuid primary key,
    user_id uuid not null references users(id),
    name varchar(200) not null,
    email varchar(320) not null,
    biography varchar(5000) not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint instructors_user_unique unique (user_id),
    constraint instructors_email_unique unique (email),
    constraint instructors_email_normalized_check check (email = lower(trim(email)))
);

create table students (
    id uuid primary key,
    user_id uuid not null references users(id),
    first_name varchar(120) not null,
    last_name varchar(160) not null,
    email varchar(320) not null,
    registered_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint students_user_unique unique (user_id),
    constraint students_email_unique unique (email),
    constraint students_email_normalized_check check (email = lower(trim(email)))
);

create table courses (
    id uuid primary key,
    title varchar(240) not null,
    description text not null,
    estimated_hours integer not null,
    level varchar(24) not null,
    price numeric(12, 2) not null,
    currency char(3) not null,
    capacity integer not null,
    occupied_seats integer not null default 0,
    status varchar(16) not null,
    category_id uuid not null references categories(id),
    instructor_id uuid not null references instructors(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint courses_title_check check (length(trim(title)) > 0),
    constraint courses_estimated_hours_check check (estimated_hours >= 0),
    constraint courses_level_check check (level in ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    constraint courses_price_check check (price >= 0),
    constraint courses_currency_check check (currency = upper(currency)),
    constraint courses_capacity_check check (capacity > 0),
    constraint courses_occupancy_check check (occupied_seats >= 0 and occupied_seats <= capacity),
    constraint courses_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create table enrollments (
    id uuid primary key,
    student_id uuid not null references students(id),
    course_id uuid not null references courses(id),
    status varchar(24) not null,
    progress integer not null default 0,
    enrolled_at timestamptz not null default now(),
    completed_at timestamptz,
    cancelled_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint enrollments_status_check check (
        status in ('PENDING_PAYMENT', 'ACTIVE', 'COMPLETED', 'CANCELLED')
    ),
    constraint enrollments_progress_check check (progress between 0 and 100),
    constraint enrollments_completed_check check (
        (status = 'COMPLETED' and progress = 100 and completed_at is not null)
        or status <> 'COMPLETED'
    ),
    constraint enrollments_cancelled_check check (
        (status = 'CANCELLED' and cancelled_at is not null)
        or status <> 'CANCELLED'
    )
);

create unique index enrollments_student_course_active_unique
    on enrollments(student_id, course_id)
    where status in ('PENDING_PAYMENT', 'ACTIVE');

create table payments (
    id uuid primary key,
    enrollment_id uuid not null references enrollments(id),
    amount numeric(12, 2) not null,
    currency char(3) not null,
    status varchar(16) not null,
    idempotency_key varchar(128) not null,
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    failed_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint payments_enrollment_unique unique (enrollment_id),
    constraint payments_idempotency_key_unique unique (idempotency_key),
    constraint payments_amount_check check (amount >= 0),
    constraint payments_currency_check check (currency = upper(currency)),
    constraint payments_status_check check (status in ('PENDING', 'CONFIRMED', 'FAILED')),
    constraint payments_terminal_timestamp_check check (
        (status = 'PENDING' and confirmed_at is null and failed_at is null)
        or (status = 'CONFIRMED' and confirmed_at is not null and failed_at is null)
        or (status = 'FAILED' and failed_at is not null and confirmed_at is null)
    )
);

create table certificates (
    id uuid primary key,
    enrollment_id uuid not null references enrollments(id),
    verification_code varchar(128) not null,
    issued_at timestamptz not null default now(),
    constraint certificates_enrollment_unique unique (enrollment_id),
    constraint certificates_verification_code_unique unique (verification_code)
);

create table idempotency_records (
    id uuid primary key,
    actor_id uuid not null references users(id),
    operation varchar(96) not null,
    idempotency_key varchar(128) not null,
    request_hash char(64) not null,
    resource_type varchar(64),
    resource_id uuid,
    response_status integer,
    response_body jsonb,
    status varchar(16) not null default 'PROCESSING',
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint idempotency_actor_operation_key_unique
        unique (actor_id, operation, idempotency_key),
    constraint idempotency_status_check check (status in ('PROCESSING', 'COMPLETED', 'FAILED')),
    constraint idempotency_request_hash_check check (request_hash ~ '^[0-9a-f]{64}$')
);

create table outbox_events (
    event_id uuid primary key,
    event_type varchar(120) not null,
    event_version integer not null,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    correlation_id uuid not null,
    causation_id uuid,
    occurred_at timestamptz not null,
    available_at timestamptz not null default now(),
    published_at timestamptz,
    attempts integer not null default 0,
    last_error varchar(2000),
    constraint outbox_event_version_check check (event_version > 0),
    constraint outbox_attempts_check check (attempts >= 0)
);

create table processed_events (
    consumer_name varchar(160) not null,
    event_id uuid not null,
    processed_at timestamptz not null default now(),
    result varchar(32) not null default 'APPLIED',
    primary key (consumer_name, event_id),
    constraint processed_events_result_check check (result in ('APPLIED', 'IGNORED'))
);

create index courses_search_category_idx on courses(category_id, status);
create index courses_search_level_price_idx on courses(level, price);
create index courses_cursor_idx on courses(created_at desc, id desc);
create index courses_instructor_idx on courses(instructor_id, status);
create index enrollments_course_status_idx on enrollments(course_id, status);
create index enrollments_student_status_idx on enrollments(student_id, status);
create index payments_status_idx on payments(status, created_at);
create index outbox_unpublished_idx on outbox_events(available_at, occurred_at)
    where published_at is null;
