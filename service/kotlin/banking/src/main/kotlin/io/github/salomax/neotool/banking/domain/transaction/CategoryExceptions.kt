package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

class CategoryNotFoundException(id: UUID) : RuntimeException("Category not found: $id")

class CategoryDepthExceededException : RuntimeException("Category hierarchy depth exceeded")

class SystemCategoryNotEditableException : RuntimeException("System categories cannot be modified")

class CategoryApplicableToMismatchException(
    childApplicableTo: CategoryApplicableTo,
    parentApplicableTo: CategoryApplicableTo,
) : RuntimeException("Category applicableTo $childApplicableTo is not compatible with parent $parentApplicableTo")

class DuplicateCategoryNameException(name: String) : RuntimeException("Category name already exists: $name")

// Category rule exceptions (CAT-13)
class CategoryRuleNotFoundException(id: java.util.UUID) : RuntimeException("Category rule not found: $id")

class SystemCategoryRuleNotDeletableException : RuntimeException("System category rules cannot be deleted")

class SystemCategoryRuleNotEditableException : RuntimeException("System category rules cannot be modified")

class DuplicateCategoryRuleException(pattern: String, patternType: PatternType) :
    RuntimeException("A rule with pattern '$pattern' and type $patternType already exists for this account")

class InvalidRegexPatternException(pattern: String, cause: Throwable? = null) :
    RuntimeException("Invalid REGEX pattern: $pattern", cause)
