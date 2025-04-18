-- Initial database schema for Workflow Orchestrator
-- Migration: V1__Initial_Schema.sql

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
    execution_order INT NOT NULL,
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

-- Workflow Version History
CREATE TABLE workflow_version_history (
    id BIGSERIAL PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    change_description TEXT,
    workflow_definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    UNIQUE (workflow_name, version)
);

-- Workflow Execution Audit Log
CREATE TABLE workflow_execution_audit (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    user_id VARCHAR(255),
    details TEXT
);

-- Create indexes for performance
CREATE INDEX idx_workflow_def_name ON workflow_definitions(name);
CREATE INDEX idx_workflow_def_name_version ON workflow_definitions(name, version);
CREATE INDEX idx_task_def_workflow ON task_definitions(workflow_definition_id);
CREATE INDEX idx_task_def_order ON task_definitions(workflow_definition_id, execution_order);
CREATE INDEX idx_workflow_exec_status ON workflow_executions(status);
CREATE INDEX idx_workflow_exec_correlation ON workflow_executions(correlation_id);
CREATE INDEX idx_workflow_exec_def ON workflow_executions(workflow_definition_id);
CREATE INDEX idx_task_exec_workflow ON task_executions(workflow_execution_id);
CREATE INDEX idx_task_exec_status ON task_executions(status);
CREATE INDEX idx_task_exec_retry ON task_executions(next_retry_at) WHERE status = 'AWAITING_RETRY';
CREATE INDEX idx_review_points_workflow ON user_review_points(workflow_execution_id);
CREATE INDEX idx_review_points_task ON user_review_points(task_execution_id);
CREATE INDEX idx_workflow_version_history_name ON workflow_version_history(workflow_name);
CREATE INDEX idx_workflow_audit_execution ON workflow_execution_audit(workflow_execution_id);

-- Create a function for automatically updating the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = NOW();
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create a trigger to automatically update the updated_at timestamp on workflow definitions
CREATE TRIGGER update_workflow_def_updated_at
BEFORE UPDATE ON workflow_definitions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Create a view for active workflows
CREATE VIEW active_workflows AS
SELECT 
    we.id, 
    wd.name as workflow_name,
    wd.version,
    we.correlation_id,
    we.status,
    we.started_at,
    now() - we.started_at as duration,
    we.current_task_index,
    (SELECT count(*) FROM task_executions te WHERE te.workflow_execution_id = we.id) as total_tasks,
    (SELECT count(*) FROM task_executions te WHERE te.workflow_execution_id = we.id AND te.status = 'COMPLETED') as completed_tasks
FROM 
    workflow_executions we
JOIN 
    workflow_definitions wd ON we.workflow_definition_id = wd.id
WHERE 
    we.status IN ('CREATED', 'RUNNING', 'PAUSED', 'AWAITING_USER_REVIEW');

-- Create a view for pending user reviews
CREATE VIEW pending_user_reviews AS
SELECT 
    urp.id as review_id,
    we.id as workflow_execution_id,
    wd.name as workflow_name,
    wd.version as workflow_version,
    we.correlation_id,
    te.id as task_execution_id,
    td.name as task_name,
    td.type as task_type,
    urp.created_at as review_requested_at,
    now() - urp.created_at as pending_duration
FROM 
    user_review_points urp
JOIN 
    workflow_executions we ON urp.workflow_execution_id = we.id
JOIN 
    workflow_definitions wd ON we.workflow_definition_id = wd.id
JOIN 
    task_executions te ON urp.task_execution_id = te.id
JOIN 
    task_definitions td ON te.task_definition_id = td.id
WHERE 
    urp.reviewed_at IS NULL
ORDER BY 
    urp.created_at ASC;
