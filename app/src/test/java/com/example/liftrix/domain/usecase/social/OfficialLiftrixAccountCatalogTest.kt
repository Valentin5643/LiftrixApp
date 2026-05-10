package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialLiftrixAccountCatalogTest {

    @Test
    fun `bodyweight users receive baseline beginner and calisthenics accounts`() {
        val accountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
            selectedEquipment = setOf(Equipment.BODYWEIGHT_ONLY.name),
            selectedGoals = emptySet()
        )

        assertEquals(
            listOf(
                OfficialLiftrixAccountCatalog.COACH_ID,
                OfficialLiftrixAccountCatalog.CHALLENGE_ID,
                OfficialLiftrixAccountCatalog.BEGINNER_ID,
                OfficialLiftrixAccountCatalog.CALISTHENICS_ID
            ),
            accountIds
        )
    }

    @Test
    fun `pull up bar users receive calisthenics account`() {
        val accountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
            selectedEquipment = setOf(Equipment.PULL_UP_BAR.name),
            selectedGoals = emptySet()
        )

        assertTrue(OfficialLiftrixAccountCatalog.CALISTHENICS_ID in accountIds)
    }

    @Test
    fun `barbell strength users receive powerlifting account`() {
        val accountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
            selectedEquipment = setOf(Equipment.BARBELL.name),
            selectedGoals = setOf(FitnessGoal.INCREASE_STRENGTH.name)
        )

        assertTrue(OfficialLiftrixAccountCatalog.POWERLIFTING_ID in accountIds)
    }

    @Test
    fun `build muscle with bench receives powerlifting account`() {
        val accountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
            selectedEquipment = setOf(Equipment.BENCH.name),
            selectedGoals = setOf(FitnessGoal.BUILD_MUSCLE.name)
        )

        assertTrue(OfficialLiftrixAccountCatalog.POWERLIFTING_ID in accountIds)
    }

    @Test
    fun `general fitness receives baseline and beginner with no duplicates`() {
        val accountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
            selectedEquipment = setOf(Equipment.DUMBBELLS.name, Equipment.BENCH.name),
            selectedGoals = setOf(FitnessGoal.GENERAL_FITNESS.name)
        )

        assertEquals(accountIds.distinct(), accountIds)
        assertEquals(
            listOf(
                OfficialLiftrixAccountCatalog.COACH_ID,
                OfficialLiftrixAccountCatalog.CHALLENGE_ID,
                OfficialLiftrixAccountCatalog.BEGINNER_ID
            ),
            accountIds
        )
    }
}
