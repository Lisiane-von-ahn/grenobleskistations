package fr.grenobleski.nativeapp.data.session

import android.content.Context
import fr.grenobleski.nativeapp.data.model.UserSession

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("grenobleski_native_session", Context.MODE_PRIVATE)

    fun save(session: UserSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putInt(KEY_USER_ID, session.userId)
            .apply()
    }

    fun load(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null).orEmpty()
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null).orEmpty()
        val userId = prefs.getInt(KEY_USER_ID, 0)
        return UserSession(token = token, email = email, displayName = displayName, userId = userId)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_USER_ID = "user_id"
    }
}
