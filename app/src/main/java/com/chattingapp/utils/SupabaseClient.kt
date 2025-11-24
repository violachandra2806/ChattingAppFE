package com.chattingapp.utils

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://nxagvhkldfxenczfahch.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im54YWd2aGtsZGZ4ZW5jemZhaGNoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc0ODc4NTUsImV4cCI6MjA2MzA2Mzg1NX0.6fxDR2kDAzIbey_anvHyOM6FKhbcvOfPN0LMsKp2BuE"
    ) {
        install(Postgrest)
        install(Realtime)
    }
}