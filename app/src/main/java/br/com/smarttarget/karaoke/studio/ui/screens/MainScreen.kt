package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
  val currentTab by viewModel.currentTab.collectAsState()
  val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("main_navigation_bar")
      ) {
        NavigationBarItem(
          selected = currentTab == "search",
          onClick = { viewModel.setTab("search") },
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_search),
              contentDescription = "Buscar URL",
              tint = if (currentTab == "search") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          label = {
            Text(
              text = "Buscar URL",
              fontWeight = if (currentTab == "search") FontWeight.Bold else FontWeight.Normal
            )
          },
          modifier = Modifier.testTag("nav_tab_search")
        )

        NavigationBarItem(
          selected = currentTab == "browser",
          onClick = { viewModel.setTab("browser") },
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_youtube),
              contentDescription = "YouTube",
              tint = if (currentTab == "browser") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          label = {
            Text(
              text = "Navegar",
              fontWeight = if (currentTab == "browser") FontWeight.Bold else FontWeight.Normal
            )
          },
          modifier = Modifier.testTag("nav_tab_browser")
        )

        NavigationBarItem(
          selected = currentTab == "queue",
          onClick = { viewModel.setTab("queue") },
          icon = {
            BadgedBox(
              badge = {
                if (activeDownloadsCount > 0) {
                  Badge { Text("$activeDownloadsCount") }
                }
              }
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_download_queue),
                contentDescription = "Downloads",
                tint = if (currentTab == "queue") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          label = {
            Text(
              text = "Downloads",
              fontWeight = if (currentTab == "queue") FontWeight.Bold else FontWeight.Normal
            )
          },
          modifier = Modifier.testTag("nav_tab_queue")
        )

        NavigationBarItem(
          selected = currentTab == "settings",
          onClick = { viewModel.setTab("settings") },
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_settings),
              contentDescription = "Configurações",
              tint = if (currentTab == "settings") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          label = {
            Text(
              text = "Ajustes",
              fontWeight = if (currentTab == "settings") FontWeight.Bold else FontWeight.Normal
            )
          },
          modifier = Modifier.testTag("nav_tab_settings")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
        when (tab) {
          "search" -> SearchScreen(viewModel = viewModel)
          "browser" -> YouTubeBrowserScreen(viewModel = viewModel)
          "queue" -> DownloadQueueScreen(viewModel = viewModel)
          "settings" -> SettingsScreen(viewModel = viewModel)
        }
      }

      // Modals
      FormatSelectionBottomSheet(viewModel = viewModel)
      KaraokePlayerBottomSheet(viewModel = viewModel)
    }
  }
}
