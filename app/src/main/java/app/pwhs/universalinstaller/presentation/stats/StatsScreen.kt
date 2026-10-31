@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.pwhs.universalinstaller.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.stats.components.ActivityTimelineCard
import app.pwhs.universalinstaller.presentation.stats.components.InstallUninstallOverviewCard
import app.pwhs.universalinstaller.presentation.stats.components.MilestoneCategoryFilterRow
import app.pwhs.universalinstaller.presentation.stats.components.MilestoneItemCard
import app.pwhs.universalinstaller.presentation.stats.components.ModesAndSecurityCard
import app.pwhs.universalinstaller.presentation.stats.components.StorageFormatsCard
import app.pwhs.universalinstaller.presentation.stats.components.TopInstalledAppsCard
import app.pwhs.universalinstaller.presentation.stats.components.UserRankBannerCard
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    initialTab: Int = 0,
) {
    val summary by viewModel.summary.collectAsState()
    val selectedDays by viewModel.selectedPeriodDays.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    var activeTab by rememberSaveable { mutableIntStateOf(initialTab) }

    LaunchedEffect(Unit) {
        delay(2_500L)
        viewModel.markMilestonesSeen()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_screen_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "rank_banner") {
                UserRankBannerCard(summary = summary)
            }

            item(key = "tab_selector") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.QueryStats,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.stats_tab_overview))
                    }
                    SegmentedButton(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text(
                            text = "${stringResource(R.string.stats_tab_milestones)} (${summary.unlockedCount}/${summary.totalMilestones})",
                        )
                    }
                }
            }

            if (activeTab == 0) {
                item(key = "install_uninstall_card") {
                    InstallUninstallOverviewCard(summary = summary)
                }
                item(key = "storage_formats_card") {
                    StorageFormatsCard(summary = summary)
                }
                item(key = "modes_security_card") {
                    ModesAndSecurityCard(summary = summary)
                }
                item(key = "activity_timeline_card") {
                    ActivityTimelineCard(
                        summary = summary,
                        selectedDays = selectedDays,
                        onSelectDays = viewModel::selectPeriodDays,
                    )
                }
                if (summary.topUpdatedApps.isNotEmpty()) {
                    item(key = "top_apps_card") {
                        TopInstalledAppsCard(topApps = summary.topUpdatedApps)
                    }
                }
            } else {
                item(key = "milestone_category_filter") {
                    MilestoneCategoryFilterRow(
                        selectedCategory = selectedCategory,
                        onSelectCategory = viewModel::selectCategory,
                    )
                }

                val filteredMilestones = summary.milestones
                    .filter { selectedCategory == null || it.id.category == selectedCategory }
                    .sortedWith(
                        compareByDescending<app.pwhs.universalinstaller.domain.stats.MilestoneProgress> { it.isNewlyUnlocked }
                            .thenByDescending { it.isUnlocked }
                            .thenByDescending { it.progressFraction },
                    )

                items(
                    items = filteredMilestones,
                    key = { "milestone_${it.id.name}" },
                ) { milestone ->
                    MilestoneItemCard(item = milestone)
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
