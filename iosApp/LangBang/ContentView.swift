import SwiftUI
import AVFoundation
import LangBangShared

private enum LbPalette {
    static let wine = Color(red: 0.34, green: 0.02, blue: 0.05)
    static let canvas = Color(red: 0.94, green: 0.96, blue: 0.98)
    static let panel = Color.white
    static let panelAlt = Color(red: 0.91, green: 0.94, blue: 0.97)
    static let line = Color(red: 0.84, green: 0.88, blue: 0.91)
    static let ink = Color(red: 0.08, green: 0.12, blue: 0.16)
    static let muted = Color(red: 0.48, green: 0.55, blue: 0.60)
    static let blue = Color(red: 0.16, green: 0.43, blue: 0.91)
    static let teal = Color(red: 0.03, green: 0.70, blue: 0.55)
    static let coral = Color(red: 0.91, green: 0.30, blue: 0.20)
    static let amber = Color(red: 0.94, green: 0.63, blue: 0.12)
}

private enum AppSection: String, CaseIterable, Identifiable {
    case pronunciation = "Pronu"
    case numbers = "Num"
    case verbs = "Verbs"
    case adjectives = "Adj"
    case adverbs = "Adv"
    case nouns = "Nouns"
    case phrases = "Phrases"
    case quizzes = "Quiz"
    case external = "LLM"

    var id: String { rawValue }
}

@MainActor
private final class NowVoicingSpeechDriver: NSObject, @preconcurrency AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var completions: [ObjectIdentifier: (Int64, (Int64) -> Void)] = [:]

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func play(_ request: NowVoicingAudioRequest, completion: @escaping (Int64) -> Void) {
        completions.removeAll()
        synthesizer.stopSpeaking(at: .immediate)
        guard !request.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            completion(request.id)
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)

        let utterance = AVSpeechUtterance(string: request.text)
        utterance.voice = AVSpeechSynthesisVoice(language: request.languageTag)
        utterance.rate = request.rate
        utterance.preUtteranceDelay = 0.04
        completions[ObjectIdentifier(utterance)] = (request.id, completion)
        synthesizer.speak(utterance)
    }

    @discardableResult
    func pause() -> Bool { synthesizer.pauseSpeaking(at: .word) }

    @discardableResult
    func resume() -> Bool { synthesizer.continueSpeaking() }

    func stop() {
        completions.removeAll()
        synthesizer.stopSpeaking(at: .immediate)
        deactivateAudioSession()
    }

    func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in self?.finishRequest(utterance) }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in
            // Intentional stop/replacement clears this utterance first. A remaining
            // entry is a system cancellation; finish it so the shared queue cannot stall.
            self?.finishRequest(utterance)
        }
    }

    private func finishRequest(_ utterance: AVSpeechUtterance) {
        guard let (id, callback) = completions.removeValue(forKey: ObjectIdentifier(utterance)) else { return }
        callback(id)
    }
}

@MainActor
private final class NowVoicingViewState: ObservableObject {
    private enum PreferenceKey {
        static let english = "nowVoicing.speakEnglish"
        static let slow = "nowVoicing.slowPolish"
        static let loop = "nowVoicing.loop"
    }

    private let model: NowVoicingModel
    private let speech = NowVoicingSpeechDriver()
    private var drivenRequestID: Int64?

    @Published private(set) var state: NowVoicingModel.UiState

    init() {
        let model = LangBangFactory.shared.createNowVoicingModel()
        self.model = model
        self.state = model.state.value
        let defaults = UserDefaults.standard
        if defaults.object(forKey: PreferenceKey.english) != nil,
           defaults.bool(forKey: PreferenceKey.english) != state.speakEnglish {
            model.toggleEnglish()
        }
        if defaults.object(forKey: PreferenceKey.slow) != nil,
           defaults.bool(forKey: PreferenceKey.slow) != state.slowPolish {
            model.toggleSlow()
        }
        if defaults.object(forKey: PreferenceKey.loop) != nil,
           defaults.bool(forKey: PreferenceKey.loop) != state.loop {
            model.toggleLoop()
        }
        self.state = model.state.value
    }

    func startGreeting() {
        startDemoQueue(at: 0)
    }

    func startAppreciate() {
        startDemoQueue(at: 1)
    }

    func togglePause() {
        model.togglePause()
        refresh()
        if state.isPaused {
            if !speech.pause() {
                // The utterance may have finished in the same run-loop turn as
                // the tap. Do not leave shared/UI state parked when nothing paused.
                model.togglePause()
                refresh()
            }
        } else if !speech.resume() {
            // The system may discard a paused utterance during a route change or
            // interruption. Retry the parked shared request instead of showing a
            // playing state with no audio.
            drivenRequestID = nil
            refreshAndDrive()
        }
    }

    func stop() {
        speech.stop()
        drivenRequestID = nil
        model.stop()
        refresh()
    }

    func replay() {
        model.replay()
        refreshAndDrive()
    }

    func previous() {
        model.previous()
        refreshAndDrive()
    }

    func next() {
        model.next()
        refreshAndDrive()
    }

    func toggleStar() {
        model.toggleStar()
        refresh()
    }

    func toggleEnglish() {
        model.toggleEnglish()
        refresh()
        UserDefaults.standard.set(state.speakEnglish, forKey: PreferenceKey.english)
    }

    func toggleSlow() {
        model.toggleSlow()
        refresh()
        UserDefaults.standard.set(state.slowPolish, forKey: PreferenceKey.slow)
    }

