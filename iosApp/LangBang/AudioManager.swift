import Foundation
import AVFoundation
import CommonCrypto

// Cache key exactly matches worker: sha1("\(locale)|\(voice)|\(text)")
func sha1Hex(_ string: String) -> String {
    let data = Data(string.utf8)
    var digest = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
    data.withUnsafeBytes { _ = CC_SHA1($0.baseAddress, CC_LONG(data.count), &digest) }
    return digest.map { String(format: "%02x", $0) }.joined()
}

final class AudioManager: ObservableObject {
    static let shared = AudioManager()

    @Published private(set) var isPreloading = false
    @Published private(set) var preloadProgress: Double = 0 // 0...1
    @Published private(set) var preloadMessage: String = ""
    @Published private(set) var preloadError: String?
    @Published private(set) var playbackStatus: String?

    private let fileManager = FileManager.default
    private var cacheDir: URL {
        let base = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("langbang-audio", isDirectory: true)
        if !fileManager.fileExists(atPath: dir.path) {
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    private var currentPlayer: AVAudioPlayer?
    private var interruptionObserver: NSObjectProtocol?

    // Persist every downloaded pack independently. Changing languages hides the
    // old pack, while its content and audio stay available for an instant return.
    private let bootstrapKeyPrefix = "langbang.bootstrap.v2."
    private let selectedPackKey = "langbang.selectedPackId.v1"
    private let contentVersionKey = "langbang.contentVersion.v1"

    @Published private(set) var selectedInstanceId: String?
    @Published private(set) var lastBootstrap: CloudBootstrap?
    @Published private(set) var lastContentVersion: String?

    init() {
        if let id = UserDefaults.standard.string(forKey: selectedPackKey) {
            selectedInstanceId = id
        }
        if let ver = UserDefaults.standard.string(forKey: contentVersionKey) {
            lastContentVersion = ver
        }
        // Restore the selected pack only. Other pack records remain untouched
        // and are loaded when the learner selects them again.
        if let id = selectedInstanceId,
           let data = UserDefaults.standard.data(forKey: bootstrapKey(for: id)),
           let b = try? JSONDecoder().decode(CloudBootstrap.self, from: data) {
            lastBootstrap = b
        }
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  AVAudioSession.InterruptionType(rawValue: rawType) == .began else { return }
            self?.stop()
        }
    }

    deinit {
        if let interruptionObserver {
            NotificationCenter.default.removeObserver(interruptionObserver)
        }
    }

    func setSelectedPack(instanceId: String, bootstrap: CloudBootstrap?) {
        selectedInstanceId = instanceId
        UserDefaults.standard.set(instanceId, forKey: selectedPackKey)
        if let b = bootstrap {
            lastBootstrap = b
            if let data = try? JSONEncoder().encode(b) {
                UserDefaults.standard.set(data, forKey: bootstrapKey(for: instanceId))
            }
            let ver = b.content.versionId ?? b.instance.id
            lastContentVersion = ver
            UserDefaults.standard.set(ver, forKey: contentVersionKey)
        }
    }

    func clearSelection() {
        selectedInstanceId = nil
        // We keep the audio files and lastBootstrap for fast return, but hide content
        UserDefaults.standard.removeObject(forKey: selectedPackKey)
    }

    func cachedBootstrap(for instanceId: String) -> CloudBootstrap? {
        guard let data = UserDefaults.standard.data(forKey: bootstrapKey(for: instanceId)) else { return nil }
        return try? JSONDecoder().decode(CloudBootstrap.self, from: data)
    }

    private func bootstrapKey(for instanceId: String) -> String {
        "\(bootstrapKeyPrefix)\(instanceId)"
    }

    // Compute needed audio requests for a bootstrap (phrases + word forms)
    func collectRequests(for bootstrap: CloudBootstrap) -> [AudioPhraseReq] {
        var seen = Set<String>()
        var reqs: [AudioPhraseReq] = []

        let lp = bootstrap.languagePair
        let srcLoc = lp.sourceLocale
        let tgtLoc = lp.targetLocale
        let srcVoice = lp.sourceVoice
        let tgtVoice = lp.targetVoice

        func add(_ text: String, locale: String, voice: String) {
            guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            let key = "\(locale)|\(voice)|\(text)"
            if seen.contains(key) { return }
            seen.insert(key)
            reqs.append(AudioPhraseReq(text: text, voice: voice, locale: locale))
        }

        func addTarget(_ text: String) {
            add(text, locale: tgtLoc, voice: tgtVoice)
            // The EN→JA pack promises deliberate slow-first playback. Download
            // that rendition during pack setup, rather than silently falling
            // back to normal speed while offline.
            for slowVoice in lp.targetSlowVoices ?? [] {
                add(text, locale: tgtLoc, voice: slowVoice)
            }
        }

        // Phrases: both directions
        if let phrases: PhrasesPayload = findLessonPayload(in: bootstrap, type: "phrases") {
            for g in phrases.groups {
                for s in g.sentences {
                    add(s.en, locale: srcLoc, voice: srcVoice)
                    addTarget(s.target)
                }
            }
        }

        // Verbs: target forms only (with subject when Polish-like, but for JA we just use the form)
        if let verbs: VerbsPayload = findLessonPayload(in: bootstrap, type: "verbs") {
            for v in verbs.verbs {
                for (_, form) in v.forms { addTarget(form) }
                if let pf = v.past_forms { for (_, form) in pf { addTarget(form) } }
            }
        }

        // Adjectives
        if let adjs: AdjectivesPayload = findLessonPayload(in: bootstrap, type: "adjectives") {
            for a in adjs.adjectives {
                for (_, v) in a.nom { addTarget(v) }
                for (_, v) in a.acc { addTarget(v) }
            }
        }

        // Adverbs
        if let advs: AdverbsPayload = findLessonPayload(in: bootstrap, type: "adverbs") {
            for a in advs.adverbs { addTarget(a.lemma) }
        }

        // Nouns
        if let nouns: NounsPayload = findLessonPayload(in: bootstrap, type: "nouns") {
            for n in nouns.nouns {
                for (_, v) in n.nom { addTarget(v) }
                for (_, v) in n.acc { addTarget(v) }
                if let g = n.gen { for (_, v) in g { addTarget(v) } }
            }
        }

        // Pronunciation examples
        if let pron: PronunciationPayload = findLessonPayload(in: bootstrap, type: "pronunciation") {
            for ph in pron.phonemes {
                for e in ph.examples { addTarget(e.target) }
            }
        }

        return reqs
    }

    // Preload: ensure manifest URLs, download missing, report progress
    @MainActor
    func preloadAudio(for bootstrap: CloudBootstrap) async {
        isPreloading = true
        preloadProgress = 0
        preloadMessage = "Preparing audio…"
        preloadError = nil
        playbackStatus = nil

        let reqs = collectRequests(for: bootstrap)
        guard !reqs.isEmpty else {
            isPreloading = false
            preloadProgress = 1
            preloadMessage = "Ready"
            return
        }

        // A complete cached pack must remain launchable with no network. Only
        // ask the manifest service for clips which are absent or fail local
        // decode validation.
        let missing = reqs.filter {
            localURL(locale: $0.locale, voice: $0.voice, text: $0.text) == nil
        }
        guard !missing.isEmpty else {
            isPreloading = false
            preloadProgress = 1
            preloadMessage = "Audio ready offline"
            return
        }

        do {
            preloadMessage = "Resolving audio…"
            let manifest = try await CloudClient.shared.fetchAudioManifest(phrases: missing)

            let entries = manifest.manifest.filter { $0.error == nil && !$0.url.isEmpty }
            let total = max(entries.count, 1)
            var done = 0
            var failed = max(missing.count - entries.count, 0)

            for entry in entries {
                preloadMessage = "Downloading audio \(done + 1)/\(total)"
                let localURL = localFileURL(for: entry)
                if !isUsableAudioFile(at: localURL) {
                    if let remote = URL(string: entry.url) {
                        do {
                            let (data, resp) = try await URLSession.shared.data(from: remote)
                            if let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                                try data.write(to: localURL, options: .atomic)
                                if !isUsableAudioFile(at: localURL) {
                                    try? fileManager.removeItem(at: localURL)
                                    failed += 1
                                }
                            } else {
                                failed += 1
                            }
                        } catch {
                            failed += 1
                        }
                    } else {
                        failed += 1
                    }
                }
                done += 1
                preloadProgress = Double(done) / Double(total)
            }

            if failed > 0 {
                preloadError = "Some audio failed to download (\(failed)). You can retry."
            } else {
                preloadMessage = "Audio ready"
            }
        } catch {
            preloadError = error.localizedDescription
        }

        isPreloading = false
        preloadProgress = preloadError == nil ? 1.0 : preloadProgress
    }

