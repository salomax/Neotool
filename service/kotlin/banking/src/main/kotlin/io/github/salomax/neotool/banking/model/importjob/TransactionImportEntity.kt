package io.github.salomax.neotool.banking.model.importjob

import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Transaction-import projection of banking.document_import_jobs.
 * JSONB columns [errorDetails] and [reviewPayload] are stored as String and serialized with Jackson.
 */
@Entity
@Table(name = "document_import_jobs", schema = "banking")
open class TransactionImportEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "job_kind", nullable = false, length = 32)
    open var jobKind: String = JOB_KIND,
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "created_by_user_id", nullable = false)
    open var createdByUserId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var source: ImportSource,
    @Column(name = "document_type", length = 64)
    open var documentType: String? = null,
    @Column(name = "file_name", length = 255)
    open var fileName: String? = null,
    @Column(name = "file_hash", nullable = false, length = 64)
    open var fileHash: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: ImportJobStatus,
    @Column(name = "extracted_text")
    open var extractedText: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_metadata", columnDefinition = "jsonb")
    open var extractionMetadata: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assistant_response", columnDefinition = "jsonb")
    open var assistantResponse: String? = null,
    @Column(name = "total_rows")
    open var totalRows: Int? = null,
    @Column(name = "imported_count")
    open var importedCount: Int? = null,
    @Column(name = "skipped_count")
    open var skippedCount: Int? = null,
    @Column(name = "duplicate_review_count")
    open var duplicateReviewCount: Int? = null,
    @Column(name = "error_count")
    open var errorCount: Int? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    open var errorDetails: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_payload", columnDefinition = "jsonb")
    open var reviewPayload: String? = null,
    @Column(name = "assistant_request_published_at")
    open var assistantRequestPublishedAt: Instant? = null,
    @Column(name = "date_from")
    open var dateFrom: LocalDate? = null,
    @Column(name = "date_to")
    open var dateTo: LocalDate? = null,
    @Column(name = "completed_at")
    open var completedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    companion object {
        const val JOB_KIND = "TRANSACTION_IMPORT"
    }
}