    func toggleLoop() {
        model.toggleLoop()
        refresh()
        UserDefaults.standard.set(state.loop, forKey: PreferenceKey.loop)
    }

    func speakWord(_ index: Int) {
        model.speakWord(index: Int32(index))
        refreshAndDrive()
    }

    private func startDemoQueue(at index: Int32) {
        model.startQueue(
            phrases: LangBangFactory.shared.nowVoicingDemoQueue(),
            startIndex: index
        )
        refreshAndDrive()
    }

    private func refresh() {
        state = model.state.value
    }

    private func refreshAndDrive() {
        refresh()
        guard let request = state.audioRequest else {
            drivenRequestID = nil
            speech.deactivateAudioSession()
            return
        }
        guard request.id != drivenRequestID else { return }
        drivenRequestID = request.id
        speech.play(request) { [weak self] requestID in
            guard let self else { return }
            self.model.audioCompleted(requestId: requestID)
            self.refreshAndDrive()
        }
    }
}

@MainActor
private final class PracticeViewState: ObservableObject {
    private let model: LangBangShared.PracticeModel
    @Published private(set) var state: LangBangShared.PracticeModel.UiState

    init() {
        let model = LangBangShared.LangBangFactory.shared.createPracticeModel()
        self.model = model
        self.state = model.state.value
        model.onEvent(event: LangBangShared.PracticeModel.EventStart.shared)
        refresh()
    }

    func send(_ event: LangBangShared.PracticeModel.Event) {
        model.onEvent(event: event)
        refresh()
    }

    func refresh() {
        state = model.state.value
    }
}

struct ContentView: View {
    @State private var section: AppSection = .pronunciation
    @State private var lastSection: AppSection = .pronunciation
    @State private var showingSettings = false
    @State private var voicingExpanded = true
    @StateObject private var practice = PracticeViewState()
    @StateObject private var nowVoicing = NowVoicingViewState()

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                AppHeader(
                    selected: section,
                    showingSettings: showingSettings,
                    voicingActive: nowVoicing.state.isVisible,
                    safeLeading: proxy.safeAreaInsets.leading,
                    safeTrailing: proxy.safeAreaInsets.trailing,
                    onSettings: toggleSettings,
                    onSelect: select,
                    onVoicing: {
                        if !nowVoicing.state.isVisible { nowVoicing.startGreeting() }
                        voicingExpanded = true
                    }
                )
                .frame(height: 62)

                if nowVoicing.state.isVisible, voicingExpanded {
                    NowVoicingPanel(
                        state: nowVoicing.state,
                        onPause: nowVoicing.togglePause,
                        onStop: nowVoicing.stop,
                        onReplay: nowVoicing.replay,
                        onPrevious: nowVoicing.previous,
                        onNext: nowVoicing.next,
                        onStar: nowVoicing.toggleStar,
                        onEnglish: nowVoicing.toggleEnglish,
                        onSlow: nowVoicing.toggleSlow,
                        onLoop: nowVoicing.toggleLoop,
                        onWord: nowVoicing.speakWord,
                        onCollapse: { voicingExpanded = false }
                    )
                    .frame(minHeight: 145, maxHeight: min(235, proxy.size.height * 0.52))
                    .padding(.horizontal, 10)
                    .padding(.top, 6)
                }

                Group {
                    if showingSettings {
                        SettingsScreen()
                    } else {
                        screen(for: section)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
            }
            .background(LbPalette.canvas)
            .foregroundStyle(LbPalette.ink)
        }
        .preferredColorScheme(.light)
        .task {
            if ProcessInfo.processInfo.environment["LANGBANG_PARITY_VOICING"] == "1",
               !nowVoicing.state.isVisible {
                nowVoicing.startGreeting()
            }
        }
    }

    @ViewBuilder
    private func screen(for section: AppSection) -> some View {
        switch section {
        case .pronunciation:
            PronunciationScreen(onPlay: nowVoicing.startGreeting)
        case .numbers:
            NumbersScreen(onPlay: nowVoicing.startGreeting)
        case .verbs:
            GrammarScreen(kind: .verbs, onPlay: nowVoicing.startAppreciate)
        case .adjectives:
            GrammarScreen(kind: .adjectives, onPlay: nowVoicing.startAppreciate)
        case .adverbs:
            GrammarScreen(kind: .adverbs, onPlay: nowVoicing.startAppreciate)
        case .nouns:
            GrammarScreen(kind: .nouns, onPlay: nowVoicing.startGreeting)
        case .phrases:
            PhrasesScreen(onPlay: nowVoicing.startGreeting)
        case .quizzes:
            QuizzesScreen(practice: practice, onPlay: nowVoicing.startGreeting)
        case .external:
            ExternalScreen(onPlay: nowVoicing.startAppreciate)
        }
    }

    private func select(_ newSection: AppSection) {
        section = newSection
        lastSection = newSection
        showingSettings = false
        nowVoicing.stop()
    }

    private func toggleSettings() {
        if showingSettings {
            showingSettings = false
            section = lastSection
        } else {
            lastSection = section
            showingSettings = true
            nowVoicing.stop()
        }
    }
}

