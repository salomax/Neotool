package io.github.salomax.neotool.app.domain.financial_data

import java.time.Instant
import java.util.regex.Pattern

/**
 * Domain class representing enhanced institution data from CNPJá API.
 *
 * This class stores additional information about financial institutions including:
 * - Domain extracted from CNPJá emails
 * - Website URL
 * - Social network links
 * - Logo storage key
 * - Full CNPJá API response (receita data)
 *
 * Primary key is `parentCnpj` (String), which is either:
 * - `cnpj_instituicao_lider` from institution table (if available)
 * - Constructed CNPJ from `bacen_cod_inst` (if no parent)
 */
data class ParentInstitution(
    /**
     * Root CNPJ (primary key).
     * Either cnpj_instituicao_lider from institution table, or constructed CNPJ from bacen_cod_inst.
     */
    val parentCnpj: String,
    
    /**
     * BACEN institution code (foreign key to institution table).
     */
    val bacenCodInst: String,
    
    /**
     * Domain extracted from first email in CNPJá API response.
     */
    val domain: String? = null,
    
    /**
     * Website URL if available from CNPJá API or website resolution.
     * Used for logo download and social network discovery.
     */
    val website: String? = null,
    
    /**
     * Storage key for institution logo from asset management service.
     * TODO: Asset management service integration pending.
     */
    val logoStorageKey: String? = null,
    
    /**
     * Social network links discovered from website scraping.
     * JSON object with keys: linkedin, instagram, facebook, twitter, x.
     * Only non-null values are stored (missing networks are omitted).
     */
    val socialNetwork: Map<String, Any>? = null,
    
    /**
     * Full CNPJá API response stored as JSONB.
     * Contains data from Receita.gov.br.
     */
    val receitaData: Map<String, Any>? = null,
    
    /**
     * Record creation timestamp.
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * Record update timestamp.
     */
    val updatedAt: Instant = Instant.now(),
) {
    /**
     * Validates CNPJ format (14 digits with valid check digits).
     *
     * @return true if CNPJ is valid, false otherwise
     */
    fun validateCnpj(): Boolean {
        val cnpj = parentCnpj.filter { it.isDigit() }
        if (cnpj.length != 14) return false
        
        // Reject CNPJs with all same digits
        if (Pattern.matches("^(\\d)\\1{13}$", cnpj)) return false
        
        // Calculate first check digit
        val weights1 = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum1 = cnpj.substring(0, 12).mapIndexed { index, char ->
            char.digitToInt() * weights1[index]
        }.sum()
        val mod1 = sum1 % 11
        val d1 = if (mod1 < 2) 0 else 11 - mod1
        
        if (d1 != cnpj[12].digitToInt()) return false
        
        // Calculate second check digit
        val weights2 = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum2 = cnpj.substring(0, 13).mapIndexed { index, char ->
            char.digitToInt() * weights2[index]
        }.sum()
        val mod2 = sum2 % 11
        val d2 = if (mod2 < 2) 0 else 11 - mod2
        
        return d2 == cnpj[13].digitToInt()
    }
    
    /**
     * Validates domain format if present.
     * Basic validation: checks for valid domain pattern.
     *
     * @return true if domain is null or valid, false if invalid format
     */
    fun validateDomain(): Boolean {
        if (domain == null || domain.isBlank()) return true
        
        // Basic domain validation: must contain at least one dot and valid characters
        val domainPattern = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
        )
        return domainPattern.matcher(domain.trim()).matches()
    }
    
    /**
     * Validates website URL format if present.
     * Checks for valid HTTP/HTTPS URL pattern.
     *
     * @return true if website is null or valid, false if invalid format
     */
    fun validateWebsite(): Boolean {
        if (website == null || website.isBlank()) return true
        
        // Basic URL validation: must start with http:// or https://
        val urlPattern = Pattern.compile(
            "^https?://([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(/.*)?$"
        )
        return urlPattern.matcher(website.trim()).matches()
    }
    
    /**
     * Validates all fields in the domain object.
     *
     * @return true if all validations pass, false otherwise
     */
    fun isValid(): Boolean {
        return validateCnpj() && validateDomain() && validateWebsite()
    }
    
    /**
     * Converts domain object to entity.
     * This method will be fully implemented when ParentInstitutionEntity is created (domain-004).
     *
     * @return ParentInstitutionEntity instance
     * @throws UnsupportedOperationException until entity is created
     */
    fun toEntity(): Nothing {
        throw UnsupportedOperationException(
            "toEntity() will be implemented when ParentInstitutionEntity is created (domain-004)"
        )
    }
}

