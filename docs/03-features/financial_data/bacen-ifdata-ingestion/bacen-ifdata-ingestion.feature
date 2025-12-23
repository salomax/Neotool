@financial_data @bacen @batch @ingestion
Feature: BACEN IFData Ingestion
  As a data engineer
  I want to ingest regulatory financial data from BACEN IFData APIs
  So that downstream systems can access and process official financial data

  Background:
    Given the BACEN IFData ingestion job is configured
    And the database schema "financial_data" exists
    And Kafka topic "financial-data.institution.upserted" is available

  @happy-path @daily-execution
  Scenario: Daily scheduled execution processes next available quarter
    Given the last extracted quarter is "202403"
    And today's date is within the publication window for quarter "202406"
    And registry data exists for quarter "202406"
    When the daily ingestion job runs
    Then the target quarter should be determined as "202406"
    And the report catalog should be fetched from BACEN API
    And extract tasks should be created for all reports and institution types
    And paginated extraction should execute for all tasks
    And raw data should be staged in "ifdata_raw" table
    And data should be promoted to "bank_report" table
    And "ifdata_load_control" should be marked as completed

  @happy-path @registry-ingestion
  Scenario: Registry ingestion with change detection and Kafka publishing
    Given registry data does not exist for quarter "202403"
    When the registry ingestion task runs for quarter "202403"
    Then "IfDataCadastro" should be fetched from BACEN API with CSV format
    And institutions should be upserted into "financial_data.institution" table
    And source hash should be computed for each institution
    And new institutions should trigger Kafka events with change_type "CREATED"
    And changed institutions should trigger Kafka events with change_type "UPDATED"
    And unchanged institutions should not trigger Kafka events

  @happy-path @paginated-extraction
  Scenario: Paginated extraction handles multiple pages correctly
    Given an extract task exists with status "PENDING"
    And the task has "page_skip" = 0
    When the extraction task executes
    Then the task status should be set to "RUNNING"
    And BACEN API should be called with pagination parameters
    And if rows are returned, the task should be marked "SUCCESS"
    And a new task should be created with "page_skip" = 1000
    And raw data should be inserted into "ifdata_raw" table
    When the next page task executes and returns zero rows
    Then the task should be marked "EMPTY"
    And no further tasks should be created for that scope

  @happy-path @promotion
  Scenario: Raw data promotion filters active institutions only
    Given raw data exists in "ifdata_raw" for multiple institutions
    And some institutions have "situacao" = 'A' in the registry
    And some institutions have "situacao" != 'A' in the registry
    When the promotion task runs for a quarter and report
    Then only institutions with "situacao" = 'A' should be included
    And data should be transformed into JSONB format
    And data should be UPSERTed into "bank_report" table
    And "ifdata_load_control" should be marked as promoted

  @validation @publication-window
  Scenario: Publication window validation prevents premature processing
    Given the last extracted quarter is "202403"
    And today's date is before the publication window for quarter "202406"
    When the daily ingestion job runs
    Then the job should wait and not advance to the next quarter
    And no extraction tasks should be created

  @validation @publication-window
  Scenario: Publication window allows processing after deadline
    Given the last extracted quarter is "202312"
    And today's date is after the 90-day publication window for December quarter
    And data is still not available for quarter "202403"
    When the daily ingestion job runs
    Then the job should advance to the next candidate quarter "202403"
    And the job should continue waiting for data availability

  @validation @registry-requirement
  Scenario: Job skips when registry is missing after refresh attempt
    Given registry data does not exist for quarter "202403"
    When the registry ingestion task runs for quarter "202403"
    And the BACEN API returns no registry data
    Then the job should skip processing for that day
    And no extraction tasks should be created
    And an appropriate log message should be recorded

  @edge-case @reload-scope
  Scenario: Scope-based raw purge on reload preserves pagination safety
    Given raw data exists for scope "(202403, Report1, 1, 1)"
    And an extract task exists with "page_skip" = 0 for the same scope
    And the scope is being reloaded
    When the extraction task executes
    Then raw rows should be purged for the scope before inserting new data
    And only rows for the specific scope should be purged
    And subsequent pages should not purge data again

  @edge-case @reload-scope
  Scenario: Raw purge does not occur for non-zero page_skip
    Given an extract task exists with "page_skip" = 1000
    When the extraction task executes
    Then no raw data should be purged
    And new data should be appended to existing raw data

  @edge-case @idempotency
  Scenario: Task retry maintains idempotency
    Given an extract task exists with status "ERROR"
    And "attempt_count" = 2
    When the task is retried
    Then the task should be marked "RUNNING"
    And "attempt_count" should be incremented
    And if the retry succeeds, the task should be marked "SUCCESS"
    And no duplicate data should be inserted into "ifdata_raw"

  @edge-case @empty-response
  Scenario: Empty response correctly terminates pagination
    Given an extract task exists with status "PENDING"
    When the extraction task executes
    And BACEN API returns zero rows
    Then the task should be marked "EMPTY"
    And "EMPTY" should be treated as a terminal state (not an error)
    And no further tasks should be created for that pagination sequence

  @edge-case @historical-backfill
  Scenario: Historical backfill starts from 201603
    Given no previous extraction has occurred
    When a manual backfill is triggered
    Then the starting quarter should be "201603"
    And processing should proceed quarter by quarter
    And each quarter should be processed completely before advancing

  @non-functional @retry-logic
  Scenario: Task retry uses exponential backoff
    Given an extract task fails with a transient error
    When the task is retried
    Then retry attempts should use exponential backoff
    And "attempt_count" should be tracked
    And "last_error" and "last_http_status" should be recorded
    And after maximum retries, the task should remain in "ERROR" state

  @non-functional @observability
  Scenario: Job emits structured logs and metrics
    When the ingestion job runs
    Then structured logs should include "batch_id", "task_name", "records_processed"
    And Prefect artifacts should contain aggregate statistics
    And metrics should be emitted for:
      - Total records extracted
      - Total records promoted
      - Kafka events published
      - Task execution duration
      - Error counts

  @non-functional @performance
  Scenario: Job handles large datasets efficiently
    Given a quarter contains thousands of institutions and reports
    When the ingestion job runs
    Then pagination should prevent memory exhaustion
    And batch inserts should be used for raw data
    And promotion should process data in batches
    And the job should complete within acceptable time limits

  @manual-trigger @reprocessing
  Scenario: Manual trigger forces specific quarter processing
    Given a manual trigger is requested for quarter "202303"
    When the manual ingestion job runs with "ano_mes" = "202303"
    Then the target quarter should be set to "202303"
    And registry should be refreshed for that quarter
    And extraction should proceed regardless of publication window
    And all steps should execute as in daily execution

  @manual-trigger @reprocessing
  Scenario: Reprocessing transforms existing raw data
    Given raw data exists in "ifdata_raw" for quarter "202303"
    When a reprocess command is executed for quarter "202303"
    Then existing raw data should be used (no re-extraction)
    And transform and load should execute
    And "bank_report" should be updated via UPSERT
    And "ifdata_load_control" should reflect the reprocessing

  @manual-trigger @reprocessing
  Scenario: Re-extraction purges scope before reloading
    Given raw data exists for scope "(202303, Report1, 1, 1)"
    When a re-extraction is requested for that scope
    Then raw data for the scope should be purged
    And new extraction tasks should be created
    And fresh data should be fetched from BACEN API

  @error-handling @api-failure
  Scenario: API failure is handled gracefully
    Given an extract task exists with status "PENDING"
    When BACEN API returns a 5xx error
    Then the task should be marked "ERROR"
    And "last_http_status" should be recorded
    And "last_error" should contain error details
    And the task should be eligible for retry
    And fallback handling should be triggered after max retries

  @error-handling @timeout
  Scenario: Request timeout is handled with retry
    Given an extract task exists with status "PENDING"
    When the BACEN API request times out
    Then the task should be marked "ERROR"
    And timeout should be recorded in "last_error"
    And the task should be eligible for retry with backoff
    And retry should use exponential backoff

  @error-handling @parse-error
  Scenario: CSV parse error is handled gracefully
    Given an extract task exists with status "PENDING"
    When BACEN API returns malformed CSV data
    Then the task should be marked "ERROR"
    And parse error details should be recorded
    And raw data should not be inserted
    And the error should be logged for investigation

  @kafka @institution-events
  Scenario: Kafka event published for new institution
    Given an institution with "bacen_cod_inst" = "0001" does not exist
    When registry ingestion processes the institution
    Then a Kafka event should be published to "financial-data.institution.upserted"
    And the message key should be "0001"
    And the payload should include "change_type" = "CREATED"
    And the payload should include all required institution fields
    And "schema_version", "event_id", "occurred_at", and "source" should be present

  @kafka @institution-events
  Scenario: Kafka event published for changed institution
    Given an institution with "bacen_cod_inst" = "0001" exists
    And the institution's "source_hash" differs from new data
    When registry ingestion processes the updated institution
    Then a Kafka event should be published to "financial-data.institution.upserted"
    And the message key should be "0001"
    And the payload should include "change_type" = "UPDATED"
    And the payload should include updated institution fields

  @kafka @institution-events
  Scenario: No Kafka event for unchanged institution
    Given an institution with "bacen_cod_inst" = "0001" exists
    And the institution's "source_hash" matches new data
    When registry ingestion processes the institution
    Then no Kafka event should be published
    And the institution record should not be updated

  @kafka @dlq
  Scenario: Failed Kafka messages go to DLQ
    Given a Kafka event is being published
    When Kafka publish fails after all retries
    Then the message should be sent to DLQ topic "financial-data.institution.upserted.dlq"
    And DLQ message should include original message and error metadata
    And an alert should be triggered
