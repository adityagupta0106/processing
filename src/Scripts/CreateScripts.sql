-- DROP TABLE IF EXISTS schm_sp.application_details;

CREATE TABLE IF NOT EXISTS schm_sp.application_details
(
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    draft_reference_no character varying COLLATE pg_catalog."default" NOT NULL,
    reference_no character varying COLLATE pg_catalog."default",
    service_id integer NOT NULL,
    apply_date timestamp with time zone,
    beneficiary_name character varying COLLATE pg_catalog."default",
    due_date timestamp with time zone,
    status character varying COLLATE pg_catalog."default" NOT NULL,
    applied_location_id integer,
    applied_location_name character varying COLLATE pg_catalog."default",
    grievance_application_id character varying COLLATE pg_catalog."default",
    beneficiary_id bigint,
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT application_details_pkey PRIMARY KEY (application_id)
)


-- DROP TABLE IF EXISTS schm_sp.application_flow_status;

CREATE TABLE IF NOT EXISTS schm_sp.application_flow_status
(
    id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    activity_type character varying COLLATE pg_catalog."default" NOT NULL,
    txn_id character varying COLLATE pg_catalog."default" NOT NULL,
    completed integer,
    form_id character varying COLLATE pg_catalog."default" NOT NULL,
    data_id character varying COLLATE pg_catalog."default",
    service_id integer,
    task_id character varying COLLATE pg_catalog."default",
    last_update timestamp with time zone,
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT application_flow_status_pkey PRIMARY KEY (id),
    CONSTRAINT application_flow_status_ukey UNIQUE (application_id, task_id, activity_type, completed, txn_id)
)


-- DROP TABLE IF EXISTS schm_sp.current_process;

CREATE TABLE IF NOT EXISTS schm_sp.current_process
(
    process_id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    service_id integer NOT NULL,
    current_task character varying COLLATE pg_catalog."default" NOT NULL,
    current_task_name character varying COLLATE pg_catalog."default" NOT NULL,
    previous_task character varying COLLATE pg_catalog."default" NOT NULL,
    previous_task_name character varying COLLATE pg_catalog."default" NOT NULL,
    previous_process_id character varying COLLATE pg_catalog."default" NOT NULL,
    data_id character varying COLLATE pg_catalog."default",
    is_parallel character varying COLLATE pg_catalog."default",
    action_code integer,
    action_taken character varying COLLATE pg_catalog."default",
    action_on timestamp with time zone,
    initiated_on timestamp with time zone,
    base_service_id integer NOT NULL,
    user_id bigint,
    user_ip character varying COLLATE pg_catalog."default",
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT current_process_pkey PRIMARY KEY (process_id)
)


-- DROP TABLE IF EXISTS schm_sp.processing_transactions;

CREATE TABLE IF NOT EXISTS schm_sp.processing_transactions
(
    txn_id character varying COLLATE pg_catalog."default" NOT NULL,
    form_id character varying COLLATE pg_catalog."default" NOT NULL,
    service_id integer NOT NULL,
    task_id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default",
    user_id bigint,
    user_ip character varying COLLATE pg_catalog."default",
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    activity_type character varying COLLATE pg_catalog."default",
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT processing_transactions_pkey PRIMARY KEY (txn_id)
)


alter table schm_sp.application_flow_status add column service_id integer;
alter table schm_sp.application_flow_status add column task_id character varying;
alter table schm_sp.current_process add column base_service_id integer;
alter table schm_sp.application_details add column service_name character varying;
ALTER TABLE IF EXISTS schm_sp.current_process ADD COLUMN current_task_type integer;