private struct AppHeader: View {
    let selected: AppSection
    let showingSettings: Bool
    let voicingActive: Bool
    let safeLeading: CGFloat
    let safeTrailing: CGFloat
    let onSettings: () -> Void
    let onSelect: (AppSection) -> Void
    let onVoicing: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onSettings) {
                Image(systemName: showingSettings ? "xmark" : "gearshape.fill")
                    .font(.system(size: 18, weight: .bold))
                    .frame(width: 44, height: 44)
            }
            .foregroundStyle(.white)

            HStack(spacing: 8) {
                Text("langbang")
                    .font(.system(size: 23, weight: .heavy, design: .rounded))
                    .italic()
                    .foregroundStyle(.white)
                Text(versionLabel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white.opacity(0.62))
            }
            .padding(.trailing, 12)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(AppSection.allCases) { item in
                        Button { onSelect(item) } label: {
                            Text(item.rawValue)
                                .font(.system(size: 15, weight: item == selected && !showingSettings ? .semibold : .regular))
                                .foregroundStyle(item == selected && !showingSettings ? LbPalette.blue : Color.white)
                                .padding(.horizontal, 14)
                                .frame(height: 56)
                                .background(item == selected && !showingSettings ? Color.white : Color.clear)
                                .overlay(alignment: .bottom) {
                                    if item == selected && !showingSettings {
                                        Rectangle().fill(LbPalette.blue.opacity(0.55)).frame(height: 2)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        Divider().overlay(Color.white.opacity(0.12))
                    }
                }
            }

            Spacer(minLength: 6)

            Button(action: onVoicing) {
                Group {
                    if voicingActive {
                        Text("Voicing").font(.system(size: 14, weight: .semibold))
                    } else {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 16, weight: .bold))
                    }
                }
                .foregroundStyle(voicingActive ? Color.white : LbPalette.teal)
                .frame(minWidth: 48, minHeight: 42)
                .padding(.horizontal, voicingActive ? 6 : 0)
                .background(voicingActive ? LbPalette.teal : Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(LbPalette.teal.opacity(0.65), lineWidth: 2))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 8)
        }
        .padding(.leading, safeLeading)
        .padding(.trailing, safeTrailing)
        .background(LbPalette.wine)
    }

    private var versionLabel: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "EN/PL · iOS \(version) (\(build))"
    }
}

private struct NowVoicingPanel: View {
    let state: NowVoicingModel.UiState
    let onPause: () -> Void
    let onStop: () -> Void
    let onReplay: () -> Void
    let onPrevious: () -> Void
    let onNext: () -> Void
    let onStar: () -> Void
    let onEnglish: () -> Void
    let onSlow: () -> Void
    let onLoop: () -> Void
    let onWord: (Int) -> Void
    let onCollapse: () -> Void

