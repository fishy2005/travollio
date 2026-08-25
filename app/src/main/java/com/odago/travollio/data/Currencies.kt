package com.odago.travollio.data

/** A selectable currency with a stable ISO code and a display symbol. */
data class Currency(val code: String, val name: String, val symbol: String)

/**
 * Currencies available for selection (home + trip-local). A curated, order-stable
 * list of the most common ISO 4217 currencies. Choosing a currency is always from
 * this list (picker) — never free text — so a valid code is guaranteed.
 */
object Currencies {

    private val list = listOf(
        Currency("USD", "US Dollar", "$"),
        Currency("EUR", "Euro", "€"),
        Currency("ILS", "Israeli Shekel", "₪"),
        Currency("GBP", "British Pound", "£"),
        Currency("JPY", "Japanese Yen", "¥"),
        Currency("CNY", "Chinese Yuan", "¥"),
        Currency("KRW", "South Korean Won", "₩"),
        Currency("THB", "Thai Baht", "฿"),
        Currency("SGD", "Singapore Dollar", "S$"),
        Currency("HKD", "Hong Kong Dollar", "HK$"),
        Currency("AED", "UAE Dirham", "د.إ"),
        Currency("AUD", "Australian Dollar", "A$"),
        Currency("CAD", "Canadian Dollar", "C$"),
        Currency("CHF", "Swiss Franc", "CHF"),
        Currency("NZD", "New Zealand Dollar", "NZ$"),
        Currency("TRY", "Turkish Lira", "₺"),
        Currency("INR", "Indian Rupee", "₹"),
        Currency("IDR", "Indonesian Rupiah", "Rp"),
        Currency("MYR", "Malaysian Ringgit", "RM"),
        Currency("PHP", "Philippine Peso", "₱"),
        Currency("VND", "Vietnamese Dong", "₫"),
        Currency("TWD", "New Taiwan Dollar", "NT$"),
        Currency("MXN", "Mexican Peso", "Mex$"),
        Currency("BRL", "Brazilian Real", "R$"),
        Currency("ARS", "Argentine Peso", "AR$"),
        Currency("CLP", "Chilean Peso", "CLP$"),
        Currency("COP", "Colombian Peso", "CO$"),
        Currency("PEN", "Peruvian Sol", "S/"),
        Currency("ZAR", "South African Rand", "R"),
        Currency("EGP", "Egyptian Pound", "E£"),
        Currency("MAD", "Moroccan Dirham", "د.م."),
        Currency("TND", "Tunisian Dinar", "د.ت"),
        Currency("JOD", "Jordanian Dinar", "JD"),
        Currency("SAR", "Saudi Riyal", "﷼"),
        Currency("QAR", "Qatari Riyal", "﷼"),
        Currency("KWD", "Kuwaiti Dinar", "KD"),
        Currency("BHD", "Bahraini Dinar", "BD"),
        Currency("OMR", "Omani Rial", "﷼"),
        Currency("ISK", "Icelandic Króna", "kr"),
        Currency("NOK", "Norwegian Krone", "kr"),
        Currency("SEK", "Swedish Krona", "kr"),
        Currency("DKK", "Danish Krone", "kr"),
        Currency("PLN", "Polish Złoty", "zł"),
        Currency("CZK", "Czech Koruna", "Kč"),
        Currency("HUF", "Hungarian Forint", "Ft"),
        Currency("RON", "Romanian Leu", "lei"),
        Currency("BGN", "Bulgarian Lev", "лв"),
        Currency("HRK", "Croatian Kuna", "kn"),
        Currency("UAH", "Ukrainian Hryvnia", "₴"),
        Currency("RUB", "Russian Ruble", "₽"),
        Currency("GEL", "Georgian Lari", "₾"),
        Currency("AZN", "Azerbaijani Manat", "₼"),
        Currency("KZT", "Kazakhstani Tenge", "₸"),
        Currency("GHS", "Ghanaian Cedi", "₵"),
        Currency("NGN", "Nigerian Naira", "₦"),
        Currency("KES", "Kenyan Shilling", "KSh"),
        Currency("ETB", "Ethiopian Birr", "Br"),
        Currency("LKR", "Sri Lankan Rupee", "Rs"),
        Currency("NPR", "Nepalese Rupee", "₨"),
        Currency("PKR", "Pakistani Rupee", "₨"),
        Currency("BDT", "Bangladeshi Taka", "৳"),
        Currency("MMK", "Myanmar Kyat", "K"),
        Currency("KHR", "Cambodian Riel", "៛"),
        Currency("LAK", "Lao Kip", "₭"),
        Currency("MNT", "Mongolian Tögrög", "₮"),
        Currency("FJD", "Fijian Dollar", "FJ$"),
        Currency("XPF", "CFP Franc", "₣"),
        Currency("BTC", "Bitcoin", "₿"),
    )

    val all: List<Currency> get() = list

    /** Find a currency by ISO code (case-insensitive). Falls back to a synthetic entry. */
    fun byCode(code: String?): Currency {
        val c = code?.trim()?.uppercase().orEmpty()
        return list.firstOrNull { it.code == c } ?: Currency(if (c.isBlank()) "USD" else c, c, "")
    }

    fun symbol(code: String?): String = byCode(code).symbol

    /** Currencies commonly used as a "home/base" currency. */
    val homeDefaults: List<Currency> = listOf(
        byCode("ILS"), byCode("USD"), byCode("EUR"), byCode("GBP"), byCode("JPY")
    )
}