CREATE TABLE IF NOT EXISTS schm_sp.timer_task_execution
(
    id bigserial,
    application_id character varying COLLATE pg_catalog."default",
    current_process_id character varying COLLATE pg_catalog."default",
    service_id integer,
    base_service_id integer,
    task_id character varying COLLATE pg_catalog."default",
    due_date timestamp with time zone,
    status character varying COLLATE pg_catalog."default",
    action_taken character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    executed_on timestamp with time zone,
    CONSTRAINT timer_task_execution_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS schm_sp.workflow_escalation
(
    id bigint NOT NULL DEFAULT nextval('schm_sp.workflow_escalation_id_seq'::regclass),
    application_id character varying COLLATE pg_catalog."default",
    service_id integer,
    current_process_id character varying COLLATE pg_catalog."default",
    task_id character varying COLLATE pg_catalog."default",
    execute_on timestamp with time zone,
    status character varying COLLATE pg_catalog."default",
    retry_count integer,
    action character varying COLLATE pg_catalog."default",
    mvel_expression character varying COLLATE pg_catalog."default",
    escalation_json character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    modified_on timestamp with time zone,
    CONSTRAINT workflow_escalation_pkey PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS schm_sp.workflow_webservice_execution
(
    execution_id bigserial,
    application_id character varying COLLATE pg_catalog."default",
    service_id integer,
    base_service_id integer,
    current_process_id character varying COLLATE pg_catalog."default",
    current_task_id character varying COLLATE pg_catalog."default",
    api_id character varying COLLATE pg_catalog."default",
    status character varying COLLATE pg_catalog."default",
    attempt_count integer,
    max_attempt integer,
    retry_interval integer,
    retry_interval_unit character varying COLLATE pg_catalog."default",
    next_retry_time timestamp with time zone,
    api_response character varying COLLATE pg_catalog."default",
    normalized_response character varying COLLATE pg_catalog."default",
    validation_token character varying COLLATE pg_catalog."default",
    form_data_id character varying COLLATE pg_catalog."default",
    error_message character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    modified_on timestamp with time zone,
    CONSTRAINT workflow_webservice_execution_pkey PRIMARY KEY (execution_id)
);

-------------------NEXT-------------------

CREATE TABLE schm_sp.application_document_log
(
    id                  character varying PRIMARY KEY,
    application_id      character varying NOT NULL,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    service_id          integer NOT NULL,
    task_id             character varying NOT NULL,
    reference_id        character varying NOT NULL,
    document_name       character varying,
    source_type         character varying,
    upload_id           character varying,
    status              character varying,
    created_by          bigint,
    created_on          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE schm_sp.application_document_merge
(
    id                      character varying PRIMARY KEY,
    application_id          character varying NOT NULL,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    task_id                 character varying NOT NULL,
    reference_id            character varying NOT NULL,
    merged_upload_id        character varying,
    merged_preview_url      character varying,
    signed_upload_id        character varying,
    status                  character varying,
    created_by              bigint,
    created_on              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              integer,
    updated_on              timestamp with time zone
);


CREATE TABLE schm_sp.application_document_submission
(
    id                  character varying PRIMARY KEY,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    application_id      character varying NOT NULL,
    service_id          integer NOT NULL,
    task_id             character varying NOT NULL,
    reference_id        character varying NOT NULL,
    document_name       character varying,
    upload_id           character varying,
    signed_upload_id    character varying,
    merged             boolean DEFAULT false,
    status              character varying,
    created_by          bigint,
    created_on          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);


alter table schm_sp.application_document_log add column tenant_id character varying;
alter table schm_sp.application_document_merge add column tenant_id character varying;
alter table schm_sp.application_document_submission add column tenant_id character varying;

    
ALTER TABLE IF EXISTS schm_sp.timer_task_execution   ALTER COLUMN id type character varying ;
ALTER TABLE IF EXISTS schm_sp.workflow_escalation   ALTER COLUMN id type character varying ;
ALTER TABLE IF EXISTS schm_sp.workflow_webservice_execution   ALTER COLUMN execution_id type character varying ;

ALTER TABLE IF EXISTS schm_sp.application_details ADD COLUMN is_priority boolean  NOT NULL DEFAULT false;


----------------------------- AUA------------------

CREATE TABLE schm_sp.aua_transaction_log (
    id BIGSERIAL PRIMARY KEY,
    txn_id VARCHAR NOT NULL,
    row_no INTEGER,

    attribute_id VARCHAR ,
    service_id INTEGER,
    task_id VARCHAR,
    tenant_id VARCHAR ,
    operation_type VARCHAR ,
    status VARCHAR DEFAULT 'INITIATED',
    provider_id BIGINT,
    api_id BIGINT,
    provider_txn_id VARCHAR,
    error_code VARCHAR,
    error_message VARCHAR,

    completed_at TIMESTAMP WITH TIME ZONE,
    cr_date TIMESTAMP WITH TIME ZONE ,
    up_date TIMESTAMP WITH TIME ZONE
);

ALTER TABLE schm_sp.current_process
ADD COLUMN IF NOT EXISTS action_remarks character varying;

ALTER TABLE IF EXISTS schm_sp.application_details
    ADD COLUMN action_tiny_url character varying;
    
ALTER TABLE IF EXISTS schm_sp.application_document_log
    ADD COLUMN document_tiny_url character varying;
    
ALTER TABLE IF EXISTS schm_sp.application_document_submission
    ADD COLUMN document_tiny_url character varying;


--------------------05/10/26----------------


CREATE TABLE schm_sp.application_attr_data (
    application_id VARCHAR PRIMARY KEY,
    base_service_id INTEGER,
    service_id INTEGER,
    attr_val VARCHAR,
    u_date TIMESTAMP,
    tenant_id VARCHAR
);


CREATE TABLE schm_sp.application_payment (
    payment_id                VARCHAR NOT NULL,
    gateway_transaction_id    VARCHAR,
    application_id            VARCHAR NOT NULL,
    service_id                INTEGER,
    process_id                VARCHAR,
    task_id                   VARCHAR,
    gateway_code              VARCHAR NOT NULL,
    environment               VARCHAR NOT NULL,
    revalidation_configuration VARCHAR,
    status                    VARCHAR,
    amount                    NUMERIC(19, 2),
    currency                  VARCHAR,
    cr_date                   TIMESTAMP WITH TIME ZONE,
    up_date                   TIMESTAMP WITH TIME ZONE,
    completed_date            TIMESTAMP WITH TIME ZONE,
    tenant_id                 VARCHAR NOT NULL,
    CONSTRAINT pk_application_payment PRIMARY KEY (payment_id)
);

CREATE TABLE IF NOT EXISTS schm_sp.edit_form_details
(
    id character varying NOT NULL,
    application_id character varying NOT NULL,
    service_id integer NOT NULL,
    holder_id character varying NOT NULL,
    form_id character varying NOT NULL,
    tenant_id character varying NOT NULL,

    allowed character varying,
    restricted character varying,
    hide character varying,
    specific text,

    cr_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_date TIMESTAMP WITH TIME ZONE,

    CONSTRAINT edit_form_details_pkey PRIMARY KEY (id),
    CONSTRAINT edit_form_details_ukey UNIQUE
        (application_id, service_id, tenant_id, holder_id, form_id)
);

-- =========================================================================
-- Pattern Generation Module Tables
-- =========================================================================

CREATE TABLE IF NOT EXISTS schm_sp.pattern_sequence
(
    id BIGSERIAL PRIMARY KEY,
    pattern_id VARCHAR NOT NULL,
    version INTEGER NOT NULL,
    tenant_id VARCHAR NOT NULL,
    service_id INTEGER NOT NULL,
    scope_key VARCHAR NOT NULL,
    current_numeric_value BIGINT NOT NULL DEFAULT 0,
    current_alpha_value BIGINT NOT NULL DEFAULT 0,
    current_formatted_sequence VARCHAR,
    total_allocated BIGINT NOT NULL DEFAULT 0,
    total_successful BIGINT NOT NULL DEFAULT 0,
    total_failed BIGINT NOT NULL DEFAULT 0,
    is_exhausted BOOLEAN NOT NULL DEFAULT FALSE,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_allocated_on TIMESTAMP WITH TIME ZONE,
    last_reset_on TIMESTAMP WITH TIME ZONE,
    reset_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_pattern_sequence_scope UNIQUE (pattern_id, tenant_id, scope_key)
);

CREATE TABLE IF NOT EXISTS schm_sp.pattern_generation_log
(
    id VARCHAR PRIMARY KEY,
    pattern_id VARCHAR NOT NULL,
    pattern_version INTEGER NOT NULL,
    generated_value VARCHAR NOT NULL,
    sequence_representation VARCHAR NOT NULL,
    scope_key VARCHAR NOT NULL,
    tenant_id VARCHAR NOT NULL,
    service_id INTEGER,
    application_id VARCHAR,
    document_id VARCHAR,
    generation_purpose VARCHAR,
    idempotency_key VARCHAR,
    status VARCHAR NOT NULL DEFAULT 'ACTIVE',
    superseded_by VARCHAR,
    generated_by VARCHAR,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pattern_generation_value UNIQUE (tenant_id, generated_value)
);

CREATE INDEX IF NOT EXISTS idx_pattern_gen_log_idempotency ON schm_sp.pattern_generation_log (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_pattern_gen_log_appl ON schm_sp.pattern_generation_log (application_id, tenant_id);

CREATE TABLE IF NOT EXISTS schm_sp.ack_generation_job
(
    id character varying COLLATE pg_catalog."default" NOT NULL,
    txn_id character varying COLLATE pg_catalog."default",
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    service_id integer NOT NULL,
    task_id character varying COLLATE pg_catalog."default" NOT NULL,
    reference_id character varying COLLATE pg_catalog."default",
    document_name character varying COLLATE pg_catalog."default",
    output_format_id bigint,
    category character varying COLLATE pg_catalog."default",
    status character varying COLLATE pg_catalog."default" NOT NULL DEFAULT 'PENDING'::character varying,
    upload_id character varying COLLATE pg_catalog."default",
    retry_count integer NOT NULL DEFAULT 0,
    error_message text COLLATE pg_catalog."default",
    tenant_id character varying COLLATE pg_catalog."default",
    cr_by bigint,
    cr_on timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_on timestamp with time zone,
    CONSTRAINT ack_generation_job_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_ack_job_app_status ON schm_sp.ack_generation_job (application_id, status);
CREATE INDEX IF NOT EXISTS idx_ack_job_txn ON schm_sp.ack_generation_job (txn_id);


