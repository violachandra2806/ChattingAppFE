package com.chattingapp.utils

import android.content.Context
import android.util.Log
import com.chattingapp.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.cio.CIO

object SupabaseClient {
    private var client: SupabaseClient? = null

    fun getClient(context: Context): SupabaseClient {
        if (client == null) {
            Log.d("SupabaseClient", "Creating new Supabase client...")
            Log.d("SupabaseClient", "URL: ${BuildConfig.SUPABASE_URL}")

            client = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                httpEngine = CIO.create()

                install(Postgrest)
                install(Realtime) {
                    // Tambahkan config realtime jika perlu
                }
                install(Storage)
            }

            Log.d("SupabaseClient", "✅ Supabase client created successfully")
        }
        return client!!
    }
}