    @ViewBuilder
    var body: some View {
        if let phrase = state.phrase {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("NOW VOICING")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(LbPalette.muted)
                    Text([state.position, state.statusDetail].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(LbPalette.muted)
                        .lineLimit(1)
                    Spacer()
                    Button(action: onStar) {
                        Image(systemName: state.isStarred ? "star.fill" : "star")
                            .font(.system(size: 29, weight: .regular))
                            .foregroundStyle(state.isStarred ? LbPalette.amber : LbPalette.coral.opacity(0.75))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(state.isStarred ? "Unstar phrase" : "Star phrase")
                }
                .frame(width: 115, alignment: .leading)

                ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 5) {
                    HStack(alignment: .top) {
                        Spacer(minLength: 115)
                        Text(phrase.english)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(LbPalette.muted)
                            .lineLimit(1)
                        Spacer()
                        Text(phrase.context)
                            .font(.system(size: 10))
                            .foregroundStyle(LbPalette.muted)
                            .lineLimit(1)
                    }

                    CenteredFlowLayout(horizontalSpacing: phrase.words.count > 4 ? 5 : 10, verticalSpacing: 4) {
                        ForEach(Array(phrase.words.enumerated()), id: \.offset) { index, word in
                            Button { onWord(index) } label: {
                                VStack(spacing: 2) {
                                    Text(word.phonetic)
                                        .font(.system(size: 10))
                                        .foregroundStyle(LbPalette.muted)
                                        .frame(minHeight: 12)
                                    Text(word.polish)
                                        .font(.system(size: phrase.words.count > 4 ? 29 : (phrase.words.count > 2 ? 38 : 48), weight: .bold))
                                        .foregroundStyle(LbPalette.ink)
                                        .padding(.horizontal, phrase.words.count > 4 ? 3 : 6)
                                        .padding(.vertical, 1)
                                        .background(index.isMultiple(of: 2) ? LbPalette.panelAlt : Color(red: 0.78, green: 0.82, blue: 0.87))
                                    Text(word.english)
                                        .font(.system(size: phrase.words.count > 4 ? 11 : 13))
                                        .foregroundStyle(LbPalette.blue)
                                        .lineLimit(1)
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Speak \(word.polish), \(word.english)")
                        }
                    }

                    HStack(spacing: 8) {
                        Spacer()
                        NowVoicingOption(label: "English", checked: state.speakEnglish, enabled: !state.canPause, action: onEnglish)
                        NowVoicingOption(label: "Slow", checked: state.slowPolish, enabled: !state.canPause, action: onSlow)
                        Button(action: onLoop) {
                            Image(systemName: "repeat")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(state.loop ? LbPalette.teal : LbPalette.muted)
                                .frame(width: 24, height: 24)
                                .background(state.loop ? LbPalette.teal.opacity(0.14) : Color.clear)
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .disabled(state.canPause)
                        .opacity(state.canPause ? 0.45 : 1)
                        .accessibilityLabel(state.loop ? "Loop on" : "Loop off")
                    }
                }
                }
                .scrollBounceBehavior(.basedOnSize)

                VStack(spacing: 4) {
                    TransportButton(icon: "backward.end.fill", accessibilityLabel: state.canGoPrevious ? "Previous phrase" : "Replay phrase", color: LbPalette.ink, enabled: true, action: state.canGoPrevious ? onPrevious : onReplay)
                    TransportButton(icon: "forward.end.fill", accessibilityLabel: "Next phrase", color: LbPalette.ink, enabled: state.canGoNext, action: onNext)
                    TransportButton(icon: state.isPaused ? "play.fill" : "pause.fill", accessibilityLabel: state.isPaused ? "Resume" : "Pause", color: LbPalette.teal, filled: true, enabled: state.canPause, action: onPause)
                    TransportButton(icon: "stop.fill", accessibilityLabel: "Stop", color: LbPalette.coral, filled: true, action: onStop)
                    TransportButton(icon: "mic.fill", accessibilityLabel: "Speech rating unavailable", color: LbPalette.ink, enabled: false, action: {})
                }
                .frame(width: 42)
                .overlay(alignment: .topTrailing) {
                    Button(action: onCollapse) {
                        Image(systemName: "chevron.up")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(LbPalette.muted)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Collapse now voicing")
                    .offset(x: 9, y: -9)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(LbPalette.panel)
            .clipShape(RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(LbPalette.line, lineWidth: 1))
        }
    }
}

private struct CenteredFlowLayout: Layout {
    let horizontalSpacing: CGFloat
    let verticalSpacing: CGFloat

    private struct Row {
        var items: [(index: Int, size: CGSize)] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let maxWidth = proposal.width ?? .greatestFiniteMagnitude
        let rows = makeRows(subviews: subviews, maxWidth: maxWidth)
        return CGSize(
            width: proposal.width ?? rows.map(\.width).max() ?? 0,
            height: rows.reduce(0) { $0 + $1.height } + CGFloat(max(0, rows.count - 1)) * verticalSpacing
        )
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = makeRows(subviews: subviews, maxWidth: bounds.width)
        var y = bounds.minY
        for row in rows {
            var x = bounds.midX - row.width / 2
            for item in row.items {
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(item.size)
                )
                x += item.size.width + horizontalSpacing
            }
            y += row.height + verticalSpacing
        }
    }

    private func makeRows(subviews: Subviews, maxWidth: CGFloat) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            let proposedWidth = row.items.isEmpty ? size.width : row.width + horizontalSpacing + size.width
            if !row.items.isEmpty, proposedWidth > maxWidth {
                rows.append(row)
                row = Row()
            }
            row.items.append((index, size))
            row.width = row.items.count == 1 ? size.width : row.width + horizontalSpacing + size.width
            row.height = max(row.height, size.height)
        }
        if !row.items.isEmpty { rows.append(row) }
        return rows
    }
}

private struct NowVoicingOption: View {
    let label: String
    let checked: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 1) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 14, weight: .medium))
                Text(label)
                    .font(.system(size: 10, weight: .semibold))
            }
            .foregroundStyle(LbPalette.muted)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .accessibilityLabel(label)
        .accessibilityValue(checked ? "On" : "Off")
    }
}

private struct TransportButton: View {
    let icon: String
    let accessibilityLabel: String
    let color: Color
    var filled = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(filled ? Color.white : color)
                .frame(width: 34, height: 28)
                .background(filled ? color : Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 7))
                .overlay(RoundedRectangle(cornerRadius: 7).stroke(filled ? color : LbPalette.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.42)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHidden(!enabled && icon == "mic.fill")
    }
}

private struct PronunciationScreen: View {
    let onPlay: () -> Void
    @State private var selectedIndex = 0
    private let items = [
        ("ą", "/ɔ̃/", "Nasal 'o' — like the French 'on' in 'bon'.", "a-ogonek"),
        ("ę", "/ɛ̃/", "Nasal 'e' — like 'en' in French 'vin' but with an 'e' base.", "e-ogonek"),
        ("ó", "/u/", "Identical to 'u' — sounds like 'oo' in 'boot'.", "o-kreska"),
        ("y", "/ɨ/", "Like the 'i' in 'bit' but pulled further back in the mouth.", "igrek"),
        ("ł", "/w/", "Like English 'w' in 'water'.", "eł"),
        ("w", "/v/", "Like English 'v' in 'van'.", "wu")
    ]

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                ToggleStrip()
                ForEach(items.indices, id: \.self) { index in
                    Button { selectedIndex = index } label: {
                        HStack(spacing: 9) {
                            Image(systemName: "checkmark.square.fill").foregroundStyle(LbPalette.ink)
                            Text(items[index].0).font(.system(size: 20)).foregroundStyle(index == selectedIndex ? .white : LbPalette.blue)
                            Text(items[index].1).font(.system(size: 14)).foregroundStyle(index == selectedIndex ? .white : LbPalette.muted)
                            Text(items[index].2).font(.system(size: 12)).foregroundStyle(index == selectedIndex ? .white : LbPalette.muted).lineLimit(2)
                            Spacer()
                        }
                        .padding(.horizontal, 10)
                        .frame(maxWidth: .infinity, minHeight: 43)
                        .background(index == selectedIndex ? LbPalette.blue : (index.isMultiple(of: 2) ? Color.white : LbPalette.panelAlt))
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center, spacing: 12) {
                    Text(items[selectedIndex].0).font(.system(size: 56, weight: .light)).foregroundStyle(LbPalette.blue)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(items[selectedIndex].3).font(.system(size: 13)).foregroundStyle(LbPalette.muted)
                        Text(items[selectedIndex].1).font(.system(size: 24)).foregroundStyle(LbPalette.ink)
                    }
                    Spacer()
                    PlayButton(label: "Play 60", action: onPlay)
                    StepperPill(value: "3")
                }
                InfoCard(title: items[selectedIndex].2, subtitle: "Tap Play to hear the selected sound, then use the word rows to practice it in context.")
                HStack {
                    Text("Common words").font(.system(size: 19)).foregroundStyle(LbPalette.blue)
                    Spacer()
                    Text("Tap mic to score yourself").font(.system(size: 12)).foregroundStyle(LbPalette.muted)
                }
                WordRow(polish: "mąż", english: "husband", onPlay: onPlay)
                WordRow(polish: "wąż", english: "snake", onPlay: onPlay)
                WordRow(polish: "kąt", english: "corner", onPlay: onPlay)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(10)
    }
}

