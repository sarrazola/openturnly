import Foundation

/// Small text rules shared by lists: search, initials, colours per person.
enum TextRules {
    /// Lower case without accents, so "andres" finds "Andrés".
    static func folded(_ value: String) -> String {
        value.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
    }

    /// Every word of the query appears somewhere in the fields.
    static func matches(_ query: String, _ fields: [String?]) -> Bool {
        let words = folded(query).split(whereSeparator: \.isWhitespace)
        guard !words.isEmpty else { return true }
        let haystack = folded(fields.compactMap { $0 }.joined(separator: " "))
        let digits = haystack.filter(\.isNumber)
        return words.allSatisfy { word in
            if haystack.contains(word) { return true }
            let wordDigits = word.filter(\.isNumber)
            return wordDigits.count >= 3 && wordDigits.count == word.count && digits.contains(wordDigits)
        }
    }

    /// "Ana María Pérez" → "AP"; one word → its first letter.
    static func initials(_ name: String) -> String {
        let words = name.split(whereSeparator: { $0.isWhitespace }).filter { $0.first?.isLetter ?? false }
        guard let first = words.first?.first else { return name.first.map { String($0).uppercased() } ?? "?" }
        if words.count > 1, let last = words.last?.first { return "\(first)\(last)".uppercased() }
        return String(first).uppercased()
    }

    /// The web's calendar colours, one per professional by their place in the team.
    static let professionalColors = ["#0f766e", "#0284c7", "#d97706", "#db2777", "#65a30d", "#7c3aed", "#475569"]

    static func professionalColor(_ id: String?, in ids: [String]) -> String {
        guard let id, let index = ids.firstIndex(of: id) else { return professionalColors[0] }
        return professionalColors[index % professionalColors.count]
    }

    /// A stable colour for a customer's avatar, from their name.
    static func avatarColor(_ seed: String) -> String {
        let palette = ["#0f766e", "#0284c7", "#7c3aed", "#db2777", "#d97706", "#65a30d", "#0891b2", "#4f46e5"]
        let sum = seed.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }
        return palette[sum % palette.count]
    }

    /// Trimmed text, nil when empty.
    static func clean(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }

    static func isEmail(_ value: String) -> Bool {
        value.range(of: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", options: .regularExpression) != nil
    }
}
