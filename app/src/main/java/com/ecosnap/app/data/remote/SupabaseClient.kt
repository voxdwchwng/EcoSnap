package com.ecosnap.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseProvider {
    // Thay thế bằng thông tin thực tế từ Supabase Dashboard (Project Settings > API)
    private const val SUPABASE_URL = "https://pqbeycxoecrqgzqbhusq.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBxYmV5Y3hvZWNycWd6cWJodXNxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3Nzc5NDcsImV4cCI6MjEwNDM1Mzk0N30.VGLBrJ-XbJVoCHzwU62dMVzFoPNc68AkTCRp324Viig"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}