private struct NumbersScreen: View {
    let onPlay: () -> Void
    private let rows = [("1", "jeden", "one"), ("2", "dwa", "two"), ("3", "trzy", "three"), ("4", "cztery", "four"), ("5", "pięć", "five")]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Numbers · Liczby").font(.system(size: 26, weight: .medium)).foregroundStyle(LbPalette.blue)
                    Text("0–100. For 30–99, combine tens + ones — e.g. trzydzieści pięć (35).")
                        .font(.system(size: 14)).foregroundStyle(LbPalette.muted)
                }
                Spacer()
                StepperPill(value: "3")
                PlayButton(label: "Play 138", action: onPlay)
            }
            ToggleStrip()
            Text("1–10").font(.system(size: 13)).foregroundStyle(LbPalette.muted)
            ForEach(rows.indices, id: \.self) { index in
                HStack(spacing: 16) {
                    Image(systemName: "checkmark.square.fill")
                    Button(action: onPlay) { Image(systemName: "play.fill").foregroundStyle(LbPalette.blue) }.buttonStyle(.plain)
                    Text(rows[index].0).font(.system(size: 22)).foregroundStyle(LbPalette.muted).frame(width: 45, alignment: .leading)
                    Text(rows[index].1).font(.system(size: 24)).foregroundStyle(LbPalette.blue).frame(maxWidth: .infinity, alignment: .leading)
                    Text(rows[index].2).font(.system(size: 17)).foregroundStyle(LbPalette.muted).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(index.isMultiple(of: 2) ? Color.white : LbPalette.panelAlt)
                .clipShape(RoundedRectangle(cornerRadius: 9))
            }
            Spacer(minLength: 0)
        }
        .padding(16)
    }
}

private enum GrammarKind {
    case verbs, adjectives, adverbs, nouns

    var words: [(String, String)] {
        switch self {
        case .verbs: return [("iść", "to go"), ("pić", "to drink"), ("chcieć", "to want"), ("móc", "can / to be able"), ("pisać", "to write"), ("kupować", "to buy")]
        case .adjectives: return [("dobry", "good"), ("nowy", "new"), ("mały", "small"), ("ważny", "important"), ("piękny", "beautiful")]
        case .adverbs: return [("dobrze", "well"), ("szybko", "quickly"), ("często", "often"), ("blisko", "nearby"), ("zawsze", "always")]
        case .nouns: return [("dom", "house / home"), ("kot", "cat"), ("pies", "dog"), ("kobieta", "woman"), ("mężczyzna", "man"), ("dziecko", "child")]
        }
    }
}

private struct GrammarScreen: View {
    let kind: GrammarKind
    let onPlay: () -> Void
    @State private var selectedIndex = 0

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 5) {
                ToggleStrip()
                ForEach(kind.words.indices, id: \.self) { index in
                    Button { selectedIndex = index } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.square.fill")
                            Text(kind.words[index].0).font(.system(size: 20)).foregroundStyle(index == selectedIndex ? .white : LbPalette.blue)
                            Text(kind.words[index].1).font(.system(size: 13)).foregroundStyle(index == selectedIndex ? .white.opacity(0.9) : LbPalette.muted)
                            Spacer()
                        }
                        .padding(.horizontal, 10)
                        .frame(minHeight: 42)
                        .background(index == selectedIndex ? LbPalette.blue : (index.isMultiple(of: 2) ? Color.white : LbPalette.panelAlt))
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 285)

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(kind.words[selectedIndex].0).font(.system(size: 32)).foregroundStyle(LbPalette.blue)
                    Button(action: onPlay) { Image(systemName: "play.fill").foregroundStyle(LbPalette.blue) }.buttonStyle(.plain)
                    Text(kind.words[selectedIndex].1).font(.system(size: 18)).foregroundStyle(LbPalette.muted)
                    Spacer()
                    PlayButton(label: kind == .nouns ? "Play 68" : "Play 7", action: onPlay)
                    StepperPill(value: kind == .nouns ? "2" : "4")
                }
                if kind == .nouns {
                    CaseRows(lemma: kind.words[selectedIndex].0, onPlay: onPlay)
                } else {
                    HStack(spacing: 10) {
                        FormColumn(title: kind == .verbs ? "Present" : "Forms", values: detailValues.0, onPlay: onPlay)
                        FormColumn(title: kind == .verbs ? "Past" : "Examples", values: detailValues.1, onPlay: onPlay)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .padding(10)
    }

    private var detailValues: ([String], [String]) {
        let word = kind.words[selectedIndex].0
        switch kind {
        case .verbs:
            let present = [
                ["ja idę", "ty idziesz", "on idzie"], ["ja piję", "ty pijesz", "ona pije"],
                ["ja chcę", "ty chcesz", "on chce"], ["ja mogę", "ty możesz", "ona może"],
                ["ja piszę", "ty piszesz", "on pisze"], ["ja kupuję", "ty kupujesz", "ona kupuje"]
            ]
            let past = [
                ["ja szedłem", "ty szedłeś", "ona szła"], ["ja piłem", "ty piłeś", "ona piła"],
                ["ja chciałem", "ty chciałeś", "ona chciała"], ["ja mogłem", "ty mogłeś", "ona mogła"],
                ["ja pisałem", "ty pisałeś", "ona pisała"], ["ja kupowałem", "ty kupowałeś", "ona kupowała"]
            ]
            return (present[selectedIndex], past[selectedIndex])
        case .adjectives:
            let stems = ["dobr", "now", "mał", "ważn", "piękn"]
            let stem = stems[selectedIndex]
            return (["\(stem)y", "\(stem)a", "\(stem)e"], ["bardzo \(word)", "naprawdę \(word)", "zbyt \(word)"])
        case .adverbs:
            return ([word, "bardzo \(word)", "niezbyt \(word)"], ["Mówię \(word).", "Robię to \(word).", "Jest \(word)."])
        case .nouns:
            return ([], [])
        }
    }
}

