import Foundation

enum CloudError: Error, LocalizedError {
    case badStatus(Int, String)
    case decode(String)
    case network(Error)

    var errorDescription: String? {
        switch self {
        case .badStatus(let code, let msg): return "HTTP \(code): \(msg)"
        case .decode(let msg): return "Decode error: \(msg)"
        case .network(let err): return "Network: \(err.localizedDescription)"
        }
    }
}

final class CloudClient {
    static let shared = CloudClient()
    let apiBase: String

    private init() {
        let configuredBase = Bundle.main.object(forInfoDictionaryKey: "LANGBANG_API_BASE") as? String
        let fallbackBase = "https://langbangml-api.langbangml.workers.dev"
        let selectedBase = configuredBase?.isEmpty == false ? configuredBase! : fallbackBase
        apiBase = selectedBase.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 30
        c.timeoutIntervalForResource = 60
        return URLSession(configuration: c)
    }()

    func fetchInstances() async throws -> [CloudInstanceSummary] {
        let url = URL(string: "\(apiBase)/v1/instances")!
        let (data, resp) = try await session.data(from: url)
        guard let http = resp as? HTTPURLResponse else { throw CloudError.network(URLError(.badServerResponse)) }
        guard (200..<300).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8) ?? ""
            throw CloudError.badStatus(http.statusCode, msg)
        }
        struct Envelope: Decodable { let instances: [CloudInstanceSummary]? }
        let env = try JSONDecoder().decode(Envelope.self, from: data)
        return env.instances ?? []
    }

    func fetchBootstrap(instanceId: String) async throws -> CloudBootstrap {
        let enc = instanceId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? instanceId
        let url = URL(string: "\(apiBase)/v1/instances/\(enc)/bootstrap")!
        let (data, resp) = try await session.data(from: url)
        guard let http = resp as? HTTPURLResponse else { throw CloudError.network(URLError(.badServerResponse)) }
        guard (200..<300).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8) ?? ""
            throw CloudError.badStatus(http.statusCode, msg)
        }
        do {
            return try JSONDecoder().decode(CloudBootstrap.self, from: data)
        } catch {
            throw CloudError.decode(error.localizedDescription)
        }
    }

    func fetchAudioManifest(phrases: [AudioPhraseReq]) async throws -> AudioManifestResponse {
        let url = URL(string: "\(apiBase)/v1/audio/manifest")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.httpBody = try JSONEncoder().encode(["phrases": phrases])

        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse else { throw CloudError.network(URLError(.badServerResponse)) }
        guard (200..<300).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8) ?? ""
            throw CloudError.badStatus(http.statusCode, msg)
        }
        return try JSONDecoder().decode(AudioManifestResponse.self, from: data)
    }
}
