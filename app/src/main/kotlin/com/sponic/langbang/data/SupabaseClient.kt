package com.sponic.langbang.data

import com.sponic.langbang.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Shared Supabase client wired against the alpacapps project
 * (https://aphrrfprbixmhissnjfn.supabase.co). The `langbang` schema
 * keeps this app's tables isolated from the alpacapps `public` schema —
 * see `supabase/migrations/20260520_langbang_schema.sql` in the
 * alpacapps repo (github.com/rsonnad/alpacapps). The schema must also
 * be added to "Exposed schemas" in Supabase Studio for PostgREST to
 * route requests here.
 */
object SupabaseClientHolder {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest) {
                defaultSchema = "langbang"
            }
        }
    }
}