private struct PhrasesScreen: View {
    let onPlay: () -> Void
    private let groups = [("random", "4"), ("Words of Polish Affirmation", "13"), ("Songs", "4"), ("more than i thought", "13"), ("Calendar Basics", "32")]

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("PHRASE GROUPS").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted)
                    Spacer()
                    CircleAction(icon: "plus") {}
                    CircleAction(icon: "arrow.clockwise") {}
                    Text("14").foregroundStyle(LbPalette.muted).padding(9).overlay(Circle().stroke(LbPalette.line, lineWidth: 1))
                }
                HStack { Image(systemName: "magnifyingglass"); Text("Search groups...") }
                    .font(.system(size: 14)).foregroundStyle(LbPalette.muted)
                    .padding(12).background(LbPalette.canvas).clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(LbPalette.line, lineWidth: 1))
                ForEach(groups.indices, id: \.self) { index in
                    HStack {
                        if index == 2 { Image(systemName: "chevron.right") }
                        Text(groups[index].0).font(.system(size: 17)).foregroundStyle(index == 0 ? LbPalette.blue : LbPalette.ink)
                        Spacer()
                        Text("[\(groups[index].1)]").foregroundStyle(LbPalette.muted)
                    }
                    .padding(.horizontal, 12).frame(minHeight: 39)
                    .background(index == 0 ? Color.white : LbPalette.canvas)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(index == 0 ? LbPalette.blue.opacity(0.35) : LbPalette.line, lineWidth: 1))
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .leading, spacing: 9) {
                Text("PRACTICE").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted)
                HStack { Text("random").font(.system(size: 25)); CircleAction(icon: "pencil.fill") {}; Spacer(); Image(systemName: "chevron.left"); Image(systemName: "chevron.right") }
                Text("4 phrases  ·  0 starred").font(.system(size: 13)).foregroundStyle(LbPalette.muted)
                HStack { PlayButton(label: "Play 4", action: onPlay); Button("☆ Starred only") {}.buttonStyle(PillButtonStyle()); CircleAction(icon: "plus") {} }
                PhraseCard(number: "01", english: "Good morning", polish: ["Dzień", "dobry"], literal: ["day", "good"], onPlay: onPlay)
                PhraseCard(number: "02", english: "Thank you very much", polish: ["Dziękuję", "bardzo"], literal: ["I thank", "very"], onPlay: onPlay)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(12)
    }
}

private struct QuizzesScreen: View {
    @ObservedObject var practice: PracticeViewState
    let onPlay: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                Text("QUIZZES").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted)
                QuizMode(title: "Practice recall", subtitle: "English cue → reveal Polish", icon: "rectangle.and.pencil.and.ellipsis")
                QuizMode(title: "Multiple choice", subtitle: "Forms, cases, and phrases", icon: "checkmark.circle")
                QuizMode(title: "Pronoun cases", subtitle: "ja · mnie · mi", icon: "person.2")
                QuizMode(title: "Verb forms", subtitle: "Present and past", icon: "text.book.closed")
                Spacer(minLength: 0)
            }
            .frame(width: 300)

            VStack(alignment: .leading, spacing: 10) {
                HStack { Text("PRACTICE RECALL").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted); Spacer(); Text("Item \(practice.state.total == 0 ? 0 : practice.state.index + 1) / \(practice.state.total)").foregroundStyle(LbPalette.muted) }
                VStack(alignment: .leading, spacing: 8) {
                    Text("EN cue").font(.system(size: 12)).foregroundStyle(LbPalette.muted)
                    if let item = practice.state.current {
                        Text(item.prompt).font(.system(size: 28, weight: .semibold))
                        if practice.state.isRevealed {
                            Text(item.answerPl).font(.system(size: 34, weight: .bold)).foregroundStyle(LbPalette.blue)
                            Text(item.context).font(.system(size: 12)).foregroundStyle(LbPalette.muted)
                        } else {
                            Text("Say it, then reveal").font(.system(size: 18)).foregroundStyle(LbPalette.muted)
                        }
                    }
                }
                .padding(14).frame(maxWidth: .infinity, minHeight: 130, alignment: .leading)
                .background(Color.white).clipShape(RoundedRectangle(cornerRadius: 11))
                HStack(spacing: 10) {
                    Button { practice.send(LangBangShared.PracticeModel.EventPrevious.shared) } label: { Label("Previous", systemImage: "chevron.left") }.buttonStyle(PillButtonStyle())
                    Button { practice.send(LangBangShared.PracticeModel.EventReveal.shared) } label: { Label("Reveal", systemImage: "eye") }.buttonStyle(PillButtonStyle())
                    Button {
                        practice.send(LangBangShared.PracticeModel.EventPlayAudio.shared)
                        onPlay()
                    } label: { Label("Play", systemImage: "speaker.wave.2") }.buttonStyle(PillButtonStyle())
                    Button { practice.send(LangBangShared.PracticeModel.EventNext.shared) } label: { Label("Next", systemImage: "chevron.right") }.buttonStyle(PillButtonStyle())
                }
                Spacer(minLength: 0)
            }
        }
        .padding(12)
    }
}

