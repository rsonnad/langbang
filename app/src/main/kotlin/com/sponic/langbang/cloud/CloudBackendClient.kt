package com.sponic.langbang.cloud

import com.sponic.langbang.data.LbJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class CloudBackendClient(
    private val apiBase: String
) {
    private val json = LbJson.lenient

    suspend fun fetchInstances(): Result<List<CloudInstanceSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("/v1/instances")
            json.decodeFromString(CloudInstancesResponse.serializer(), body).instances
        }
    }

    suspend fun fetchBootstrap(instanceId: String): Result<CloudBootstrap> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(instanceId, "UTF-8")
            json.decodeFromString(CloudBootstrap.serializer(), get("/v1/instances/$encoded/bootstrap"))
        }
    }

    private fun get(path: String): String {
        val endpoint = "${apiBase.trimEnd('/')}$path"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Cloudflare API HTTP $code: ${err.take(180)}")
        } finally {
            conn.disconnect()
        }
    }
}
