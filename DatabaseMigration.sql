-- Initial database schema for workflow orchestrator

-- Workflow Definitions
CREATE TABLE workflow_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    strategy_type VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
    UNIQUE (name, version)
);

-- Task Definitions
CREATE TABLE task_definitions (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(100) NOT NULL,
    execution_order INT,
    retry_limit INT DEFAULT 3,
    timeout_seconds INT DEFAULT 60,
    execution_mode VARCHAR(50) NOT NULL DEFAULT 'API',
    require_user_review BOOLEAN DEFAULT FALSE,
    conditional_expression TEXT,
    next_task_on_success BIGINT,
    next_task_on_failure BIGINT
);

-- Task Definition Configuration
CREATE TABLE task_definition_config (
    id BIGSERIAL PRIMARY KEY,
    task_definition_id BIGINT NOT NULL REFERENCES task_definitions(id) ON DELETE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    UNIQUE (task_definition_id, config_key)
);

-- Workflow Executions
CREATE TABLE workflow_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    correlation_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    current_task_index INT,
    retry_count INT DEFAULT 0,
    error_message TEXT
);

-- Workflow Execution Variables
CREATE TABLE workflow_execution_variables (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    variable_key VARCHAR(255) NOT NULL,
    variable_value TEXT,
    UNIQUE (workflow_execution_id, variable_key)
);

-- Task Executions
CREATE TABLE task_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    task_definition_id BIGINT NOT NULL REFERENCES task_definitions(id),
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_mode VARCHAR(50) NOT NULL,
    retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP,
    error_message TEXT
);

-- Task Execution Inputs
CREATE TABLE task_execution_inputs (
    id BIGSERIAL PRIMARY KEY,
    task_execution_id BIGINT NOT NULL REFERENCES task_executions(id) ON DELETE CASCADE,
    input_key VARCHAR(255) NOT NULL,
    input_value TEXT,
    UNIQUE (task_execution_id, input_key)
);

-- Task Execution Outputs
CREATE TABLE task_execution_outputs (
    id BIGSERIAL PRIMARY KEY,
    task_execution_id BIGINT NOT NULL REFERENCES task_executions(id) ON DELETE CASCADE,
    output_key VARCHAR(255) NOT NULL,
    output_value TEXT,
    UNIQUE (task_execution_id, output_key)
);

-- User Review Points
CREATE TABLE user_review_points (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    task_execution_id BIGINT NOT NULL REFERENCES task_executions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewer VARCHAR(255),
    comment TEXT,
    decision VARCHAR(50)
);

-- Indexes for performance
CREATE INDEX idx_workflow_def_name ON workflow_definitions(name);
CREATE INDEX idx_workflow_exec_status ON workflow_executions(status);
CREATE INDEX idx_workflow_exec_correlation ON workflow_executions(correlation_id);
CREATE INDEX idx_task_exec_status ON task_executions(status);
CREATE INDEX idx_task_exec_next_retry ON task_executions(next_retry_at) WHERE status = 'AWAITING_RETRY';
