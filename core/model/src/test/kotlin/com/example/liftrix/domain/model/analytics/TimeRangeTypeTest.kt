package com.example.liftrix.domain.model.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeRangeTypeTest {

    @Test
    fun fromConfigAcceptsSelectorLabelsAndStoredNames() {
        assertEquals(TimeRangeType.MONTH, TimeRangeType.fromConfig("1M"))
        assertEquals(TimeRangeType.MONTH, TimeRangeType.fromConfig("MONTH"))
        assertEquals(TimeRangeType.SIX_MONTHS, TimeRangeType.fromConfig("6M"))
        assertEquals(TimeRangeType.SIX_MONTHS, TimeRangeType.fromConfig("6 Months"))
        assertEquals(TimeRangeType.ALL_TIME, TimeRangeType.fromConfig("All"))
        assertEquals(TimeRangeType.ALL_TIME, TimeRangeType.fromConfig("ALL_TIME"))
    }
}
