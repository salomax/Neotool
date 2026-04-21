package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.model.dto.investment.GridClassMapping
import io.github.salomax.neotool.banking.domain.investment.AssetType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GridClassMappingTest {
    @Test
    fun `maps STOCK to stocks`() {
        assertThat(GridClassMapping.gridClassFor(AssetType.STOCK)).isEqualTo(GridClassMapping.STOCKS)
    }

    @Test
    fun `maps REAL_ESTATE_FUND to reits`() {
        assertThat(GridClassMapping.gridClassFor(AssetType.REAL_ESTATE_FUND)).isEqualTo(GridClassMapping.REITS)
    }

    @Test
    fun `maps CRYPTO to crypto`() {
        assertThat(GridClassMapping.gridClassFor(AssetType.CRYPTO)).isEqualTo(GridClassMapping.CRYPTO)
    }

    @Test
    fun `maps OPTION to null`() {
        assertThat(GridClassMapping.gridClassFor(AssetType.OPTION)).isNull()
    }

    @Test
    fun `maps null to null`() {
        assertThat(GridClassMapping.gridClassFor(null)).isNull()
    }

    @Test
    fun `all keys include exactly six grid classes`() {
        assertThat(GridClassMapping.ALL_KEYS).containsExactlyInAnyOrder(
            GridClassMapping.CASH,
            GridClassMapping.FIXED_INCOME,
            GridClassMapping.FUNDS,
            GridClassMapping.STOCKS,
            GridClassMapping.REITS,
            GridClassMapping.CRYPTO,
        )
    }
}
