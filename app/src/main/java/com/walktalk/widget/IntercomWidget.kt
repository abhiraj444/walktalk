package com.walktalk.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class IntercomWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎙️ WalkTalk Intercom",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Quick Call Grid
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactQuickCallButton(name = "Papa", targetId = "papa_phone", statusColor = Color(0xFF00E676))
                Spacer(modifier = GlanceModifier.width(8.dp))
                ContactQuickCallButton(name = "Mama", targetId = "mama_phone", statusColor = Color(0xFFFFD600))
                Spacer(modifier = GlanceModifier.width(8.dp))
                ContactQuickCallButton(name = "Sis", targetId = "sis_phone", statusColor = Color(0xFF00E676))
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Everyone Broadcast Button
            val everyoneKey = ActionParameters.Key<String>("target_id")
            Button(
                text = "📢 Blast to Everyone",
                onClick = actionStartActivity<WidgetTrampolineActivity>(
                    actionParametersOf(everyoneKey to "everyone")
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun ContactQuickCallButton(name: String, targetId: String, statusColor: Color) {
        val targetKey = ActionParameters.Key<String>("target_id")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.padding(4.dp)
        ) {
            Button(
                text = "📞 $name",
                onClick = actionStartActivity<WidgetTrampolineActivity>(
                    actionParametersOf(targetKey to targetId)
                ),
                modifier = GlanceModifier.background(Color(0xFF2C3E50))
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(statusColor)
            ) {}
        }
    }
}
