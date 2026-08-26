package com.odago.travollio.data

/**
 * Offline currency conversion, shared by the manual budget entry and (optionally)
 * the agent. Rates are an approximate static snapshot in ILS-equivalents; live
 * refresh is future work. Values are "1 unit = X ILS". Unsupported pairs → null.
 */
object Fx {

    /** 1 unit of [from] → [to]. Returns null if either currency is unknown. */
    fun convert(amount: Double, from: String, to: String): Double? {
        val f = from.trim().uppercase()
        val t = to.trim().uppercase()
        if (amount.isNaN() || amount.isInfinite()) return null
        if (f == t) return amount
        val fr = toIls[f] ?: return null
        val tr = toIls[t] ?: return null
        return amount * (fr / tr)
    }

    /** Convert a local amount into the trip's home currency, if a rate exists. */
    fun toHome(amountLocal: Double, localCcy: String?, homeCcy: String?): Double? =
        convert(amountLocal, localCcy ?: "", homeCcy ?: "")

    private val toIls: Map<String, Double> = mapOf(
        "ILS" to 1.0, "USD" to 3.73, "EUR" to 4.02, "GBP" to 4.66, "CHF" to 4.16,
        "JPY" to 0.0256, "KRW" to 0.0027, "CNY" to 0.516, "THB" to 0.104, "TWD" to 0.114,
        "VND" to 0.00015, "MYR" to 0.80, "SGD" to 2.76, "HKD" to 0.477, "AUD" to 2.45,
        "CAD" to 2.72, "NZD" to 2.22, "AED" to 1.015, "SAR" to 0.994, "QAR" to 1.02,
        "INR" to 0.0447, "IDR" to 0.00024, "PHP" to 0.064, "MXN" to 0.196, "BRL" to 0.68,
        "TRY" to 0.11, "PLN" to 0.94, "CZK" to 0.16, "HUF" to 0.010, "SEK" to 0.36,
        "NOK" to 0.36, "DKK" to 0.54, "ZAR" to 0.20, "EGP" to 0.12, "MAD" to 0.38,
    )
}
