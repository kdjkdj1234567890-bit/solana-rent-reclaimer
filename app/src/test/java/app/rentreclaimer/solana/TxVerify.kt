package app.rentreclaimer.solana

fun main() {
    var failures = 0
    fun check(name: String, cond: Boolean) {
        println((if (cond) "PASS" else "FAIL") + ": $name")
        if (!cond) failures++
    }

    // --- Base58 vectors ---
    val zeros32 = ByteArray(32)
    check("encode 32 zero bytes == 1111...1", Base58.encode(zeros32) == "11111111111111111111111111111111")
    check("decode 1111...1 == 32 zero bytes", Base58.decode("11111111111111111111111111111111").contentEquals(zeros32))

    val tokenProg = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    val tokenProgBytes = Base58.decode(tokenProg)
    check("token program decodes to 32 bytes", tokenProgBytes.size == 32)
    check("token program first bytes known", tokenProgBytes[0] == 0x06.toByte() && tokenProgBytes[1] == 0xDD.toByte())
    check("base58 roundtrip token program", Base58.encode(tokenProgBytes) == tokenProg)

    val ownerB58 = "SysvarC1ock11111111111111111111111111111111"
    val ownerBytes = Base58.decode(ownerB58)
    check("decode 44-char address == 32 bytes", ownerBytes.size == 32)
    check("base58 roundtrip address", Base58.encode(ownerBytes) == ownerB58)

    // --- TransactionBuilder ---
    val owner = ByteArray(32) { 0x01 }
    val target = ByteArray(32) { 0x02 }
    val blockhash = ByteArray(32) { 0x03 }
    val tx = TransactionBuilder.buildCloseAccountsTx(
        owner, listOf(TransactionBuilder.CloseTarget(target)), blockhash
    )
    // expected: 1 (sigcount) + 64 (placeholder sig) + message
    // message: 3 header + 1 keycount + 3*32 keys + 32 blockhash + 1 ixcount + 7/ix
    // (1 progidx + 1 acctcount + 3 accts + 1 datalen + 1 data)
    val expectedLen = 1 + 64 + (3 + 1 + 96 + 32 + 1 + 7)
    check("tx length == $expectedLen", tx.size == expectedLen)

    var o = 0
    check("sig count == 1", tx[o++] == 1.toByte())
    check("64 zero placeholder sig", tx.slice(o until o + 64).all { it == 0.toByte() }); o += 64
    check("header [1,0,1]", tx[o] == 1.toByte() && tx[o + 1] == 0.toByte() && tx[o + 2] == 1.toByte()); o += 3
    check("key count == 3", tx[o++] == 3.toByte())
    check("key[0] == owner", tx.slice(o until o + 32).toByteArray().contentEquals(owner)); o += 32
    check("key[1] == target", tx.slice(o until o + 32).toByteArray().contentEquals(target)); o += 32
    // key[2] would be second target; skip (only 1 target here) -- key[2] is token program
    check("key[2] == token program", tx.slice(o until o + 32).toByteArray().contentEquals(tokenProgBytes)); o += 32
    check("blockhash", tx.slice(o until o + 32).toByteArray().contentEquals(blockhash)); o += 32
    check("ix count == 1", tx[o++] == 1.toByte())
    check("program id index == 2", tx[o++] == 2.toByte())
    check("ix account count == 3", tx[o++] == 3.toByte())
    check("ix accounts == [1,0,0]", tx[o] == 1.toByte() && tx[o + 1] == 0.toByte() && tx[o + 2] == 0.toByte()); o += 3
    check("ix data len == 1", tx[o++] == 1.toByte())
    check("ix data == 9 (CloseAccount)", tx[o++] == 9.toByte())
    check("consumed all bytes", o == tx.size)

    // multi-account tx: 3 targets
    val tx3 = TransactionBuilder.buildCloseAccountsTx(
        owner,
        listOf(
            TransactionBuilder.CloseTarget(ByteArray(32) { 0x02 }),
            TransactionBuilder.CloseTarget(ByteArray(32) { 0x04 }),
            TransactionBuilder.CloseTarget(ByteArray(32) { 0x05 }),
        ),
        blockhash
    )
    // keys = owner + 3 targets + program = 5; ixs = 3 (7 bytes each)
    val expectedLen3 = 1 + 64 + (3 + 1 + 5 * 32 + 32 + 1 + 3 * 7)
    check("3-target tx length == $expectedLen3", tx3.size == expectedLen3)

    println(if (failures == 0) "ALL TESTS PASSED" else "$failures TESTS FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
