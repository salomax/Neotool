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

