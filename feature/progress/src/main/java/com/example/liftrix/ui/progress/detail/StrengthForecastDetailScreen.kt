package com.example.liftrix.ui.progress.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.StrengthForecastEvent
import com.example.liftrix.ui.progress.StrengthForecastState
import com.example.liftrix.ui.progress.StrengthForecastViewModel
import com.example.liftrix.ui.progress.components.StrengthForecastCard
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen

@Composable
fun StrengthForecastDetailScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: StrengthForecastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = (uiState as? UiState.Success)?.data ?: StrengthForecastState()

    LaunchedEffect(Unit) {
        viewModel.handleEvent(StrengthForecastEvent.Load)
    }

    AnalyticsDetailScreen(
        title = "Strength Forecast",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StrengthForecastCard(
                state = state,
                weightUnit = WeightUnit.getSystemDefault(),
                onEvent = viewModel::handleEvent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
