import Foundation

// MARK: - Instances

struct CloudInstanceSummary: Codable, Identifiable, Equatable {
    let id: String
    let displayName: String
    let uiLocale: String
    let contentVersionId: String
    let languagePair: CloudLanguagePairSummary
}

struct CloudLanguagePairSummary: Codable, Equatable {
    let id: String
    let sourceLanguage: String
    let targetLanguage: String
    let sourceLocale: String
    let targetLocale: String
}

// MARK: - Bootstrap

struct CloudBootstrap: Codable, Equatable {
    let instance: CloudInstance
    let languagePair: CloudLanguagePair
    let content: CloudContent
    let labels: [String: String]?
    let audio: CloudAudioConfig
    let syncedAt: String
}

struct CloudInstance: Codable, Equatable {
    let id: String
    let displayName: String
    let uiLocale: String
    // Settings are feature/audio/content objects in the live bootstrap, not a
    // flat string map. Keep them losslessly decodable so a settings expansion
    // never prevents the whole pack from opening.
    let settings: [String: JSONAny]?
    let updatedAt: String?
}

struct CloudLanguagePair: Codable, Equatable {
    let id: String
    let sourceLanguage: String
    let targetLanguage: String
    let sourceLocale: String
    let targetLocale: String
    let sourceVoice: String
    let targetVoice: String
    let targetSlowVoices: [String]?
    let description: String?
}

struct CloudContent: Codable, Equatable {
    let versionId: String?
    let lessons: [CloudLesson]
}

struct CloudLesson: Codable, Equatable {
    let id: String
    let type: String
    let sortOrder: Int
    let title: String
    let summary: String?
    let payload: [String: JSONAny] // flexible raw JSON for lesson payloads
    let updatedAt: String?
}

struct CloudAudioConfig: Codable, Equatable {
    let manifestEndpoint: String
    let publicR2Base: String
    let audioPrefix: String
}

// MARK: - Audio manifest

struct AudioPhraseReq: Codable, Equatable {
    let text: String
    let voice: String
    let locale: String
}

struct AudioManifestEntry: Codable, Equatable {
    let text: String
    let voice: String
    let locale: String
    let sha1: String
    let url: String
    let uploaded: Bool
    let error: String?
}

struct AudioManifestResponse: Codable, Equatable {
    let summary: AudioManifestSummary
    let manifest: [AudioManifestEntry]
}

struct AudioManifestSummary: Codable, Equatable {
    let requested: Int
    let synthesized: Int
    let cached: Int
    let failed: Int
}

// MARK: - Lesson payloads (decoded on demand)

struct PronunciationPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let phonemes: [PhonemeEntry]
}

struct PhonemeEntry: Decodable, Equatable {
    let letter: String
    let name: String
    let ipa: String
    let englishApproximation: String
    let description: String?
    let examples: [ExampleWord]
}

struct ExampleWord: Decodable, Equatable {
    // The API retains its legacy `pl` key for target-language text. Give it a
    // language-neutral app-facing name so EN→JA content is never mislabeled.
    let target: String
    let en: String

    private enum CodingKeys: String, CodingKey {
        case target = "pl"
        case en
    }
}

struct VerbsPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let verbs: [VerbEntry]
}

struct VerbEntry: Decodable, Equatable {
    let lemma: String
    let en: String
    let forms: [String: String]
    let past_forms: [String: String]?
}

struct AdjectivesPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let adjectives: [AdjectiveEntry]
}

struct AdjectiveEntry: Decodable, Equatable {
    let lemma: String
    let en: String
    let nom: [String: String]
    let acc: [String: String]
}

struct AdverbsPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let adverbs: [AdverbEntry]
}

struct AdverbEntry: Decodable, Equatable {
    let lemma: String
    let en: String
}

struct NounsPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let nouns: [NounEntry]
}

struct NounEntry: Decodable, Equatable {
    let lemma: String
    let en: String
    let gender: String?
    let nom: [String: String]
    let acc: [String: String]
    let gen: [String: String]?
}

