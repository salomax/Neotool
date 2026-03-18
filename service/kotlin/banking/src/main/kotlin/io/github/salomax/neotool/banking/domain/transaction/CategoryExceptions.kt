package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.DomainException
import java.util.UUID

class CategoryNotFoundException(id: UUID) :
    DomainException(
        errorCode = BankingErrorCode.CATEGORY_NOT_FOUND,
        field = "categoryId",
        parameters = mapOf("categoryId" to id.toString()),
    )

class CategoryDepthExceededException :
    DomainException(errorCode = BankingErrorCode.CATEGORY_DEPTH_EXCEEDED)

class SystemCategoryNotEditableException :
    DomainException(errorCode = BankingErrorCode.CATEGORY_SYSTEM_NOT_EDITABLE)

class CategoryApplicableToMismatchException(
    childApplicableTo: CategoryApplicableTo,
    parentApplicableTo: CategoryApplicableTo,
) : DomainException(
        errorCode = BankingErrorCode.CATEGORY_APPLICABLE_TO_MISMATCH,
        field = "applicableTo",
        parameters =
            mapOf(
                "childApplicableTo" to childApplicableTo.name,
                "parentApplicableTo" to parentApplicableTo.name,
            ),
    )

class DuplicateCategoryNameException(name: String) :
    DomainException(
        errorCode = BankingErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
        field = "name",
        parameters = mapOf("name" to name),
    )

// Category rule exceptions (CAT-13)
class CategoryRuleNotFoundException(id: UUID) :
    DomainException(
        errorCode = BankingErrorCode.CATEGORY_RULE_NOT_FOUND,
        field = "ruleId",
        parameters = mapOf("ruleId" to id.toString()),
    )

class SystemCategoryRuleNotDeletableException :
    DomainException(errorCode = BankingErrorCode.CATEGORY_RULE_SYSTEM_NOT_DELETABLE)

class SystemCategoryRuleNotEditableException :
    DomainException(errorCode = BankingErrorCode.CATEGORY_RULE_SYSTEM_NOT_EDITABLE)

class DuplicateCategoryRuleException(pattern: String, patternType: PatternType) :
    DomainException(
        errorCode = BankingErrorCode.CATEGORY_RULE_DUPLICATE,
        field = "pattern",
        parameters =
            mapOf(
                "pattern" to pattern,
                "patternType" to patternType.name,
            ),
    )

class InvalidRegexPatternException(pattern: String, cause: Throwable? = null) :
    DomainException(
        errorCode = BankingErrorCode.CATEGORY_RULE_INVALID_REGEX,
        field = "pattern",
        parameters = mapOf("pattern" to pattern),
        cause = cause,
    )
