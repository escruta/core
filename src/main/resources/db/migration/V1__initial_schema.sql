CREATE TABLE conversations
(
    created_at  datetime(6),
    updated_at  datetime(6),
    notebook_id binary(16)   NOT NULL,
    id          varchar(255) NOT NULL PRIMARY KEY,
    title       varchar(255) NOT NULL
);

CREATE TABLE users
(
    created_at datetime(6),
    updated_at datetime(6),
    id         binary(16)   NOT NULL PRIMARY KEY,
    email      varchar(100) NOT NULL UNIQUE,
    name       varchar(255) NOT NULL,
    password   varchar(255) NOT NULL
);

CREATE TABLE folders
(
    created_at datetime(6),
    updated_at datetime(6),
    id         binary(16)   NOT NULL PRIMARY KEY,
    user_id    binary(16)   NOT NULL,
    title      varchar(255) NOT NULL,
    color      varchar(50),
    CONSTRAINT fk_folders_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE notebooks
(
    created_at datetime(6),
    updated_at datetime(6),
    id         binary(16)   NOT NULL PRIMARY KEY,
    user_id    binary(16)   NOT NULL,
    folder_id  binary(16)   NULL,
    icon       varchar(255),
    summary    text,
    title      varchar(255) NOT NULL,
    CONSTRAINT fkk5jweiuqjycrab2dgljlc919i FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notebooks_folders FOREIGN KEY (folder_id) REFERENCES folders (id) ON DELETE SET NULL
);

CREATE TABLE generation_jobs
(
    completed_at  timestamp(6) NULL,
    created_at    timestamp(6) NULL,
    updated_at    timestamp(6) NULL,
    id            binary(16)   NOT NULL PRIMARY KEY,
    notebook_id   binary(16)   NOT NULL,
    user_id       binary(16)   NOT NULL,
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
    id                 binary(16)   NOT NULL PRIMARY KEY,
    notebook_id        binary(16)   NOT NULL,
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
    id          binary(16)   NOT NULL PRIMARY KEY,
    notebook_id binary(16)   NOT NULL,
    content     longtext,
    title       varchar(255) NOT NULL,
    CONSTRAINT fk814tu72hqd3m67ramoipdr0qq FOREIGN KEY (notebook_id) REFERENCES notebooks (id)
);

CREATE TABLE messages
(
    id                  bigint       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    conversation_id     varchar(255) NOT NULL,
    role                varchar(10)  NOT NULL,
    content             text         NOT NULL,
    selected_source_ids text,
    cited_sources       text,
    created_at          datetime(6)  NOT NULL,
    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT fk_messages_conversations FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

CREATE INDEX messages_conversation_id_idx
    ON messages (conversation_id, id);


ALTER TABLE conversations
    ADD CONSTRAINT fka2jxphva2eexgctoqxgpl4krc FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
