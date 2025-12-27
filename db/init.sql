CREATE TABLE IF NOT EXISTS metrics (
    id SERIAL PRIMARY KEY,
    agent_name VARCHAR(50),
    cpu DOUBLE PRECISION,
    ram BIGINT,
    disk BIGINT,
    disk_io VARCHAR(255),
    net_in BIGINT,
    net_out BIGINT,
    cpu_severity VARCHAR(20),
    ram_severity VARCHAR(20),
    disk_severity VARCHAR(20),
    disk_io_severity VARCHAR(20),
    net_severity VARCHAR(20),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS actions (
    id SERIAL PRIMARY KEY,
    node VARCHAR(50),
    metric VARCHAR(20),
    action VARCHAR(100),
    pid VARCHAR(20),
    process VARCHAR(200),
    reason TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_reports (
    id SERIAL PRIMARY KEY,
    node VARCHAR(50),
    cpu DOUBLE PRECISION,
    mem BIGINT,
    processes TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);









