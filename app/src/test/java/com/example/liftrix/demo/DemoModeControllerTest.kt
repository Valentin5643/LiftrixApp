package com.example.liftrix.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoModeControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `activate emits active state with session seed`() = runTest {
        val controller = controller()

        val result = controller.activate()
        val state = controller.state.first { it.isActive }

        assertThat(result.isSuccess).isTrue()
        assertThat(state.isActive).isTrue()
        assertThat(state.sessionSeed).isNotNull()
        assertThat(state.activatedAtMillis).isNotNull()
    }

    @Test
    fun `disable clears active session seed`() = runTest {
        val controller = controller()

        controller.activate()
        controller.state.first { it.isActive }
        val result = controller.disable()
        val state = controller.state.first { !it.isActive && it.lastDisabledAtMillis != null }

        assertThat(result.isSuccess).isTrue()
        assertThat(state.sessionSeed).isNull()
        assertThat(state.lastDisabledAtMillis).isNotNull()
    }

    private fun kotlinx.coroutines.test.TestScope.controller(): DemoModeController {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFolder().resolve("demo.preferences_pb") }
        )
        return DemoModeController(DemoModeStore(dataStore), backgroundScope)
    }
}
