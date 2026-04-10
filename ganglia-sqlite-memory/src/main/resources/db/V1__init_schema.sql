-- Ganglia SQLite Memory Schema v1

-- Schema version tracking
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TEXT NOT NULL  -- ISO-8601
);

-- Core memory entries
CREATE TABLE IF NOT EXISTS memory_entries (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    summary TEXT,
    full_content TEXT,
    category TEXT NOT NULL,
    tags TEXT,                -- JSON array: [{"name":"k","value":"v"}]
    related_files TEXT,       -- JSON array: ["path1","path2"]
    created_at TEXT NOT NULL  -- ISO-8601
);

-- FTS5 full-text index on title + summary + full_content
CREATE VIRTUAL TABLE IF NOT EXISTS memory_entries_fts USING fts5(
    title, summary, full_content,
    content=memory_entries,
    content_rowid=rowid
);

-- Triggers to keep FTS index in sync
CREATE TRIGGER IF NOT EXISTS memory_entries_ai AFTER INSERT ON memory_entries BEGIN
    INSERT INTO memory_entries_fts(rowid, title, summary, full_content)
    VALUES (new.rowid, new.title, new.summary, new.full_content);
END;

CREATE TRIGGER IF NOT EXISTS memory_entries_ad AFTER DELETE ON memory_entries BEGIN
    INSERT INTO memory_entries_fts(memory_entries_fts, rowid, title, summary, full_content)
    VALUES ('delete', old.rowid, old.title, old.summary, old.full_content);
END;

CREATE TRIGGER IF NOT EXISTS memory_entries_au AFTER UPDATE ON memory_entries BEGIN
    INSERT INTO memory_entries_fts(memory_entries_fts, rowid, title, summary, full_content)
    VALUES ('delete', old.rowid, old.title, old.summary, old.full_content);
    INSERT INTO memory_entries_fts(rowid, title, summary, full_content)
    VALUES (new.rowid, new.title, new.summary, new.full_content);
END;

-- Session records
CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    goal TEXT,
    summary TEXT,
    turn_count INTEGER DEFAULT 0,
    tool_call_count INTEGER DEFAULT 0,
    start_time TEXT,
    end_time TEXT
);

-- FTS5 full-text index on session goal + summary
CREATE VIRTUAL TABLE IF NOT EXISTS sessions_fts USING fts5(
    goal, summary,
    content=sessions,
    content_rowid=rowid
);

-- Triggers to keep sessions FTS index in sync
CREATE TRIGGER IF NOT EXISTS sessions_ai AFTER INSERT ON sessions BEGIN
    INSERT INTO sessions_fts(rowid, goal, summary)
    VALUES (new.rowid, new.goal, new.summary);
END;

CREATE TRIGGER IF NOT EXISTS sessions_ad AFTER DELETE ON sessions BEGIN
    INSERT INTO sessions_fts(sessions_fts, rowid, goal, summary)
    VALUES ('delete', old.rowid, old.goal, old.summary);
END;

CREATE TRIGGER IF NOT EXISTS sessions_au AFTER UPDATE ON sessions BEGIN
    INSERT INTO sessions_fts(sessions_fts, rowid, goal, summary)
    VALUES ('delete', old.rowid, old.goal, old.summary);
    INSERT INTO sessions_fts(rowid, goal, summary)
    VALUES (new.rowid, new.goal, new.summary);
END;

-- Daily records
CREATE TABLE IF NOT EXISTS daily_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_date TEXT NOT NULL,
    session_id TEXT NOT NULL,
    goal TEXT,
    accomplishments TEXT,
    created_at TEXT NOT NULL
);

-- Long-term memory (topic-based key-value)
CREATE TABLE IF NOT EXISTS long_term_memory (
    topic TEXT PRIMARY KEY,
    content TEXT NOT NULL DEFAULT ''
);

-- Timeline events
CREATE TABLE IF NOT EXISTS timeline_events (
    event_id TEXT PRIMARY KEY,
    description TEXT,
    category TEXT,
    event_time TEXT NOT NULL,
    affected_files TEXT
);

CREATE INDEX IF NOT EXISTS idx_daily_records_date ON daily_records(record_date);
CREATE INDEX IF NOT EXISTS idx_timeline_events_time ON timeline_events(event_time DESC);