struct PhrasesPayload: Decodable, Equatable {
    let id: String
    let title: String
    let summary: String?
    let groups: [PhraseGroup]
}

struct PhraseGroup: Decodable, Equatable, Identifiable {
    let id: String
    let title: String
    let subtitle: String?
    let sentences: [SentenceExample]
}

struct SentenceExample: Decodable, Equatable {
    let en: String
    let target: String
    let literal: String?

    private enum CodingKeys: String, CodingKey {
        case en
        case target = "pl"
        case literal
    }
}

// MARK: - JSONAny: flexible container for arbitrary JSON lesson payloads

struct JSONAny: Codable, Equatable {
    private let storage: Data // canonical JSON bytes

    var rawValue: Any {
        return (try? JSONSerialization.jsonObject(with: storage, options: [.fragmentsAllowed])) ?? NSNull()
    }

    init(_ value: Any) {
        storage = JSONAny.jsonData(for: value)
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        let obj: Any
        if let x = try? c.decode(Bool.self) { obj = x }
        else if let x = try? c.decode(Int.self) { obj = x }
        else if let x = try? c.decode(Double.self) { obj = x }
        else if let x = try? c.decode(String.self) { obj = x }
        else if let x = try? c.decode([String: JSONAny].self) { obj = x.mapValues { $0.rawValue } }
        else if let x = try? c.decode([JSONAny].self) { obj = x.map { $0.rawValue } }
        else { obj = NSNull() }
        storage = JSONAny.jsonData(for: obj)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        let obj = (try? JSONSerialization.jsonObject(with: storage, options: [.fragmentsAllowed])) ?? NSNull()
        if let b = obj as? Bool { try c.encode(b); return }
        if let i = obj as? Int { try c.encode(i); return }
        if let d = obj as? Double { try c.encode(d); return }
        if let s = obj as? String { try c.encode(s); return }
        if let dict = obj as? [String: Any] {
            try c.encode(dict.mapValues { JSONAny($0) })
            return
        }
        if let arr = obj as? [Any] {
            try c.encode(arr.map { JSONAny($0) })
            return
        }
        try c.encodeNil()
    }

    private static func jsonData(for value: Any) -> Data {
        switch value {
        case let bool as Bool:
            return (try? JSONEncoder().encode(bool)) ?? Data("null".utf8)
        case let int as Int:
            return (try? JSONEncoder().encode(int)) ?? Data("null".utf8)
        case let double as Double:
            return (try? JSONEncoder().encode(double)) ?? Data("null".utf8)
        case let string as String:
            return (try? JSONEncoder().encode(string)) ?? Data("null".utf8)
        case is NSNull:
            return Data("null".utf8)
        default:
            guard JSONSerialization.isValidJSONObject(value) else { return Data("null".utf8) }
            return (try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])) ?? Data("null".utf8)
        }
    }

    static func == (lhs: JSONAny, rhs: JSONAny) -> Bool {
        lhs.storage == rhs.storage
    }
}

extension Dictionary where Key == String, Value == JSONAny {
    func decode<T: Decodable>(_ type: T.Type) -> T? {
        let obj = self.mapValues { $0.rawValue }
        guard JSONSerialization.isValidJSONObject(obj),
              let data = try? JSONSerialization.data(withJSONObject: obj, options: []) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}

// Helpers

extension CloudBootstrap {
    var targetVoice: String { languagePair.targetVoice }
    var targetLocale: String { languagePair.targetLocale }
    var sourceVoice: String { languagePair.sourceVoice }
    var sourceLocale: String { languagePair.sourceLocale }
    var slowTargetVoice: String? { languagePair.targetSlowVoices?.first }
}

func findLessonPayload<T: Decodable>(in bootstrap: CloudBootstrap, type: String) -> T? {
    guard let lesson = bootstrap.content.lessons.first(where: { $0.type == type }) else { return nil }
    return lesson.payload.decode(T.self)
}
