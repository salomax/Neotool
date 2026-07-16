package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionCategoryRepository IT")
class TransactionCategoryRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var categoryRepository: TransactionCategoryRepository

    private lateinit var accountId: UUID

    @BeforeEach
    fun insertTestAccount() {
        accountId = UUID.randomUUID()
    }

    @Nested
    @DisplayName("findAll")
    inner class FindAllTests {
        @Test
        fun `returns all system categories and user-defined for account excludes soft-deleted`() {
            val all = categoryRepository.findAll(accountId)
            assertThat(all).isNotEmpty
            val system = all.filter { it.accountId == null }
            val userDefined = all.filter { it.accountId == accountId }
            assertThat(system).isNotEmpty
            assertThat(system).allMatch { it.isSystem }
        }

        @Test
        fun `system categories from seed have correct names icons colors and parent child structure`() {
            val all = categoryRepository.findAll(accountId)
            val roots = all.filter { it.parentId == null }
            assertThat(roots).isNotEmpty
            val salary = roots.find { it.name == "Salário" }
            assertThat(salary).isNotNull
            assertThat(salary!!.icon).isEqualTo("payments")
            assertThat(salary.color).isEqualTo("#2E7D32")
            val alimentacao = roots.find { it.name == "Alimentação" }
            assertThat(alimentacao).isNotNull
            assertThat(alimentacao!!.icon).isEqualTo("restaurant")
            val presentes = roots.find { it.name == "Presentes" }
            assertThat(presentes).isNotNull
            assertThat(presentes!!.icon).isEqualTo("card_giftcard")
            assertThat(presentes.color).isEqualTo("#AB47BC")
            val celebracoes = roots.find { it.name == "Celebrações & Festas" }
            assertThat(celebracoes).isNotNull
            assertThat(celebracoes!!.icon).isEqualTo("celebration")
            assertThat(celebracoes.color).isEqualTo("#FF5722")
            val beleza = roots.find { it.name == "Beleza e Cuidados Pessoais" }
            assertThat(beleza).isNotNull
            assertThat(beleza!!.icon).isEqualTo("face_retouching_natural")
            assertThat(beleza.color).isEqualTo("#EC407A")
            assertThat(beleza.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(beleza.isSystem).isTrue()
            val outros = roots.find { it.name == "Outros" }
            assertThat(outros).isNotNull
            assertThat(outros!!.icon).isEqualTo("help_outline")
            val direitosTrabalhistas = roots.find { it.name == "Direitos Trabalhistas" }
            assertThat(direitosTrabalhistas).isNotNull
            assertThat(direitosTrabalhistas!!.icon).isEqualTo("gavel")
            assertThat(direitosTrabalhistas.color).isEqualTo("#00695C")
            val children = all.filter { it.parentId == direitosTrabalhistas.id }
            val fgts = children.find { it.name == "FGTS" }
            assertThat(fgts).isNotNull
            assertThat(fgts!!.icon).isEqualTo("savings")
            assertThat(fgts.color).isEqualTo("#00695C")
            val seguroDesemprego = children.find { it.name == "Seguro Desemprego" }
            assertThat(seguroDesemprego).isNotNull
            assertThat(seguroDesemprego!!.icon).isEqualTo("shield")
            assertThat(seguroDesemprego.color).isEqualTo("#00695C")
            val belezaChildren = all.filter { it.parentId == beleza.id }
            assertThat(belezaChildren).hasSize(8)
            assertThat(belezaChildren)
                .allMatch { it.isSystem }
                .allMatch { it.applicableTo.name == "EXPENSE" }
                .allMatch { it.color == "#EC407A" }
            assertThat(belezaChildren.find { it.name == "Perfumaria" }?.icon).isEqualTo("local_florist")
            assertThat(belezaChildren.find { it.name == "Cabeleireiro" }?.icon).isEqualTo("content_cut")
            assertThat(belezaChildren.find { it.name == "Depilação" }?.icon).isEqualTo("spa")
            assertThat(belezaChildren.find { it.name == "Maquiagem" }?.icon).isEqualTo("brush")
            assertThat(belezaChildren.find { it.name == "Barbearia" }?.icon).isEqualTo("content_cut")
            assertThat(belezaChildren.find { it.name == "Manicure e Pedicure" }?.icon).isEqualTo("color_lens")
            assertThat(belezaChildren.find { it.name == "Estética Facial" }?.icon).isEqualTo("face_retouching_natural")
            assertThat(belezaChildren.find { it.name == "Massagem" }?.icon).isEqualTo("self_improvement")
            val alimentacaoChildren = all.filter { it.parentId == alimentacao.id }
            val padaria = alimentacaoChildren.find { it.name == "Padaria" }
            assertThat(padaria).isNotNull
            assertThat(padaria!!.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(padaria.icon).isEqualTo("bakery_dining")
            assertThat(padaria.sortOrder).isEqualTo(50)
            assertThat(padaria.color).isEqualTo(alimentacao.color)
            val moradia = roots.find { it.name == "Moradia" }
            assertThat(moradia).isNotNull
            assertThat(moradia!!.isSystem).isTrue()
            val moradiaChildren = all.filter { it.parentId == moradia.id }
            val lavanderia = moradiaChildren.find { it.name == "Lavanderia" }
            assertThat(lavanderia).isNotNull
            assertThat(lavanderia!!.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(lavanderia.icon).isEqualTo("local_laundry_service")
            assertThat(lavanderia.sortOrder).isEqualTo(80)
            assertThat(lavanderia.color).isEqualTo(moradia.color)
            val transporte = roots.find { it.name == "Transporte" }
            assertThat(transporte).isNotNull
            assertThat(transporte!!.isSystem).isTrue()
            val transporteChildren = all.filter { it.parentId == transporte.id }
            val lavaRapido = transporteChildren.find { it.name == "Lava rápido" }
            assertThat(lavaRapido).isNotNull
            assertThat(lavaRapido!!.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(lavaRapido.icon).isEqualTo("local_car_wash")
            assertThat(lavaRapido.sortOrder).isEqualTo(90)
            assertThat(lavaRapido.color).isEqualTo(transporte.color)
            val lazer = roots.find { it.name == "Lazer e Entretenimento" }
            assertThat(lazer).isNotNull
            assertThat(lazer!!.isSystem).isTrue()
            val lazerChildren = all.filter { it.parentId == lazer.id }
            val hotelAirbnb = lazerChildren.find { it.name == "Hotel / Airbnb e similares" }
            assertThat(hotelAirbnb).isNotNull
            assertThat(hotelAirbnb!!.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(hotelAirbnb.icon).isEqualTo("hotel")
            assertThat(hotelAirbnb.sortOrder).isEqualTo(45)
            assertThat(hotelAirbnb.color).isEqualTo(lazer.color)
            val tarifasBancarias = roots.find { it.name == "Tarifas Bancárias" }
            assertThat(tarifasBancarias).isNotNull
            assertThat(tarifasBancarias!!.isSystem).isTrue()
            val tarifasBancariasChildren = all.filter { it.parentId == tarifasBancarias.id }
            val faturaCartao = tarifasBancariasChildren.find { it.name == "Fatura Cartão de Crédito" }
            assertThat(faturaCartao).isNotNull
            assertThat(faturaCartao!!.applicableTo.name).isEqualTo("EXPENSE")
            assertThat(faturaCartao.icon).isEqualTo("credit_card")
            assertThat(faturaCartao.sortOrder).isEqualTo(40)
            assertThat(faturaCartao.color).isEqualTo(tarifasBancarias.color)
            val withParent = all.filter { it.parentId != null }
            assertThat(withParent).allMatch { cat -> all.any { it.id == cat.parentId } }
        }
    }

    @Nested
    @DisplayName("findById")
    inner class FindByIdTests {
        @Test
        fun `returns null for soft-deleted category`() {
            val container = getPgContainer()
            val catId = UUID.randomUUID()
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            ).use {
                    conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.transaction_categories (id, account_id, name, applicable_to, is_system, sort_order, created_at, updated_at)
                    VALUES ('$catId', '$accountId', 'To Delete', 'ANY', false, 0, NOW(), NOW())
                    """.trimIndent(),
                )
                conn.createStatement().executeUpdate(
                    "UPDATE banking.transaction_categories SET deleted_at = NOW() WHERE id = '$catId'",
                )
            }
            val found = categoryRepository.findById(catId)
            assertThat(found).isNull()
        }
    }
}