    private func localFileURL(for entry: AudioManifestEntry) -> URL {
        // Use the server's sha1 for the filename to be consistent with direct R2 too
        return cacheDir.appendingPathComponent("\(entry.sha1).mp3")
    }

    private func localFileURL(locale: String, voice: String, text: String) -> URL {
        let key = sha1Hex("\(locale)|\(voice)|\(text)")
        return cacheDir.appendingPathComponent("\(key).mp3")
    }

    // Resolve a playable local URL. If missing on disk, returns nil (caller can trigger synth or show error).
    func localURL(locale: String, voice: String, text: String) -> URL? {
        let url = localFileURL(locale: locale, voice: voice, text: text)
        return isUsableAudioFile(at: url) ? url : nil
    }

    private func isUsableAudioFile(at url: URL) -> Bool {
        guard let values = try? url.resourceValues(forKeys: [.fileSizeKey]),
              (values.fileSize ?? 0) > 512,
              let player = try? AVAudioPlayer(contentsOf: url),
              player.duration > 0 else { return false }
        return true
    }

    // Play a clip. Prefers local cached file. Returns true on success.
    @discardableResult
    func play(locale: String, voice: String, text: String) -> Bool {
        guard let url = localURL(locale: locale, voice: voice, text: text) else {
            // A first tap remains useful even if a partial preload missed this
            // clip. Keep the learner informed while it is fetched and played.
            setPlaybackStatus("Loading audio…")
            Task { [weak self] in
                _ = await self?.ensureAndPlay(locale: locale, voice: voice, text: text)
            }
            return false
        }
        let didPlay = playFile(url: url)
        setPlaybackStatus(didPlay ? nil : "Couldn’t play this audio. Tap again to retry.")
        return didPlay
    }

