package app.getkosha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.R
import app.getkosha.design.Tab
import app.getkosha.design.TabBar

enum class Main { Overview, Activity, Recurring, Accounts }

/** The signed-in shell: four tabs around the Add button. Each tab is a placeholder until its milestone. */
@Composable
fun KoshaApp() {
    var tab by rememberSaveable { mutableStateOf(Main.Overview) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(
        Tab(Main.Overview, stringResource(R.string.shell_nav_overview), "chart-pie-slice"),
        Tab(Main.Activity, stringResource(R.string.shell_nav_activity), "list-bullets"),
        Tab(Main.Recurring, stringResource(R.string.shell_nav_recurring), "arrows-clockwise"),
        Tab(Main.Accounts, stringResource(R.string.shell_nav_accounts), "bank"),
    )
    Box(Modifier.fillMaxSize().background(KoshaTheme.colors.bg)) {
        if (adding) {
            BackHandler { adding = false }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton("x", stringResource(R.string.common_close), { adding = false })
                }
                PageTitle(stringResource(R.string.add_heading))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    PageTitle(tabs.first { it.value == tab }.label)
                }
                TabBar(tabs, tab, { tab = it }, stringResource(R.string.add_heading), { adding = true })
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    KText(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).semantics { heading() },
        style = KoshaTheme.type.page,
    )
}
