package app.rentreclaimer.solana

import java.io.ByteArrayOutputStream

/**
 * Builds unsigned legacy Solana transactions for closing empty SPL Token accounts.
 *
 * Each closed account returns its rent-exempt lamports to the owner. The transaction
 * carries a 64-byte zero placeholder signature which the wallet replaces during
 * Mobile Wallet Adapter signAndSendTransactions.
 *
 * Message layout (legacy):
 *   header: [numRequiredSignatures=1, numReadonlySigned=0, numReadonlyUnsigned=1]
 *   account keys: [owner(signer,writable), ...tokenAccounts(writable), tokenProgram(readonly)]
 *   recent blockhash (32 bytes)
 *   instructions: one SPL Token CloseAccount (id=9) per token account:
 *     accounts=[tokenAccount, destination=owner, authority=owner], data=[9]
 */
object TransactionBuilder {
    const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private const val CLOSE_ACCOUNT_IX: Byte = 9

    data class CloseTarget(val tokenAccount: ByteArray) {
        init { require(tokenAccount.size == 32) { "tokenAccount must be 32 bytes" } }
    }

    fun buildCloseAccountsTx(
        owner: ByteArray,
        targets: List<CloseTarget>,
        recentBlockhash: ByteArray,
    ): ByteArray {
        require(owner.size == 32) { "owner must be 32 bytes" }
        require(recentBlockhash.size == 32) { "blockhash must be 32 bytes" }
        require(targets.isNotEmpty()) { "at least one account to close" }
        val tokenProgram = Base58.decode(TOKEN_PROGRAM_ID)

        // Account keys: owner first (signer), then writable token accounts, then program (readonly)
        val keys = ArrayList<ByteArray>(targets.size + 2).apply {
            add(owner)
            targets.forEach { add(it.tokenAccount) }
            add(tokenProgram)
        }
        val programIndex = keys.size - 1

        val msg = ByteArrayOutputStream()
        msg.write(1) // numRequiredSignatures
        msg.write(0) // numReadonlySignedAccounts
        msg.write(1) // numReadonlyUnsignedAccounts
        msg.writeCompactU16(keys.size)
        keys.forEach { msg.write(it) }
        msg.write(recentBlockhash)
        msg.writeCompactU16(targets.size)
        targets.forEachIndexed { i, _ ->
            val acctIndex = 1 + i
            msg.write(programIndex)
            msg.writeCompactU16(3)
            msg.write(acctIndex) // token account to close
            msg.write(0)         // destination = owner (rent goes back to you)
            msg.write(0)         // authority = owner
            msg.writeCompactU16(1)
            msg.write(CLOSE_ACCOUNT_IX.toInt())
        }

        // Unsigned transaction: [sigCount=1][64 zero bytes][message]
        return ByteArrayOutputStream().apply {
            writeCompactU16(1)
            write(ByteArray(64))
            write(msg.toByteArray())
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeCompactU16(value: Int) {
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            write(b)
        } while (v != 0)
    }
}