    // Ensure via manifest + download then play. Used for taps on uncached.
    private func ensureAndPlay(locale: String, voice: String, text: String) async -> Bool {
        let req = AudioPhraseReq(text: text, voice: voice, locale: locale)
        do {
            let manifest = try await CloudClient.shared.fetchAudioManifest(phrases: [req])
            guard let entry = manifest.manifest.first,
                  entry.error == nil,
                  let remote = URL(string: entry.url) else {
                setPlaybackStatus("This audio is unavailable. Check your connection and tap again.")
                return false
            }
            let local = localFileURL(for: entry)
            if !isUsableAudioFile(at: local) {
                let (data, resp) = try await URLSession.shared.data(from: remote)
                guard let h = resp as? HTTPURLResponse, (200..<300).contains(h.statusCode) else {
                    setPlaybackStatus("Couldn’t download this audio. Tap again to retry.")
                    return false
                }
                try data.write(to: local, options: .atomic)
                guard isUsableAudioFile(at: local) else {
                    try? fileManager.removeItem(at: local)
                    setPlaybackStatus("Downloaded audio was invalid. Tap again to retry.")
                    return false
                }
            }
            let didPlay = playFile(url: local)
            setPlaybackStatus(didPlay ? nil : "Couldn’t play this audio. Tap again to retry.")
            return didPlay
        } catch {
            setPlaybackStatus("Couldn’t load this audio. Check your connection and tap again.")
            return false
        }
    }

    @discardableResult
    private func playFile(url: URL) -> Bool {
        do {
            try configureAudioSession()
            // Stop previous
            currentPlayer?.stop()
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            player.play()
            currentPlayer = player
            return true
        } catch {
            return false
        }
    }

    private func configureAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try session.setActive(true)
    }

    private func setPlaybackStatus(_ message: String?) {
        if Thread.isMainThread {
            playbackStatus = message
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.playbackStatus = message
            }
        }
    }

    func stop() {
        currentPlayer?.stop()
        currentPlayer = nil
    }

    // Convenience: play target (Japanese) with optional slow first
    func playTarget(bootstrap: CloudBootstrap, text: String, slowFirst: Bool = false) {
        let lp = bootstrap.languagePair
        let locale = lp.targetLocale
        let normalVoice = lp.targetVoice

        if slowFirst, let slow = lp.targetSlowVoices?.first {
            // Do not start a slow download and normal playback concurrently.
            // Preload provides slow-first; a partial/offline cache falls back
            // immediately to normal speed.
            if let slowURL = localURL(locale: locale, voice: slow, text: text), playFile(url: slowURL) {
                setPlaybackStatus(nil)
                return
            }
            // With no cached rendition at all, fetch the promised slow voice
            // rather than racing a normal download beside it.
            if localURL(locale: locale, voice: normalVoice, text: text) == nil {
                _ = play(locale: locale, voice: slow, text: text)
                return
            }
        }
        _ = play(locale: locale, voice: normalVoice, text: text)
    }

    func playSource(bootstrap: CloudBootstrap, text: String) {
        let lp = bootstrap.languagePair
        _ = play(locale: lp.sourceLocale, voice: lp.sourceVoice, text: text)
    }

}
