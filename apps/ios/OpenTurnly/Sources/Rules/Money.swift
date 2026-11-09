import Foundation

/// Amounts travel as integers in the currency's minor unit.
///
/// The number of decimals comes from a fixed table shared with the server and the
/// web, not from the platform: ICU versions disagree (COP is 2 in ISO 4217 but 0
/// here), and a stored price must mean the same amount everywhere.
enum Money {
    private static let zeroDecimals: Set<String> = [
        "BIF", "CLP", "COP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
    ]
    private static let threeDecimals: Set<String> = ["BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND"]

    /// The locale each currency's customers expect it formatted in ("$ 25.000" for COP).
    private static let locales: [String: String] = [
        "ARS": "es_AR", "BOB": "es_BO", "BRL": "pt_BR", "CLP": "es_CL", "COP": "es_CO", "CRC": "es_CR",
        "DOP": "es_DO", "EUR": "es_ES", "GTQ": "es_GT", "MXN": "es_MX", "PEN": "es_PE", "PYG": "es_PY",
        "USD": "en_US", "UYU": "es_UY",
    ]

    static func digits(_ currency: String) -> Int {
        let code = currency.uppercased()
        if zeroDecimals.contains(code) { return 0 }
        if threeDecimals.contains(code) { return 3 }
        return 2
    }

    private static func scale(_ currency: String) -> Decimal {
        var value: Decimal = 1
        for _ in 0..<digits(currency) { value *= 10 }
        return value
    }

    static func fromMinor(_ amount: Int, currency: String) -> Decimal {
        Decimal(amount) / scale(currency)
    }

    static func toMinor(_ amount: Decimal, currency: String) -> Int {
        var scaled = amount * scale(currency)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return NSDecimalNumber(decimal: rounded).intValue
    }

    /// 35000 COP → "$ 35.000"; 2550 USD → "$25.50". Whole amounts drop the decimals.
    static func format(_ amount: Int, currency: String) -> String {
        let code = currency.uppercased()
        let value = fromMinor(amount, currency: code)
        let whole = amount % NSDecimalNumber(decimal: scale(code)).intValue == 0
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: locales[code] ?? "en")
        formatter.currencyCode = code
        formatter.minimumFractionDigits = whole ? 0 : digits(code)
        formatter.maximumFractionDigits = whole ? 0 : digits(code)
        return formatter.string(from: NSDecimalNumber(decimal: value)) ?? "\(code) \(value)"
    }

    /// The currency's symbol as its own locale writes it ("$", "€").
    static func symbol(_ currency: String) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: locales[currency.uppercased()] ?? "en")
        formatter.currencyCode = currency.uppercased()
        return formatter.currencySymbol ?? currency
    }

    /// What someone typed, in major units, as minor units; nil when it is not a number.
    ///
    /// People type thousands separators ("35.000") and either decimal mark, so the
    /// last "." or "," only counts as the decimal mark when the currency has
    /// decimals and at most that many digits follow it.
    static func parse(_ text: String, currency: String) -> Int? {
        let cleaned = text.filter { $0.isNumber || $0 == "." || $0 == "," }
        guard cleaned.contains(where: \.isNumber) else { return nil }
        let places = digits(currency)
        var integer = cleaned
        var fraction = ""
        if places > 0, let mark = cleaned.lastIndex(where: { $0 == "." || $0 == "," }) {
            let tail = cleaned[cleaned.index(after: mark)...]
            if !tail.isEmpty, tail.count <= places, tail.allSatisfy(\.isNumber) {
                integer = String(cleaned[..<mark])
                fraction = String(tail)
            } else if tail.isEmpty {
                integer = String(cleaned[..<mark])
            }
        }
        let integerDigits = integer.filter(\.isNumber)
        let padded = fraction.padding(toLength: places, withPad: "0", startingAt: 0)
        guard let value = Int((integerDigits.isEmpty ? "0" : integerDigits) + padded) else { return nil }
        return value
    }

    /// An amount ready to edit: digits with the currency's own grouping, no symbol.
    static func editable(_ amount: Int, currency: String) -> String {
        let code = currency.uppercased()
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: locales[code] ?? "en")
        let whole = amount % NSDecimalNumber(decimal: scale(code)).intValue == 0
        formatter.minimumFractionDigits = whole ? 0 : digits(code)
        formatter.maximumFractionDigits = digits(code)
        return formatter.string(from: NSDecimalNumber(decimal: fromMinor(amount, currency: code))) ?? ""
    }
}
