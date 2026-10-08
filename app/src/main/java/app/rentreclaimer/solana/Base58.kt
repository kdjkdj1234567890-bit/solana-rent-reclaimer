package app.rentreclaimer.solana

/**
 * Base58 (Bitcoin/Solana alphabet) encode/decode.
 * Implemented locally to avoid an extra dependency.
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.also { arr ->
        ALPHABET.forEachIndexed { index, c -> arr[c.code] = index }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val input58 = ByteArray(input.size * 2)
        var length = 0
        for (b in input) {
            var carry = b.toUByte().toInt()
            var i = 0
            while (i < length || carry != 0) {
                val v = input58[i].toUByte().toInt() * 256 + carry
                input58[i] = (v % 58).toByte()
                carry = v / 58
                i++
            }
            length = i
        }
        var zeros = 0
        while (zeros < input.size && input[zeros] == 0.toByte()) zeros++
        val sb = StringBuilder()
        repeat(zeros) { sb.append('1') }
        for (i in length - 1 downTo 0) sb.append(ALPHABET[input58[i].toUByte().toInt()])
        return sb.toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid Base58 character: $c" }
            input58[i] = digit.toByte()
        }
        var zeros = 0
        while (zeros < input58.size && input58[zeros] == 0.toByte()) zeros++
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 256, 58).toByte()
            if (input58[inputStart] == 0.toByte()) inputStart++
        }
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) outputStart++
        return ByteArray(zeros + (decoded.size - outputStart)).also {
            System.arraycopy(decoded, outputStart, it, zeros, decoded.size - outputStart)
        }
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toUByte().toInt()
            val temp = remainder * divisor + digit
            number[i] = (temp / base).toByte()
            remainder = temp % base
        }
        return remainder
    }
}
