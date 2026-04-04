ALTER TABLE notes
    ADD COLUMN user_id uuid;

UPDATE notes
SET user_id = notebooks.user_id
FROM notebooks
WHERE notes.notebook_id = notebooks.id;

ALTER TABLE notes
    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE notes
    ALTER COLUMN notebook_id DROP NOT NULL;

ALTER TABLE notes
    ADD CONSTRAINT fk_notes_users FOREIGN KEY (user_id) REFERENCES users (id);
