package app.getkosha.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.getkosha.MainActivity
import app.getkosha.data.Kosha
import app.getkosha.design.R

/**
 * The home-screen quick-add widget: Expense and Income open Add on the amount, and Scan opens the receipt camera
 * when receipt scanning is on. It shows no amounts, so nothing private sits on the home screen.
 */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val receipts = Kosha.init(context).meta.value?.ai?.receipts == true
        val expense = context.getString(R.string.add_kind_expense)
        val income = context.getString(R.string.add_kind_income)
        val scan = context.getString(R.string.add_scan_receipt)
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().background(SURFACE).cornerRadius(24.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Key(context, "+ $expense", "expense", primary = true, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Key(context, "+ $income", "income", modifier = GlanceModifier.defaultWeight())
                if (receipts) {
                    Spacer(GlanceModifier.width(6.dp))
                    Key(context, scan, "scan", modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Key(context: Context, label: String, add: String, primary: Boolean = false, modifier: GlanceModifier) {
        val intent = Intent(Intent.ACTION_VIEW).setComponent(ComponentName(context, MainActivity::class.java))
            .putExtra("kosha.add", add)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Box(
            modifier.fillMaxWidth().height(52.dp).background(if (primary) INK else SOFT).cornerRadius(14.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = if (primary) ON_INK else INK, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    private companion object {
        // Kosha's surface, soft, ink and on-ink tokens (k.css), light and dark.
        val SURFACE = ColorProvider(Color(0xFFFFFFFF), Color(0xFF131315))
        val SOFT = ColorProvider(Color(0xFFF0F0ED), Color(0xFF1C1C1F))
        val INK = ColorProvider(Color(0xFF111113), Color(0xFFF3F3F4))
        val ON_INK = ColorProvider(Color(0xFFF6F6F4), Color(0xFF0B0B0C))
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
