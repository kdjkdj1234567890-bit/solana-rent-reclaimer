package app.rentreclaimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private val controller = WalletController()
    private lateinit var root: LinearLayout

    // colors
    private val bg = Color.parseColor("#0A0A12")
    private val card = Color.parseColor("#14141F")
    private val border = Color.parseColor("#26263A")
    private val purple = Color.parseColor("#9945FF")
    private val green = Color.parseColor("#14F195")
    private val textPrimary = Color.parseColor("#F2F2F7")
    private val textSecondary = Color.parseColor("#9A9AB0")
    private val dim = Color.parseColor("#6E6E85")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        setContentView(scroll)

        controller.onStateChanged = { render() }
        render()
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun shortAddr(addr: String): String =
        if (addr.length > 12) "${addr.take(6)}…${addr.takeLast(4)}" else addr

    private fun render() {
        root.removeAllViews()
        when (val s = controller.state) {
            is UiState.Disconnected -> renderConnect()
            is UiState.Connected -> renderConnected(s)
            is UiState.Scanning -> renderLoading("Scanning your wallet…", "Reading token accounts via Solana RPC")
            is UiState.Result -> renderResult(s)
            is UiState.Signing -> renderLoading(
                "Waiting for wallet signature…",
                "Approve the transaction in your wallet to reclaim ${"%.4f".format(s.totalSol)} SOL"
            )
            is UiState.Success -> renderSuccess(s)
            is UiState.Error -> renderError(s.message)
        }
    }

    // ---------- view helpers ----------

    private fun textView(text: String, sizeSp: Int, color: Int, bold: Boolean = false,
                         gravity: Int = Gravity.START): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.gravity = gravity
        }

    private fun primaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isEnabled = enabled
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(if (enabled) purple else border)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            }
        }

    private fun linkButton(label: String, onClick: () -> Unit): TextView =
        textView(label, 14, textSecondary, gravity = Gravity.CENTER).apply {
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onClick() }
        }

    private fun spacer(hDp: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(hDp))
    }

    private fun demoBadge(): TextView =
        textView("DEMO MODE", 11, bg, bold = true).apply {
            setBackgroundColor(green)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

    inner class LogoView(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = width / 2f
            p.color = purple; canvas.drawCircle(cx, cy, r, p)
            p.color = green; canvas.drawCircle(cx, cy, r * 0.62f, p)
            p.color = bg; canvas.drawCircle(cx, cy, r * 0.34f, p)
        }
    }

    private fun logo(sizeDp: Int): View = LogoView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    // ---------- screens ----------

    private fun renderConnect() {
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(spacer(40))
        root.addView(logo(88))
        root.addView(spacer(20))
        root.addView(textView("Rent Reclaimer", 30, textPrimary, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(8))
        root.addView(textView("Empty token accounts lock SOL rent.\nScan your wallet and reclaim it in one tap.",
            15, textSecondary, gravity = Gravity.CENTER))
        root.addView(spacer(24))

        // cluster selector
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        Cluster.entries.forEach { c ->
            val selected = controller.cluster == c
            val tv = textView(c.label, 14, if (selected) bg else textSecondary, bold = selected).apply {
                setBackgroundColor(if (selected) green else card)
                setPadding(dp(24), dp(10), dp(24), dp(10))
                setOnClickListener { controller.setCluster(c) }
            }
            row.addView(tv)
            val sp = Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
            }
            row.addView(sp)
        }
        root.addView(row)
        root.addView(spacer(12))

        root.addView(primaryButton("Connect Wallet") { controller.connect(this) })
        root.addView(linkButton("Try demo mode (no wallet needed)") { controller.enableDemoMode() })
        root.addView(spacer(24))
        root.addView(textView("Powered by Solana Mobile Wallet Adapter", 12, dim, gravity = Gravity.CENTER))
    }

    private fun renderConnected(s: UiState.Connected) {
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(spacer(60))
        root.addView(textView("✓", 56, green, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(8))
        root.addView(textView("Wallet connected", 22, textPrimary, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(8))
        root.addView(textView(shortAddr(s.pubkeyBase58), 15, textSecondary, gravity = Gravity.CENTER))
        root.addView(textView("${s.cluster.label} · Mobile Wallet Adapter session", 13, dim, gravity = Gravity.CENTER))
        root.addView(spacer(24))
        root.addView(primaryButton("Scan for empty token accounts") { controller.scan() })
        root.addView(linkButton("Disconnect") { controller.disconnect() })
    }

    private fun renderLoading(title: String, subtitle: String) {
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(spacer(120))
        val pb = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        root.addView(pb)
        root.addView(spacer(16))
        root.addView(textView(title, 18, textPrimary, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(8))
        root.addView(textView(subtitle, 14, textSecondary, gravity = Gravity.CENTER))
    }

    private fun renderResult(s: UiState.Result) {
        root.gravity = Gravity.NO_GRAVITY
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        titleRow.addView(textView("Reclaimable rent", 22, textPrimary, bold = true))
        if (s.demoMode) {
            val sp = Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(1))
            }
            titleRow.addView(sp)
            titleRow.addView(demoBadge())
        }
        root.addView(titleRow)
        root.addView(spacer(4))
        root.addView(textView("${s.accounts.size} empty token account(s) found", 14, textSecondary))
        root.addView(spacer(12))

        // total card
        val totalCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        totalCard.addView(textView("Total reclaimable", 13, textSecondary, gravity = Gravity.CENTER))
        totalCard.addView(textView("%.4f SOL".format(s.totalSol), 34, green, bold = true, gravity = Gravity.CENTER))
        totalCard.addView(textView("${s.selected.size} account(s) selected", 13, textSecondary, gravity = Gravity.CENTER))
        root.addView(totalCard)
        root.addView(spacer(12))

        if (s.accounts.isEmpty()) {
            root.addView(textView("No empty token accounts found.\nYour wallet is already rent-efficient. 🎉",
                15, textSecondary, gravity = Gravity.CENTER))
            root.addView(spacer(16))
        } else {
            s.accounts.forEach { acc ->
                root.addView(accountRow(acc))
                root.addView(spacer(8))
            }
        }

        root.addView(primaryButton(
            if (s.selected.isEmpty()) "Select accounts to reclaim"
            else "Reclaim %.4f SOL".format(s.totalSol),
            enabled = s.selected.isNotEmpty()
        ) { controller.reclaim(this) })
        val back = linkButton("Back") { controller.backToConnected() }
        (back.layoutParams as? LinearLayout.LayoutParams)?.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(back)
    }

    private fun accountRow(acc: ScannedAccount): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { controller.toggleAccount(acc.pubkey) }
        }
        val cb = CheckBox(this).apply {
            isChecked = acc.selected
            setOnCheckedChangeListener { _, _ -> controller.toggleAccount(acc.pubkey) }
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
        }
        row.addView(cb)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(textView(shortAddr(acc.pubkey), 15, textPrimary, bold = true))
        col.addView(textView("Mint ${shortAddr(acc.mint)}", 12, textSecondary))
        row.addView(col)
        row.addView(textView("%.4f SOL".format(acc.lamports / 1_000_000_000.0), 14, green, bold = true))
        return row
    }

    private fun renderSuccess(s: UiState.Success) {
        root.gravity = Gravity.CENTER_HORIZONTAL
        if (s.demoMode) root.addView(demoBadge())
        root.addView(spacer(40))
        root.addView(textView("✓", 72, green, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(12))
        root.addView(textView("Reclaimed!", 26, textPrimary, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(8))
        root.addView(textView("%.4f SOL from %d account(s)\nreturned to your wallet.".format(
            s.solReclaimed, s.accountsClosed), 15, textSecondary, gravity = Gravity.CENTER))
        root.addView(spacer(16))
        val sigCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(card)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        sigCard.addView(textView("Transaction signature", 12, textSecondary))
        sigCard.addView(textView(shortAddr(s.signature), 14, textPrimary, bold = true))
        sigCard.addView(textView("solscan.io/tx/${s.signature}${s.cluster.explorerSuffix}", 11, purple))
        root.addView(sigCard)
        root.addView(spacer(16))
        root.addView(primaryButton("Scan again") { controller.backToConnected() })
    }

    private fun renderError(message: String) {
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(spacer(80))
        root.addView(textView("Something went wrong", 22, textPrimary, bold = true, gravity = Gravity.CENTER))
        root.addView(spacer(12))
        root.addView(textView(message, 15, textSecondary, gravity = Gravity.CENTER))
        root.addView(spacer(24))
        root.addView(primaryButton("Back") { controller.dismissError() })
    }
}
