package com.example.fastcontacts

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("fast_contacts_prefs", Context.MODE_PRIVATE)

    fun showEmail(c: Context): Boolean = sp(c).getBoolean("show_email", true)
    fun setShowEmail(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_email", v).apply()

    fun showPhone(c: Context): Boolean = sp(c).getBoolean("show_phone", true)
    fun setShowPhone(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_phone", v).apply()

    fun alphabetIndex(c: Context): Boolean = sp(c).getBoolean("alphabet_index", true)
    fun setAlphabetIndex(c: Context, v: Boolean) = sp(c).edit().putBoolean("alphabet_index", v).apply()

    fun sortByLast(c: Context): Boolean = sp(c).getBoolean("sort_last", false)
    fun setSortByLast(c: Context, v: Boolean) = sp(c).edit().putBoolean("sort_last", v).apply()

    fun countryCode(c: Context): String = sp(c).getString("country_code", "") ?: ""
    fun setCountryCode(c: Context, v: String) = sp(c).edit().putString("country_code", v).apply()

    // 0 = system, 1 = light, 2 = dark
    fun theme(c: Context): Int = sp(c).getInt("theme", 0)
    fun setTheme(c: Context, v: Int) = sp(c).edit().putInt("theme", v).apply()

    fun applyTheme(c: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(c)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