private struct ExternalScreen: View {
    let onPlay: () -> Void
    @State private var prompt = "I appreciate your help"

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                Text("LLM VOICING").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted)
                Text("Turn an English thought into a grounded Polish phrase, then voice it with the same global controls.")
                    .font(.system(size: 15)).foregroundStyle(LbPalette.muted)
                TextField("English prompt", text: $prompt)
                    .textFieldStyle(.plain).padding(12).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 10))
                HStack { Button("Generate") {}.buttonStyle(PrimaryButtonStyle()).disabled(true).opacity(0.5); Button("Clear") { prompt = "" }.buttonStyle(PillButtonStyle()) }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            VStack(alignment: .leading, spacing: 10) {
                Text("RESULT").font(.system(size: 13, weight: .medium)).foregroundStyle(LbPalette.muted)
                InfoCard(title: "Doceniam twoją pomoc", subtitle: "I appreciate your help · present, first person")
                HStack(spacing: 8) {
                    WordToken(word: "Doceniam", literal: "I appreciate")
                    WordToken(word: "twoją", literal: "your")
                    WordToken(word: "pomoc", literal: "help")
                }
                HStack { PlayButton(label: "Voice", action: onPlay); Button("Save to phrases") {}.buttonStyle(PillButtonStyle()) }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(14)
    }
}

private struct SettingsScreen: View {
    @State private var slowAudio = true
    @State private var analytics = true

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                SettingsCard(title: "Account", icon: "person.crop.circle") {
                    Text("Sign in to sync phrases, stars, and progress.").foregroundStyle(LbPalette.muted)
                    Button("Sign in") {}.buttonStyle(PrimaryButtonStyle()).disabled(true).opacity(0.5)
                }
                SettingsCard(title: "Audio", icon: "speaker.wave.2") {
                    Toggle("Slow Polish by default", isOn: $slowAudio)
                    Text("R2 audio first · Azure fallback").foregroundStyle(LbPalette.muted)
                }
                SettingsCard(title: "Practice filters", icon: "slider.horizontal.3") {
                    Text("Pronouns: ja, ty, on/ona, my, wy, oni").foregroundStyle(LbPalette.muted)
                    Button("Configure") {}.buttonStyle(PillButtonStyle()).disabled(true).opacity(0.5)
                }
                SettingsCard(title: "Privacy & usage", icon: "chart.bar") {
                    Toggle("Share anonymous product analytics", isOn: $analytics)
                    Text("Account deletion and usage details").foregroundStyle(LbPalette.muted)
                }
            }
            .padding(14)
        }
    }
}

private struct ToggleStrip: View {
    var body: some View {
        HStack(spacing: 14) {
            Label("all", systemImage: "checkmark.square.fill")
            Label("random", systemImage: "square")
            Spacer()
        }
        .font(.system(size: 13)).foregroundStyle(LbPalette.muted).padding(.vertical, 3)
    }
}

private struct PlayButton: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) { Label(label, systemImage: "play.fill") }
            .buttonStyle(PrimaryButtonStyle())
    }
}

private struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 14).frame(minHeight: 38)
            .background(LbPalette.teal.opacity(configuration.isPressed ? 0.72 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

private struct PillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(LbPalette.ink)
            .padding(.horizontal, 13).frame(minHeight: 36)
            .background(Color.white.opacity(configuration.isPressed ? 0.7 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(LbPalette.line, lineWidth: 1))
    }
}

private struct StepperPill: View {
    let value: String
    var body: some View {
        HStack(spacing: 10) { Image(systemName: "chevron.left"); Text(value).font(.system(size: 18)); Image(systemName: "chevron.right") }
            .foregroundStyle(LbPalette.muted).padding(.horizontal, 11).frame(height: 38)
            .background(Color.white).clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(LbPalette.line, lineWidth: 1))
    }
}

private struct InfoCard: View {
    let title: String
    let subtitle: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.system(size: 17)).foregroundStyle(LbPalette.ink)
            Text(subtitle).font(.system(size: 13)).foregroundStyle(LbPalette.muted).lineLimit(3)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white).clipShape(RoundedRectangle(cornerRadius: 11))
    }
}

