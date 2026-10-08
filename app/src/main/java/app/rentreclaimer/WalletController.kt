package app.rentreclaimer

import androidx.activity.ComponentActivity
import android.net.Uri
import app.rentreclaimer.solana.Base58
import app.rentreclaimer.solana.SolanaRpc
import app.rentreclaimer.solana.TransactionBuilder
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.Blockchain
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Cluster(val label: String, val rpcUrl: String, val explorerSuffix: String) {
    MAINNET("Mainnet", "https://api.mainnet-beta.solana.com", ""),
    DEVNET("Devnet", "https://api.devnet.solana.com", "?cluster=devnet");

    fun toBlockchain(): Blockchain = when (this) {
        MAINNET -> Solana.Mainnet
        DEVNET -> Solana.Devnet
    }
}

data class ScannedAccount(
    val pubkey: String,
    val mint: String,
    val lamports: Long,
    val selected: Boolean = true,
)

sealed interface UiState {
    data object Disconnected : UiState
    data class Connected(val pubkeyBase58: String, val cluster: Cluster) : UiState
    data class Scanning(val pubkeyBase58: String, val cluster: Cluster) : UiState
    data class Result(
        val pubkeyBase58: String,
        val cluster: Cluster,
        val accounts: List<ScannedAccount>,
        val demoMode: Boolean,
    ) : UiState {
        val selected: List<ScannedAccount> get() = accounts.filter { it.selected }
        val totalLamports: Long get() = selected.sumOf { it.lamports }
        val totalSol: Double get() = totalLamports / 1_000_000_000.0
    }
    data class Signing(val totalSol: Double) : UiState
    data class Success(
        val signature: String,
        val solReclaimed: Double,
        val accountsClosed: Int,
        val cluster: Cluster,
        val demoMode: Boolean,
    ) : UiState
    data class Error(val message: String) : UiState
}

/** MWA session + scan/reclaim orchestration. Framework-only (no lifecycle deps). */
class WalletController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var cluster: Cluster = Cluster.MAINNET
        private set
    var state: UiState = UiState.Disconnected
        private set

    var onStateChanged: (() -> Unit)? = null

    private val walletAdapter = MobileWalletAdapter(
        ConnectionIdentity(
            identityUri = Uri.parse("https://rentreclaimer.app"),
            iconUri = Uri.parse("/favicon.ico"),
            identityName = "Rent Reclaimer",
        )
    )

    private fun update(s: UiState) {
        state = s
        onStateChanged?.invoke()
    }

    fun setCluster(c: Cluster) {
        cluster = c
        walletAdapter.blockchain = c.toBlockchain()
        onStateChanged?.invoke()
    }

    fun connect(activity: ComponentActivity) {
        scope.launch {
            walletAdapter.blockchain = cluster.toBlockchain()
            when (val r = walletAdapter.transact(ActivityResultSender(activity)) { authResult ->
                Base58.encode(authResult.accounts.first().publicKey)
            }) {
                is TransactionResult.Success ->
                    update(UiState.Connected(r.payload, cluster))
                is TransactionResult.Failure ->
                    update(UiState.Error("Wallet connection failed: ${r.message}"))
                is TransactionResult.NoWalletFound ->
                    update(UiState.Error("No compatible wallet found. Install a Solana mobile wallet (e.g. Phantom) and try again."))
            }
        }
    }

    /** Demo mode: simulates the full flow with mock data for presentations. */
    fun enableDemoMode() {
        val rnd = Random(42)
        val mock = (1..5).map {
            ScannedAccount(
                pubkey = Base58.encode(ByteArray(32) { rnd.nextInt(256).toByte() }),
                mint = Base58.encode(ByteArray(32) { rnd.nextInt(256).toByte() }),
                lamports = 2_039_280L,
            )
        }
        update(
            UiState.Result(
                pubkeyBase58 = Base58.encode(ByteArray(32) { rnd.nextInt(256).toByte() }),
                cluster = cluster,
                accounts = mock,
                demoMode = true,
            )
        )
    }

    fun scan() {
        val st = state as? UiState.Connected ?: return
        update(UiState.Scanning(st.pubkeyBase58, st.cluster))
        scope.launch {
            try {
                val rpc = SolanaRpc(st.cluster.rpcUrl)
                val accounts = rpc.getEmptyTokenAccounts(st.pubkeyBase58).map {
                    ScannedAccount(it.pubkey, it.mint, it.lamports)
                }
                update(UiState.Result(st.pubkeyBase58, st.cluster, accounts, demoMode = false))
            } catch (e: Exception) {
                update(UiState.Error("Scan failed: ${e.message ?: "network error"}"))
            }
        }
    }

    fun toggleAccount(pubkey: String) {
        val st = state as? UiState.Result ?: return
        update(st.copy(accounts = st.accounts.map {
            if (it.pubkey == pubkey) it.copy(selected = !it.selected) else it
        }))
    }

    fun reclaim(activity: ComponentActivity) {
        val st = state as? UiState.Result ?: return
        if (st.selected.isEmpty()) {
            update(UiState.Error("Select at least one account to reclaim."))
            return
        }
        update(UiState.Signing(st.totalSol))
        scope.launch {
            try {
                if (st.demoMode) {
                    delay(1800)
                    update(
                        UiState.Success(
                            signature = Base58.encode(Random.nextBytes(64)),
                            solReclaimed = st.totalSol,
                            accountsClosed = st.selected.size,
                            cluster = st.cluster,
                            demoMode = true,
                        )
                    )
                    return@launch
                }
                val rpc = SolanaRpc(st.cluster.rpcUrl)
                val blockhash = Base58.decode(rpc.getLatestBlockhash())
                val owner = Base58.decode(st.pubkeyBase58)
                val tx = TransactionBuilder.buildCloseAccountsTx(
                    owner = owner,
                    targets = st.selected.map { TransactionBuilder.CloseTarget(Base58.decode(it.pubkey)) },
                    recentBlockhash = blockhash,
                )
                when (val r = walletAdapter.transact(ActivityResultSender(activity)) {
                    signAndSendTransactions(arrayOf(tx))
                }) {
                    is TransactionResult.Success -> {
                        val sig = Base58.encode(r.payload.signatures.first())
                        update(UiState.Success(sig, st.totalSol, st.selected.size, st.cluster, false))
                    }
                    is TransactionResult.Failure ->
                        update(UiState.Error("Transaction rejected: ${r.message}"))
                    is TransactionResult.NoWalletFound ->
                        update(UiState.Error("Wallet session lost. Please reconnect."))
                }
            } catch (e: Exception) {
                update(UiState.Error("Reclaim failed: ${e.message ?: "unknown error"}"))
            }
        }
    }

    fun backToConnected() {
        val pubkey = when (val s = state) {
            is UiState.Connected -> s.pubkeyBase58
            is UiState.Result -> s.pubkeyBase58
            is UiState.Scanning -> s.pubkeyBase58
            else -> null
        }
        update(pubkey?.let { UiState.Connected(it, cluster) } ?: UiState.Disconnected)
    }

    fun disconnect() = update(UiState.Disconnected)
    fun dismissError() = backToConnected()
    fun destroy() = scope.cancel()
}
