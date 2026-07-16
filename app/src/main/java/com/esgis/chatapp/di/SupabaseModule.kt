package com.esgis.chatapp.di

import com.esgis.chatapp.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Client Supabase singleton.
 *
 * Les clés sont lues depuis local.properties via BuildConfig (voir app/build.gradle.kts) :
 *   SUPABASE_URL=...
 *   SUPABASE_ANON_KEY=...
 *
 * Tant qu'elles ne sont pas renseignées, un placeholder valide est utilisé pour que
 * l'app démarre quand même (seuls les appels réseau échoueront).
 */
object SupabaseModule {

    private val supabaseUrl =
        BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
    private val supabaseKey =
        BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-anon-key" }

    /** true si les vraies clés Supabase ont été renseignées. */
    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
