CREATE TABLE IF NOT EXISTS workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    absolute_path VARCHAR(500) NOT NULL,
    description TEXT,
    create_at TIMESTAMP NOT NULL,
    update_at TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL, -- -1 表示 BasicAgent 執行
    command TEXT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
);

CREATE TABLE IF NOT EXISTS result_text (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    history_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, -- 'STDOUT' or 'STDERR'
    create_at TIMESTAMP NOT NULL,
    content CLOB,
    FOREIGN KEY (history_id) REFERENCES history(id)
);
CREATE INDEX IF NOT EXISTS idx_result_text_history_id_type_create_at ON result_text(history_id, type, create_at ASC);

CREATE TABLE IF NOT EXISTS schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    command TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    start_time TIMESTAMP,
    cron VARCHAR(100),
    enabled BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE TABLE IF NOT EXISTS memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_key VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_category ON memory(category);
CREATE INDEX IF NOT EXISTS idx_memory_key ON memory(memory_key);

CREATE TABLE IF NOT EXISTS agent_command_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    command TEXT NOT NULL,
    history_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    is_read BOOLEAN DEFAULT FALSE,
    create_at TIMESTAMP NOT NULL,
    update_at TIMESTAMP NOT NULL,
    FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    FOREIGN KEY (history_id) REFERENCES history(id)
);

CREATE TABLE IF NOT EXISTS agent_chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(20) NOT NULL, 
    message TEXT NOT NULL,
    source VARCHAR(20) NOT NULL, 
    create_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_todo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(500) NOT NULL,
    scheduled_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);