private struct WordRow: View {
    let polish: String
    let english: String
    let onPlay: () -> Void
    var body: some View {
        HStack {
            Button(action: onPlay) { Image(systemName: "play.fill") }.buttonStyle(.plain)
            Text(polish).font(.system(size: 21)).foregroundStyle(LbPalette.blue)
            Spacer()
            Text(english).font(.system(size: 15)).foregroundStyle(LbPalette.muted)
            Spacer()
            Image(systemName: "mic.fill").foregroundStyle(LbPalette.blue)
        }
        .padding(.horizontal, 12).frame(minHeight: 39).background(Color(red: 0.89, green: 0.93, blue: 0.99)).clipShape(RoundedRectangle(cornerRadius: 9))
    }
}

private struct CircleAction: View {
    let icon: String
    var enabled = false
    let action: () -> Void
    var body: some View {
        Button(action: action) { Image(systemName: icon).frame(width: 34, height: 34).background(Color.white).clipShape(Circle()).overlay(Circle().stroke(LbPalette.line, lineWidth: 1)) }
            .buttonStyle(.plain).foregroundStyle(LbPalette.blue)
            .disabled(!enabled)
            .opacity(enabled ? 1 : 0.45)
    }
}

private struct FormColumn: View {
    let title: String
    let values: [String]
    let onPlay: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Label(title, systemImage: "checkmark.square.fill").font(.system(size: 14)).foregroundStyle(LbPalette.muted)
            ForEach(values, id: \.self) { value in
                HStack { Image(systemName: "checkmark"); Button(action: onPlay) { Image(systemName: "play.fill") }.buttonStyle(.plain); Text(value).font(.system(size: 19)); Spacer() }
                    .padding(.horizontal, 10).frame(minHeight: 48).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct CaseRows: View {
    let lemma: String
    let onPlay: () -> Void
    private var cases: [(String, String, String, String)] {
        let forms: [String: [(String, String)]] = [
            "dom": [("dom", "domy"), ("dom", "domy"), ("domu", "domów")],
            "kot": [("kot", "koty"), ("kota", "koty"), ("kota", "kotów")],
            "pies": [("pies", "psy"), ("psa", "psy"), ("psa", "psów")],
            "kobieta": [("kobieta", "kobiety"), ("kobietę", "kobiety"), ("kobiety", "kobiet")],
            "mężczyzna": [("mężczyzna", "mężczyźni"), ("mężczyznę", "mężczyzn"), ("mężczyzny", "mężczyzn")],
            "dziecko": [("dziecko", "dzieci"), ("dziecko", "dzieci"), ("dziecka", "dzieci")]
        ]
        let selected = forms[lemma] ?? [(lemma, lemma), (lemma, lemma), (lemma, lemma)]
        return [("Nominative", "subject", selected[0].0, selected[0].1), ("Accusative", "direct object", selected[1].0, selected[1].1), ("Genitive", "of / absence", selected[2].0, selected[2].1)]
    }
    var body: some View {
        VStack(spacing: 6) {
            ForEach(cases.indices, id: \.self) { index in
                HStack(spacing: 8) {
                    VStack(alignment: .leading) { Text(cases[index].0); Text(cases[index].1).font(.system(size: 11)) }.foregroundStyle(LbPalette.muted).frame(width: 105, alignment: .leading)
                    VStack(spacing: 3) {
                        WordRow(polish: cases[index].2, english: "singular", onPlay: onPlay)
                        WordRow(polish: cases[index].3, english: "plural", onPlay: onPlay)
                    }
                }
                .padding(7).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

private struct PhraseCard: View {
    let number: String
    let english: String
    let polish: [String]
    let literal: [String]
    let onPlay: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Text(number).font(.system(size: 11)).foregroundStyle(LbPalette.muted)
            Button(action: onPlay) { Image(systemName: "play.fill") }.buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                Text(english).font(.system(size: 13)).foregroundStyle(LbPalette.muted)
                HStack(spacing: 8) { ForEach(polish, id: \.self) { Text($0).font(.system(size: 20)).foregroundStyle(LbPalette.blue) } }
                HStack(spacing: 22) { ForEach(literal, id: \.self) { Text($0).font(.system(size: 10)).foregroundStyle(LbPalette.muted) } }
            }
            Spacer()
            Image(systemName: "star").font(.system(size: 20)).foregroundStyle(LbPalette.muted)
        }
        .padding(10).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 11)).overlay(RoundedRectangle(cornerRadius: 11).stroke(LbPalette.line, lineWidth: 1))
    }
}

private struct QuizMode: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 20)).foregroundStyle(LbPalette.blue).frame(width: 32)
            VStack(alignment: .leading) { Text(title).font(.system(size: 16, weight: .medium)); Text(subtitle).font(.system(size: 11)).foregroundStyle(LbPalette.muted) }
            Spacer(); Image(systemName: "chevron.right").foregroundStyle(LbPalette.muted)
        }
        .padding(12).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

private struct WordToken: View {
    let word: String
    let literal: String
    var body: some View {
        VStack(spacing: 4) { Text(word).font(.system(size: 23)).foregroundStyle(LbPalette.blue); Text(literal).font(.system(size: 10)).foregroundStyle(LbPalette.muted) }
            .padding(10).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 9))
    }
}

private struct SettingsCard<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content
    init(title: String, icon: String, @ViewBuilder content: () -> Content) { self.title = title; self.icon = icon; self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title, systemImage: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(LbPalette.blue)
            content.font(.system(size: 13))
            Spacer(minLength: 0)
        }
        .padding(14).frame(maxWidth: .infinity, minHeight: 130, alignment: .topLeading)
        .background(Color.white).clipShape(RoundedRectangle(cornerRadius: 11))
    }
}
