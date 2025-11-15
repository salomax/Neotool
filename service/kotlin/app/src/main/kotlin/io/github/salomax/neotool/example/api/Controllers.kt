package io.github.salomax.neotool.example.api

import io.github.salomax.neotool.example.dto.*
import io.github.salomax.neotool.example.service.*
import io.micronaut.http.annotation.*
import jakarta.validation.Valid
import java.util.Optional
import java.util.UUID

@Controller("/api/products")
open class ProductController(private val service: ProductService) {

    @Get("/")
    open fun list(): ProductListResponse = service.list().let { products ->
        ProductListResponse(products.map { it.toResponse() }, products.size)
    }

    @Get("/{id}")
    open fun get(@PathVariable id: UUID): Optional<ProductResponse> =
        Optional.ofNullable(service.get(id)?.toResponse())

    @Post("/")
    open fun create(@Valid @Body request: CreateProductRequest): ProductResponse =
        service.create(request.toDomain()).toResponse()

    @Put("/{id}")
    open fun update(@PathVariable id: UUID, @Valid @Body request: UpdateProductRequest): Optional<ProductResponse> =
        Optional.ofNullable(service.update(request.toDomain(id))?.toResponse())

    @Delete("/{id}")
    open fun delete(@PathVariable id: UUID) =
        service.delete(id)
}

@Controller("/api/customers")
open class CustomerController(private val service: CustomerService) {

    @Get("/")
    open fun list(): CustomerListResponse = service.list().let { customers ->
        CustomerListResponse(customers.map { it.toResponse() }, customers.size)
    }

    @Get("/{id}")
    open fun get(@PathVariable id: UUID): Optional<CustomerResponse> =
        Optional.ofNullable(service.get(id)?.toResponse())

    @Post("/")
    open fun create(@Valid @Body request: CreateCustomerRequest): CustomerResponse =
        service.create(request.toDomain()).toResponse()

    @Put("/{id}")
    open fun update(@PathVariable id: UUID, @Valid @Body request: UpdateCustomerRequest): Optional<CustomerResponse> =
        Optional.ofNullable(service.update(request.toDomain(id))?.toResponse())

    @Delete("/{id}")
    open fun delete(@PathVariable id: UUID) =
        service.delete(id)
}
