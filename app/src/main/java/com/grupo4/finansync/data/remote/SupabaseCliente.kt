package com.grupo4.finansync.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseCliente {


    private const val SUPABASE_URL = "https://yowbfqemcubhvmbindnr.supabase.co"


    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inlvd2JmcWVtY3ViaHZtYmluZG5yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA2MjI4NTAsImV4cCI6MjA5NjE5ODg1MH0.nymT47MGNReYZHRdYwZezDet5jRca7K0A5WsBW4HI7E"

    val cliente = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Auth)
    }
}