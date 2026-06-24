CREATE TABLE conversations
(
    created_at  datetime(6),
    updated_at  datetime(6),
    notebook_id uuid         NOT NULL,
    id          varchar(255) NOT NULL PRIMARY KEY,
    title       varchar(255) NOT NULL
);

CREATE TABLE users
(
    created_at datetime(6),
    updated_at datetime(6),
    id         uuid         NOT NULL PRIMARY KEY,
    email      varchar(100) NOT NULL UNIQUE,
    name       varchar(255) NOT NULL,
    password   varchar(255) NOT NULL
);

CREATE TABLE folders
(
    created_at datetime(6),
    updated_at datetime(6),
    id         uuid         NOT NULL PRIMARY KEY,
    user_id    uuid         NOT NULL,
    title      varchar(255) NOT NULL,
    color      varchar(50),
    CONSTRAINT fk_folders_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE notebooks
(
    created_at datetime(6),
    updated_at datetime(6),
    id         uuid         NOT NULL PRIMARY KEY,
    user_id    uuid         NOT NULL,
    icon       varchar(255),
    summary    text,
    title      varchar(255) NOT NULL,
    CONSTRAINT fkk5jweiuqjycrab2dgljlc919i FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE generation_jobs
(
    completed_at  timestamp(6) NULL,
    created_at    timestamp(6) NULL,
    updated_at    timestamp(6) NULL,
    id            uuid         NOT NULL PRIMARY KEY,
    notebook_id   uuid         NOT NULL,
    user_id       uuid         NOT NULL,
    error_message text,
    result        longtext,
    status        varchar(255) NOT NULL,
    type          varchar(255) NOT NULL,
    CONSTRAINT generation_jobs_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT generation_jobs_type_check CHECK (type IN ('MIND_MAP', 'STUDY_GUIDE', 'FLASHCARDS', 'QUESTIONNAIRE')),
    CONSTRAINT fkk52imtum2mlr8jincdyof51b4 FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fknbxv6clr25q42x7racegnuli0 FOREIGN KEY (notebook_id) REFERENCES notebooks (id)
);

CREATE TABLE sources
(
    is_converted_by_ai boolean      NOT NULL,
    created_at         datetime(6),
    updated_at         datetime(6),
    id                 uuid         NOT NULL PRIMARY KEY,
    notebook_id        uuid         NOT NULL,
    content            longtext     NOT NULL,
    icon               varchar(255),
    link               varchar(255),
    status             varchar(255) NOT NULL,
    summary            text,
    title              varchar(255) NOT NULL,
    type               varchar(255),
    CONSTRAINT sources_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT sources_type_check CHECK (type IN ('WEBSITE', 'YOUTUBE_VIDEO', 'FILE', 'TEXT')),
    CONSTRAINT fk4w6o7fe9755mx90a81awu2klm FOREIGN KEY (notebook_id) REFERENCES notebooks (id)
);

CREATE TABLE notes
(
    created_at  datetime(6),
    updated_at  datetime(6),
    id          uuid         NOT NULL PRIMARY KEY,
    notebook_id uuid         NULL,
    folder_id   uuid         NULL,
    user_id     uuid         NOT NULL,
    source_id   uuid UNIQUE,
    content     longtext,
    title       varchar(255) NOT NULL,
    CONSTRAINT fk_notes_folders FOREIGN KEY (folder_id) REFERENCES folders (id),
    CONSTRAINT fk814tu72hqd3m67ramoipdr0qq FOREIGN KEY (notebook_id) REFERENCES notebooks (id),
    CONSTRAINT fkka3wrcqyt11gt9qyvbpkuah7 FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT fk_notes_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE SPRING_AI_CHAT_MEMORY
(
    conversation_id varchar(36) NOT NULL,
    content         text        NOT NULL,
    type            varchar(10) NOT NULL,
    `timestamp`     timestamp   NOT NULL,
    sequence_id     BIGINT      NOT NULL,
    CONSTRAINT SPRING_AI_CHAT_MEMORY_TYPE_CHECK CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, `timestamp`);

CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, sequence_id);


ALTER TABLE conversations
    ADD CONSTRAINT fka2jxphva2eexgctoqxgpl4krc FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
