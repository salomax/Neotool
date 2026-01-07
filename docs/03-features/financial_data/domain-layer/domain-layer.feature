@financial_data @domain @ddd
Feature: Domain Layer Implementation for Financial Data Models
  As a developer
  I want to implement domain objects following DDD patterns for financial_data service
  So that business logic is separated from persistence layer and type-safe domain models are available

  Background:
    Given the financial_data database schema exists
    And migrations V1_2, V1_6, V1_7, and V1_8 have been applied
    And the domain layer follows patterns from domain-model-pattern.md

  Rule: Institution Domain Object
    @happy-path
    Scenario: Create Institution domain object with all required fields
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string (64 characters)
      When I create an Institution domain object
      Then the domain object should be created successfully
      And bacenCodInst should be "0001"
      And anoMesRef should be "202401"
      And sourceHash should match the provided hash
      And createdAt and updatedAt should be set to current timestamp

    @happy-path
    Scenario: Create Institution domain object with optional fields
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string
      And nomeInstituicao is "Banco Example"
      And situacao is "A"
      And uf is "SP"
      And municipio is "São Paulo"
      And tipoConsolidadoBancario is "b1"
      And tipoConsolidacao is "I"
      And tipoControle is "1"
      When I create an Institution domain object
      Then the domain object should be created successfully
      And all provided fields should be set correctly

    @validation
    Scenario: Institution domain object requires bacenCodInst
      Given I have institution data without bacenCodInst
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate bacenCodInst is required

    @validation
    Scenario: Institution domain object requires anoMesRef
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is missing
      And sourceHash is a valid SHA-256 hex string
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate anoMesRef is required

    @validation
    Scenario: Institution domain object requires sourceHash
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is missing
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate sourceHash is required

    @validation
    Scenario: Institution sourceHash must be exactly 64 characters
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is "abc123" (less than 64 characters)
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate sourceHash must be 64 characters

    @validation
    Scenario: Institution anoMesRef must be 6 characters (YYYYMM format)
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "2024" (invalid format)
      And sourceHash is a valid SHA-256 hex string
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate anoMesRef must be 6 characters

    @validation
    Scenario: Institution situacao must be single character
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string
      And situacao is "ACTIVE" (more than one character)
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate situacao must be single character

    @validation
    Scenario: Institution tipoConsolidacao must be 'I' or 'C'
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string
      And tipoConsolidacao is "X" (invalid value)
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate tipoConsolidacao must be 'I' or 'C'

    @validation
    Scenario: Institution tipoControle must be '1', '2', or '3'
      Given I have institution data with bacenCodInst "0001"
      And anoMesRef is "202401"
      And sourceHash is a valid SHA-256 hex string
      And tipoControle is "4" (invalid value)
      When I attempt to create an Institution domain object
      Then creation should fail
      And error should indicate tipoControle must be '1', '2', or '3'

    @happy-path
    Scenario: Convert Institution domain object to entity
      Given I have a valid Institution domain object
      When I call toEntity() on the domain object
      Then an InstitutionEntity should be returned
      And all fields should be mapped correctly
      And id should be set to bacenCodInst value

  Rule: BankReport Domain Object
    @happy-path
    Scenario: Create BankReport domain object with all required fields
      Given I have bank report data
      And codInst is "0001"
      And anoMes is "202401"
      And nomeRelatorio is "Balanço Patrimonial"
      And numeroRelatorio is "1"
      And tipoInstituicao is 1
      When I create a BankReport domain object
      Then the domain object should be created successfully
      And id should be null (for new entities, DB generates UUID v7)
      And codInst should be "0001"
      And anoMes should be "202401"
      And nomeRelatorio should be "Balanço Patrimonial"
      And numeroRelatorio should be "1"
      And tipoInstituicao should be 1
      And createdAt and updatedAt should be set to current timestamp

    @validation
    Scenario: BankReport domain object requires codInst
      Given I have bank report data without codInst
      And anoMes is "202401"
      And nomeRelatorio is "Balanço Patrimonial"
      And numeroRelatorio is "1"
      And tipoInstituicao is 1
      When I attempt to create a BankReport domain object
      Then creation should fail
      And error should indicate codInst is required

    @validation
    Scenario: BankReport domain object requires all natural key fields
      Given I have bank report data with codInst "0001"
      And anoMes is missing
      And nomeRelatorio is "Balanço Patrimonial"
      And numeroRelatorio is "1"
      And tipoInstituicao is 1
      When I attempt to create a BankReport domain object
      Then creation should fail
      And error should indicate all natural key fields are required

    @validation
    Scenario: BankReport tipoInstituicao must be between 1 and 4
      Given I have bank report data with codInst "0001"
      And anoMes is "202401"
      And nomeRelatorio is "Balanço Patrimonial"
      And numeroRelatorio is "1"
      And tipoInstituicao is 5 (invalid value)
      When I attempt to create a BankReport domain object
      Then creation should fail
      And error should indicate tipoInstituicao must be between 1 and 4

    @validation
    Scenario: BankReport tipoInstituicao must be positive
      Given I have bank report data with codInst "0001"
      And anoMes is "202401"
      And nomeRelatorio is "Balanço Patrimonial"
      And numeroRelatorio is "1"
      And tipoInstituicao is 0 (invalid value)
      When I attempt to create a BankReport domain object
      Then creation should fail
      And error should indicate tipoInstituicao must be between 1 and 4

    @happy-path
    Scenario: Convert BankReport domain object to entity
      Given I have a valid BankReport domain object
      When I call toEntity() on the domain object
      Then a BankReportEntity should be returned
      And all fields should be mapped correctly
      And id should be null (for new entities, DB generates UUID v7)

  Rule: BankReportData Domain Object
    @happy-path
    Scenario: Create BankReportData domain object with all required fields
      Given I have bank report data row
      And bankReportId is a valid UUID
      And grupo is "Ativo"
      And conta is "1.1.01"
      And nomeColuna is "Caixa"
      And descricaoColuna is "Caixa e Equivalentes de Caixa"
      And saldo is 1000000.50
      When I create a BankReportData domain object
      Then the domain object should be created successfully
      And id should be null (for new entities, DB generates UUID v7)
      And bankReportId should match the provided UUID
      And all provided fields should be set correctly
      And createdAt and updatedAt should be set to current timestamp

    @happy-path
    Scenario: Create BankReportData domain object with nullable saldo
      Given I have bank report data row
      And bankReportId is a valid UUID
      And saldo is null (can be currency or percentage)
      When I create a BankReportData domain object
      Then the domain object should be created successfully
      And saldo should be null

    @validation
    Scenario: BankReportData domain object requires bankReportId
      Given I have bank report data row without bankReportId
      And grupo is "Ativo"
      And conta is "1.1.01"
      When I attempt to create a BankReportData domain object
      Then creation should fail
      And error should indicate bankReportId is required

    @validation
    Scenario: BankReportData bankReportId must be a valid UUID
      Given I have bank report data row
      And bankReportId is "invalid-uuid"
      And grupo is "Ativo"
      When I attempt to create a BankReportData domain object
      Then creation should fail
      And error should indicate bankReportId must be a valid UUID

    @happy-path
    Scenario: Convert BankReportData domain object to entity
      Given I have a valid BankReportData domain object
      When I call toEntity() on the domain object
      Then a BankReportDataEntity should be returned
      And all fields should be mapped correctly
      And id should be null (for new entities, DB generates UUID v7)

  Rule: InstitutionEnhancement Domain Object
    @happy-path
    Scenario: Create InstitutionEnhancement domain object with all fields
      Given I have institution enhancement data
      And parentCnpj is "12345678000190"
      And legalName is "Banco Example S.A."
      And tradeName is "Banco Example"
      And emailDomain is "example.com.br"
      And website is "https://www.example.com.br"
      And logoStorageKey is "logos/example.png"
      And socialNetwork is a valid JSONB object
      And receitaData is a valid JSONB object
      When I create an InstitutionEnhancement domain object
      Then the domain object should be created successfully
      And parentCnpj should be "12345678000190"
      And all provided fields should be set correctly
      And createdAt and updatedAt should be set to current timestamp

    @happy-path
    Scenario: Create InstitutionEnhancement domain object with minimal fields
      Given I have institution enhancement data
      And parentCnpj is "12345678000190"
      And other fields are optional
      When I create an InstitutionEnhancement domain object
      Then the domain object should be created successfully
      And parentCnpj should be set
      And optional fields should be null

    @validation
    Scenario: InstitutionEnhancement domain object requires parentCnpj
      Given I have institution enhancement data without parentCnpj
      And legalName is "Banco Example S.A."
      When I attempt to create an InstitutionEnhancement domain object
      Then creation should fail
      And error should indicate parentCnpj is required

    @validation
    Scenario: InstitutionEnhancement parentCnpj must not be blank
      Given I have institution enhancement data
      And parentCnpj is an empty string
      When I attempt to create an InstitutionEnhancement domain object
      Then creation should fail
      And error should indicate parentCnpj must not be blank

    @validation
    Scenario: InstitutionEnhancement socialNetwork must be valid JSONB
      Given I have institution enhancement data
      And parentCnpj is "12345678000190"
      And socialNetwork is invalid JSON string
      When I attempt to create an InstitutionEnhancement domain object
      Then creation should fail
      And error should indicate socialNetwork must be valid JSON

    @validation
    Scenario: InstitutionEnhancement receitaData must be valid JSONB
      Given I have institution enhancement data
      And parentCnpj is "12345678000190"
      And receitaData is invalid JSON string
      When I attempt to create an InstitutionEnhancement domain object
      Then creation should fail
      And error should indicate receitaData must be valid JSON

    @happy-path
    Scenario: Convert InstitutionEnhancement domain object to entity
      Given I have a valid InstitutionEnhancement domain object
      When I call toEntity() on the domain object
      Then an InstitutionEnhancementEntity should be returned
      And all fields should be mapped correctly
      And id should be set to parentCnpj value

  Rule: Domain Object Package Structure
    @happy-path
    Scenario: Domain objects are in correct package
      Given domain objects are implemented
      When I check the package structure
      Then Institution should be in package "io.github.salomax.neotool.financial_data.domain"
      And BankReport should be in package "io.github.salomax.neotool.financial_data.domain"
      And BankReportData should be in package "io.github.salomax.neotool.financial_data.domain"
      And InstitutionEnhancement should be in package "io.github.salomax.neotool.financial_data.domain"

  Rule: Domain-Entity Conversion Pattern
    @happy-path
    Scenario: Domain object with null ID converts to entity with null ID (UUID v7)
      Given I have a BankReport domain object
      And id is null (new entity)
      When I call toEntity() on the domain object
      Then the entity should have id set to null
      And the database will generate UUID v7 on insert

    @happy-path
    Scenario: Domain object with existing ID converts to entity with same ID
      Given I have a BankReport domain object
      And id is an existing UUID
      When I call toEntity() on the domain object
      Then the entity should have the same UUID as the domain object

    @happy-path
    Scenario: Institution domain object converts with String primary key
      Given I have an Institution domain object
      And bacenCodInst is "0001"
      When I call toEntity() on the domain object
      Then the entity should have id set to "0001"
      And the entity primary key should be the bacenCodInst value


