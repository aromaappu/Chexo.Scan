package com.chexo.scan

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Auth) {
            scheme = "chexoscan"
            host = "auth-callback"
        }
        install(Postgrest)
    }
}
