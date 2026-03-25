CREATE TABLE access_tokens
(
    expires_at timestamp(6) with time zone NOT NULL,
    email      character varying(255)      NOT NULL,
    token      character varying(255)      NOT NULL PRIMARY KEY
);

CREATE TABLE conversations
(
    created_at  timestamp(6) without time zone,
    updated_at  timestamp(6) without time zone,
    notebook_id uuid                   NOT NULL,
    id          character varying(255) NOT NULL PRIMARY KEY,
    title       character varying(255) NOT NULL
);

CREATE TABLE generation_jobs
(
    completed_at  timestamp(6) with time zone,
    created_at    timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    id            uuid                   NOT NULL PRIMARY KEY,
    notebook_id   uuid                   NOT NULL,
    user_id       uuid                   NOT NULL,
    error_message text,
    result        text,
    status        character varying(255) NOT NULL,
    type          character varying(255) NOT NULL,
    CONSTRAINT generation_jobs_status_check CHECK (((status)::text = ANY
                                                    ((ARRAY ['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT generation_jobs_type_check CHECK (((type)::text = ANY
                                                  ((ARRAY ['MIND_MAP'::character varying, 'STUDY_GUIDE'::character varying, 'FLASHCARDS'::character varying, 'QUESTIONNAIRE'::character varying])::text[])))
);

CREATE TABLE notebooks
(
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id         uuid                   NOT NULL PRIMARY KEY,
    user_id    uuid                   NOT NULL,
    icon       character varying(255),
    summary    text,
    title      character varying(255) NOT NULL
);

CREATE TABLE notes
(
    created_at  timestamp(6) without time zone,
    updated_at  timestamp(6) without time zone,
    id          uuid                   NOT NULL PRIMARY KEY,
    notebook_id uuid                   NOT NULL,
    source_id   uuid UNIQUE,
    content     text,
    icon        character varying(255),
    title       character varying(255) NOT NULL
);

CREATE TABLE sources
(
    is_converted_by_ai boolean                NOT NULL,
    created_at         timestamp(6) without time zone,
    updated_at         timestamp(6) without time zone,
    id                 uuid                   NOT NULL PRIMARY KEY,
    notebook_id        uuid                   NOT NULL,
    content            text                   NOT NULL,
    icon               character varying(255),
    link               character varying(255),
    status             character varying(255) NOT NULL,
    summary            text,
    title              character varying(255) NOT NULL,
    type               character varying(255),
    CONSTRAINT sources_status_check CHECK (((status)::text = ANY
                                            ((ARRAY ['PENDING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT sources_type_check CHECK (((type)::text = ANY
                                          ((ARRAY ['WEBSITE'::character varying, 'YOUTUBE_VIDEO'::character varying, 'FILE'::character varying, 'TEXT'::character varying])::text[])))
);

CREATE TABLE spring_ai_chat_memory
(
    conversation_id character varying(36)       NOT NULL,
    content         text                        NOT NULL,
    type            character varying(10)       NOT NULL,
    "timestamp"     timestamp without time zone NOT NULL,
    CONSTRAINT spring_ai_chat_memory_type_check CHECK (((type)::text = ANY
                                                        ((ARRAY ['USER'::character varying, 'ASSISTANT'::character varying, 'SYSTEM'::character varying, 'TOOL'::character varying])::text[])))
);

CREATE TABLE users
(
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id         uuid                   NOT NULL PRIMARY KEY,
    email      character varying(100) NOT NULL UNIQUE,
    name       character varying(255) NOT NULL,
    password   character varying(255) NOT NULL
);

CREATE INDEX spring_ai_chat_memory_conversation_id_timestamp_idx ON spring_ai_chat_memory USING btree (conversation_id, "timestamp");

ALTER TABLE sources
    ADD CONSTRAINT fk4w6o7fe9755mx90a81awu2klm FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
ALTER TABLE notes
    ADD CONSTRAINT fk814tu72hqd3m67ramoipdr0qq FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
ALTER TABLE conversations
    ADD CONSTRAINT fka2jxphva2eexgctoqxgpl4krc FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
ALTER TABLE generation_jobs
    ADD CONSTRAINT fkk52imtum2mlr8jincdyof51b4 FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE notebooks
    ADD CONSTRAINT fkk5jweiuqjycrab2dgljlc919i FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE notes
    ADD CONSTRAINT fkka3wrcqyt11gt9qyvbpkuah7 FOREIGN KEY (source_id) REFERENCES sources (id);
ALTER TABLE generation_jobs
    ADD CONSTRAINT fknbxv6clr25q42x7racegnuli0 FOREIGN KEY (notebook_id) REFERENCES notebooks (id);
