package io.github.temqua.timeclockwizardclient

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Store(private val context: Context) {


    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        val EMAIL_KEY = stringPreferencesKey("email")
        val SUBDOMAIN_KEY = stringPreferencesKey("subdomain")
    }

    val emailFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[EMAIL_KEY] ?: ""
        }

    val subdomainFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SUBDOMAIN_KEY] ?: ""
        }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[EMAIL_KEY] = email
        }
    }

    suspend fun saveSubdomain(subdomain: String) {
        context.dataStore.edit { preferences ->
            preferences[SUBDOMAIN_KEY] = subdomain
        }
    }


}