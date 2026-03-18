package io.github.salomax.neotool.banking.model.billimportjob

import io.github.salomax.neotool.banking.domain.billimport.BillImportJobStatus
import io.github.salomax.neotool.banking.domain.billimport.ImportDocumentType
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
import java.util.UUID

@Entity
@Table(name = "pdf_import_jobs", schema = "banking")
open class BillImportJobEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_account_id")
    open var financialAccountId: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "created_by_user_id", nullable = false)
    open var createdByUserId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 32)
    open var importType: ImportDocumentType = ImportDocumentType.CREDIT_CARD_BILL,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    open var status: BillImportJobStatus,
    @Column(name = "asset_id", nullable = false)
    open var assetId: UUID,
    @Column(name = "file_name", length = 255)
    open var fileName: String? = null,
    @Column(name = "file_hash", nullable = false, length = 64)
    open var fileHash: String,
    @Column(name = "extracted_text")
    open var extractedText: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_metadata", columnDefinition = "jsonb")
    open var extractionMetadata: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assistant_response", columnDefinition = "jsonb")
    open var assistantResponse: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_payload", columnDefinition = "jsonb")
    open var reviewPayload: String? = null,
    @Column(name = "error_message")
    open var errorMessage: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    open var errorDetails: String? = null,
    @Column(name = "completed_at")
    open var completedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
