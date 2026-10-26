-- Apply once after V001. No sample users or clinical data.
CREATE TABLE desktop_sessions (
 token_hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, auth_version BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL, FOREIGN KEY(user_id) REFERENCES users(user_id), INDEX(expires_at)
);
CREATE TABLE document_content (
 document_id BIGINT PRIMARY KEY, content LONGBLOB NOT NULL,
 FOREIGN KEY(document_id) REFERENCES medical_documents(document_id)
);
