package app.rentreclaimer.solana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Solana JSON-RPC client (OkHttp + org.json, no extra SDK needed).
 */
class SolanaRpc(endpoint: String) {
    private val endpoint = endpoint.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class EmptyTokenAccount(
        val pubkey: String,  // base58 address of the token account
        val mint: String,    // base58 mint address
        val lamports: Long,  // rent-exempt lamports locked in the account
    )

    /** Returns SPL Token accounts owned by [ownerBase58] with zero token balance. */
    suspend fun getEmptyTokenAccounts(ownerBase58: String): List<EmptyTokenAccount> =
        withContext(Dispatchers.IO) {
            val params = JSONArray().apply {
                put(ownerBase58)
                put(JSONObject().put("programId", TransactionBuilder.TOKEN_PROGRAM_ID))
                put(JSONObject().put("encoding", "jsonParsed"))
            }
            val resp = post("getTokenAccountsByOwner", params)
            val out = mutableListOf<EmptyTokenAccount>()
            val values = resp.getJSONObject("result").getJSONArray("value")
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                val info = item.getJSONObject("account")
                    .getJSONObject("data")
                    .getJSONObject("parsed")
                    .getJSONObject("info")
                val amount = info.getJSONObject("tokenAmount").getString("amount")
                if (amount == "0") {
                    out += EmptyTokenAccount(
                        pubkey = item.getString("pubkey"),
                        mint = info.getString("mint"),
                        lamports = item.getLong("lamports"),
                    )
                }
            }
            out
        }

    suspend fun getLatestBlockhash(): String = withContext(Dispatchers.IO) {
        val params = JSONArray().put(JSONObject().put("commitment", "confirmed"))
        post("getLatestBlockhash", params)
            .getJSONObject("result").getJSONObject("value").getString("blockhash")
    }

    suspend fun getRentExemptMinimum(dataSize: Int = 165): Long = withContext(Dispatchers.IO) {
        post("getMinimumBalanceForRentExemption", JSONArray().put(dataSize))
            .getJSONObject("result").getLong("value")
    }

    private fun post(method: String, params: JSONArray): JSONObject {
        val json = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)
            .toString()
        val req = Request.Builder()
            .url(endpoint)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw IllegalStateException("Empty RPC response")
            val obj = JSONObject(text)
            if (obj.has("error")) {
                throw IllegalStateException("RPC error: ${obj.getJSONObject("error")}")
            }
            return obj
        }
    }
}
