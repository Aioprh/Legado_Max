package io.legado.app.help.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GreetingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun greet(): String {
        return "Hello from Hilt! 🎉"
    }
}
