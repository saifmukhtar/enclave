package com.enclave.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.enclave.app.MainActivity
import com.enclave.app.R
import com.enclave.app.data.local.EnclaveDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompanionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            try {
                val db = EnclaveDatabase.getInstance(context)
                val partner = db.userProfileDao().getPartnerProfile().first()
                
                val name = partner?.displayName?.ifBlank { "Partner" } ?: "Partner"
                val bio = partner?.bio ?: ""
                
                // Extract emoji from bio
                val MOOD_EMOJIS = listOf("❤️","😊","🥰","😴","🔥","✨","🎵","📚","🍿","🌙","💪","🤫","💋","🥺","😂")
                var mood = "❤️"
                for (emoji in MOOD_EMOJIS) {
                    if (bio.startsWith(emoji)) {
                        mood = emoji
                        break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, name, mood)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        name: String,
        mood: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_companion_status)
        views.setTextViewText(R.id.widget_name, name)
        views.setTextViewText(R.id.widget_mood, mood)
        
        // On click, launch MainActivity
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_mood, pendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
