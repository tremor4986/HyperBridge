package com.alexkoala.kyper.ui.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import com.alexkoala.kyper.R
import com.alexkoala.kyper.data.AppPreferences
import com.alexkoala.kyper.ui.screens.home.HomeScreen
import com.alexkoala.kyper.ui.screens.onboarding.OnboardingScreen
import com.alexkoala.kyper.ui.screens.settings.AppPriorityScreen
import com.alexkoala.kyper.ui.screens.settings.BackupSettingsScreen
import com.alexkoala.kyper.ui.screens.settings.BlocklistAppListScreen
import com.alexkoala.kyper.ui.screens.settings.ChangelogHistoryScreen
import com.alexkoala.kyper.ui.screens.settings.EngineSettingsScreen
import com.alexkoala.kyper.ui.screens.settings.GlobalBlocklistScreen
import com.alexkoala.kyper.ui.screens.settings.GlobalSettingsScreen
import com.alexkoala.kyper.ui.screens.settings.ImportPreviewScreen
import com.alexkoala.kyper.ui.screens.settings.InfoScreen
import com.alexkoala.kyper.ui.screens.settings.IslandSettingsScreen
import com.alexkoala.kyper.ui.screens.settings.LicensesScreen
import com.alexkoala.kyper.ui.screens.settings.NavCustomizationScreen
import com.alexkoala.kyper.ui.screens.settings.PrioritySettingsScreen
import com.alexkoala.kyper.ui.screens.settings.SetupHealthScreen
import com.alexkoala.kyper.util.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun mainNavGraph(
    context: Context,
    scope: CoroutineScope,
    preferences: AppPreferences,
    navigator: Navigator<Screen>,
    backupManager: BackupManager,
    currentVersionCode: Int,
    onExit: () -> Unit
) = entryProvider {
    entry<Screen.Onboarding> {
        OnboardingScreen {
            scope.launch {
                preferences.setSetupComplete(true)
                navigator.finishOnboarding(Screen.Home)
            }
        }
    }
    entry<Screen.Home> {
        HomeScreen(
            onSettingsClick = { navigator.navigate(Screen.Info) },
            onNavConfigClick = { pkg -> navigator.navigate(Screen.NavCustomization(pkg)) }
        )
    }
    entry<Screen.Info> {
        InfoScreen(
            onBack = { if (!navigator.goBack()) onExit() },
            onSetupClick = { navigator.navigate(Screen.Setup) },
            onLicensesClick = { navigator.navigate(Screen.Licenses) },
            onBehaviorClick = { navigator.navigate(Screen.Behavior) },
            onGlobalSettingsClick = { navigator.navigate(Screen.GlobalSettings) },
            onHistoryClick = { navigator.navigate(Screen.History) },
            onBlocklistClick = { navigator.navigate(Screen.GlobalBlocklist) },
            onBackupClick = { navigator.navigate(Screen.Backup) },
            onEngineClick = { navigator.navigate(Screen.EngineSettings) }
        )
    }
    entry<Screen.GlobalSettings> {
        GlobalSettingsScreen(
            onBack = { navigator.goBack() },
            onNavSettingsClick = { navigator.navigate(Screen.NavCustomization(null)) },
            onInlineReplyClick = { navigator.navigate(Screen.ReplyCustomization) },
            onIslandSettingsClick = { navigator.navigate(Screen.IslandSettings) },
            onEngineSettingsClick = { navigator.navigate(Screen.EngineSettings) },
            onDndSettingsClick = { navigator.navigate(Screen.DndSettings) },
            onPermanentIslandClick = { navigator.navigate(Screen.PermanentIslandConfig) }
        )
    }
    entry<Screen.DndSettings> {
        com.alexkoala.kyper.ui.screens.settings.DndSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.PermanentIslandConfig> {
        com.alexkoala.kyper.ui.screens.settings.PermanentIslandConfigScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.ReplyCustomization> {
        com.alexkoala.kyper.ui.screens.theme.GlobalReplyCustomizationScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.NavCustomization> { key ->
        NavCustomizationScreen(
            onBack = { navigator.goBack() },
            packageName = key.packageName
        )
    }
    entry<Screen.EngineSettings> {
        EngineSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Setup> {
        SetupHealthScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Licenses> {
        LicensesScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Behavior> {
        PrioritySettingsScreen(
            onBack = { navigator.goBack() },
            onNavigateToPriorityList = { navigator.navigate(Screen.AppPriority) }
        )
    }
    entry<Screen.AppPriority> {
        AppPriorityScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.History> {
        ChangelogHistoryScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.GlobalBlocklist> {
        GlobalBlocklistScreen(
            onBack = { navigator.goBack() },
            onNavigateToAppList = { navigator.navigate(Screen.BlocklistApps) }
        )
    }
    entry<Screen.BlocklistApps> {
        BlocklistAppListScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Backup> {
        BackupSettingsScreen(
            onBack = { navigator.goBack() },
            backupManager = backupManager,
            onBackupFileLoaded = { backup ->
                navigator.navigate(Screen.ImportPreview(backup))
            }
        )
    }
    entry<Screen.ImportPreview> { key ->
        val importSuccessMsg = stringResource(R.string.import_success)
        val importFailedMsg = stringResource(R.string.import_failed)
        ImportPreviewScreen(
            backupData = key.backup,
            onBack = { navigator.goBack() },
            onConfirmRestore = { selection ->
                scope.launch {
                    val result = backupManager.restoreBackup(key.backup, selection)
                    if (result.isSuccess) {
                        Toast.makeText(context, importSuccessMsg, Toast.LENGTH_LONG).show()
                        navigator.navigate(Screen.Home)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: ""
                        Toast.makeText(context, importFailedMsg.format(error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    entry<Screen.IslandSettings> {
        IslandSettingsScreen(onBack = { navigator.goBack() })
    }
}
