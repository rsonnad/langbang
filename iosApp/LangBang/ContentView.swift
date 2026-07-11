import SwiftUI

// MARK: - Root

struct ContentView: View {
    @StateObject private var audio = AudioManager.shared
    @State private var instances: [CloudInstanceSummary] = []
    @State private var isLoadingInstances = false
    @State private var instancesError: String?

    @State private var bootstrap: CloudBootstrap?
    @State private var isLoadingBootstrap = false

    var body: some View {
        Group {
            if audio.selectedInstanceId == nil {
                PackPickerView(
                    instances: instances,
                    isLoading: isLoadingInstances,
                    error: instancesError,
                    onRefresh: loadInstances,
                    onSelect: { inst in
                        Task { await selectPack(inst) }
                    }
                )
            } else {
                StudyRootView(
                    bootstrap: $bootstrap,
                    isLoading: isLoadingBootstrap,
                    instances: instances,
                    isLoadingInstances: isLoadingInstances,
                    instancesError: instancesError,
                    onSwitchPack: {
                        audio.clearSelection()
                        bootstrap = nil
                    },
                    onRefreshInstances: loadInstances,
                    onSelectPack: { instance in
                        // Hide the old pack immediately, but retain its isolated content
                        // and audio cache so returning to it never needs a redownload.
                        audio.clearSelection()
                        bootstrap = nil
                        Task { await selectPack(instance) }
                    },
                    onRetryAudio: {
                        if let b = bootstrap { Task { await preloadAndShow(b) } }
                    }
                )
            }
        }
        .task {
            await loadInstances()
            // If a pack was persisted, auto-resume it (but do NOT auto-select on a fresh first launch without persisted state)
            if let id = audio.selectedInstanceId, bootstrap == nil {
                await resumePersistedPack(id)
            }
        }
    }

    private func loadInstances() async {
        isLoadingInstances = true
        instancesError = nil
        do {
            let list = try await CloudClient.shared.fetchInstances()
            // Sort so EN-JA is prominent but still data-driven
            instances = list.sorted { a, b in
                if a.id.contains("en-ja") { return true }
                if b.id.contains("en-ja") { return false }
                return a.displayName < b.displayName
            }
        } catch {
            instancesError = error.localizedDescription
        }
        isLoadingInstances = false
    }

    private func selectPack(_ summary: CloudInstanceSummary) async {
        isLoadingBootstrap = true
        do {
            let b = try await CloudClient.shared.fetchBootstrap(instanceId: summary.id)
            bootstrap = b
            audio.setSelectedPack(instanceId: summary.id, bootstrap: b)
            await preloadAndShow(b)
        } catch {
            // A previously downloaded pack remains usable while offline. The
            // picker stays visible for an uncached pack and explains the failure.
            if let cached = audio.cachedBootstrap(for: summary.id) {
                bootstrap = cached
                audio.setSelectedPack(instanceId: summary.id, bootstrap: cached)
                await preloadAndShow(cached)
            } else {
                instancesError = "Could not open \(summary.displayName): \(error.localizedDescription)"
            }
        }
        isLoadingBootstrap = false
    }

    private func resumePersistedPack(_ instanceId: String) async {
        isLoadingBootstrap = true
        do {
            let b = try await CloudClient.shared.fetchBootstrap(instanceId: instanceId)
            bootstrap = b
            audio.setSelectedPack(instanceId: instanceId, bootstrap: b)
            // Reuse cached audio if present; still run preload to fill gaps and show honest state
            await preloadAndShow(b)
        } catch {
            // If we cannot reach backend, still allow cached bootstrap if we have it
            if let cached = audio.cachedBootstrap(for: instanceId) {
                bootstrap = cached
                audio.setSelectedPack(instanceId: instanceId, bootstrap: cached)
                // Reuse the local pack and fill any missing clips if service
                // has returned since the initial bootstrap request failed.
                await preloadAndShow(cached)
            } else {
                instancesError = "Could not resume the selected pack: \(error.localizedDescription)"
                // Force user back to picker if we truly have nothing
                audio.clearSelection()
            }
        }
        isLoadingBootstrap = false
    }

    @MainActor
    private func preloadAndShow(_ b: CloudBootstrap) async {
        await audio.preloadAudio(for: b)
        // Even on preload error we show content (user can tap to play individual clips which will try on-demand)
    }
}

// MARK: - Pack Picker (first launch, data-driven, no auto default)

struct PackPickerView: View {
    let instances: [CloudInstanceSummary]
    let isLoading: Bool
    let error: String?
    let onRefresh: () async -> Void
    let onSelect: (CloudInstanceSummary) -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                VStack(spacing: 8) {
                    Text("LangBang")
                        .font(.largeTitle.bold())
                    Text("Choose a language pack")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 24)

                if isLoading && instances.isEmpty {
                    ProgressView("Loading packs…")
                        .padding()
                }

                if let error {
                    VStack(spacing: 8) {
                        Text("Could not load packs")
                            .font(.headline)
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Button("Retry", action: { Task { await onRefresh() } })
                    }
                    .padding()
                }

                if !instances.isEmpty {
                    Text("Select a pack to begin. No pack is pre-selected.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)

                    List(instances) { inst in
                        Button {
                            onSelect(inst)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(inst.displayName)
                                        .font(.headline)
                                    Text("\(inst.languagePair.sourceLanguage) → \(inst.languagePair.targetLanguage)")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                    Text(inst.id)
                                        .font(.caption2.monospaced())
                                        .foregroundStyle(.tertiary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(.tertiary)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Double tap to study \(inst.displayName)")
                    }
                    .listStyle(.insetGrouped)
                }

                Spacer()

                if isLoading && !instances.isEmpty {
                    ProgressView().padding(.bottom, 8)
                }
            }
            .navigationTitle("Language Packs")
            .refreshable { await onRefresh() }
        }
    }
}

// MARK: - Study Root (after selection)

struct StudyRootView: View {
    @Binding var bootstrap: CloudBootstrap?
    let isLoading: Bool
    let instances: [CloudInstanceSummary]
    let isLoadingInstances: Bool
    let instancesError: String?
    let onSwitchPack: () -> Void
    let onRefreshInstances: () async -> Void
    let onSelectPack: (CloudInstanceSummary) -> Void
    let onRetryAudio: () -> Void

    @StateObject private var audio = AudioManager.shared
    @State private var selectedTab: StudyTab = .phrases
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && bootstrap == nil {
                    ProgressView("Loading content…")
                } else if let b = bootstrap {
                    VStack(spacing: 0) {
                        // Header
                        header(for: b)
                            .padding(.horizontal)
                            .padding(.top, 8)

                        if let nowVoicing = audio.nowVoicing {
                            nowVoicingBanner(nowVoicing)
                        }
                        if audio.isPreloading {
                            preloadBanner
                        } else if let err = audio.preloadError {
                            errorBanner(err)
                        } else if let status = audio.playbackStatus {
                            playbackBanner(status)
                        }

                        TabView(selection: $selectedTab) {
                            PronunciationSection(bootstrap: b)
                                .tabItem { Label("Pronunciation", systemImage: "speaker.wave.2") }
                                .tag(StudyTab.pronunciation)

                            VerbsSection(bootstrap: b)
                                .tabItem { Label("Verbs", systemImage: "textformat.abc") }
                                .tag(StudyTab.verbs)

                            AdjectivesSection(bootstrap: b)
                                .tabItem { Label("Adjectives", systemImage: "textformat") }
                                .tag(StudyTab.adjectives)

                            NounsSection(bootstrap: b)
                                .tabItem { Label("Nouns", systemImage: "character.book.closed") }
                                .tag(StudyTab.nouns)

                            PhrasesSection(bootstrap: b)
                                .tabItem { Label("Phrases", systemImage: "text.bubble") }
                                .tag(StudyTab.phrases)

                            AdverbsSection(bootstrap: b)
                                .tabItem { Label("Adverbs", systemImage: "clock") }
                                .tag(StudyTab.adverbs)
                        }
                        .tint(.accentColor)
                    }
                } else {
                    Text("Select a pack to begin.")
                }
            }
            .navigationTitle(titleForCurrentPack)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Settings") { showSettings = true }
                        Divider()
                        Button("Switch Language Pack", action: onSwitchPack)
                        if audio.isPreloading {
                            Text("Downloading audio…")
                        } else {
                            Button("Retry Audio Download", action: onRetryAudio)
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                StudySettingsView(
                    selectedInstanceId: audio.selectedInstanceId,
                    instances: instances,
                    isLoadingInstances: isLoadingInstances,
                    instancesError: instancesError,
                    onRefreshInstances: onRefreshInstances,
                    onSelectPack: onSelectPack
                )
            }
        }
    }

    private var titleForCurrentPack: String {
        if let b = bootstrap {
            if b.instance.id.contains("en-ja") { return "Japanese Study" }
            return b.instance.displayName
        }
        return "Study"
    }

    @ViewBuilder
    private func header(for b: CloudBootstrap) -> some View {
        let lp = b.languagePair
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image("LangBangWordmark")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 26)
                    .accessibilityLabel("LangBang")

                Spacer(minLength: 0)

                Text("v\(appVersion) (\(appBuild))")
                    .font(.caption2.monospaced().weight(.semibold))
                    .foregroundStyle(.white.opacity(0.72))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(red: 0.08, green: 0.09, blue: 0.12), in: RoundedRectangle(cornerRadius: 12))

            Text("\(lp.sourceLanguage) → \(lp.targetLanguage)")
                .font(.headline)
            if let ver = b.content.versionId {
                Text(ver).font(.caption2).foregroundStyle(.tertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 4)
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    private var appBuild: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
    }

    private var preloadBanner: some View {
        VStack(spacing: 6) {
            ProgressView(value: audio.preloadProgress)
                .progressViewStyle(.linear)
            Text(audio.preloadMessage)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(Color(.secondarySystemBackground))
    }

    private func errorBanner(_ message: String) -> some View {
        HStack {
            Image(systemName: "exclamationmark.triangle")
            Text(message).font(.footnote)
            Spacer()
            Button("Retry", action: onRetryAudio)
        }
        .padding(8)
        .background(Color(.tertiarySystemFill))
        .padding(.horizontal)
    }

    private func nowVoicingBanner(_ message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "speaker.wave.2.circle.fill")
                .foregroundStyle(.tint)
            Text(message)
                .font(.footnote.weight(.medium))
                .lineLimit(1)
            Spacer()
        }
        .padding(8)
        .background(Color(.secondarySystemBackground))
        .padding(.horizontal)
        .accessibilityLabel(message)
    }

    private func playbackBanner(_ message: String) -> some View {
        HStack {
            if message == "Loading audio…" {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: "speaker.slash")
            }
            Text(message).font(.footnote)
            Spacer()
            if message != "Loading audio…" {
                Button("Retry", action: onRetryAudio)
            }
        }
        .padding(8)
        .background(Color(.tertiarySystemFill))
        .padding(.horizontal)
    }
}

// MARK: - Settings

private struct StudySettingsView: View {
    let selectedInstanceId: String?
    let instances: [CloudInstanceSummary]
    let isLoadingInstances: Bool
    let instancesError: String?
    let onRefreshInstances: () async -> Void
    let onSelectPack: (CloudInstanceSummary) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Learning language") {
                    Text("Choose a language pack. Its lessons and audio download when selected; previously downloaded packs stay on this device for fast switching back.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    if isLoadingInstances && instances.isEmpty {
                        ProgressView("Loading language packs…")
                    }

                    if let instancesError {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Could not refresh language packs")
                                .font(.subheadline.weight(.semibold))
                            Text(instancesError)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            Button("Retry") { Task { await onRefreshInstances() } }
                        }
                    }

                    ForEach(instances) { instance in
                        Button {
                            onSelectPack(instance)
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(instance.languagePair.sourceLanguage + " → " + instance.languagePair.targetLanguage)
                                        .font(.body.weight(.semibold))
                                    Text(instance.displayName)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if instance.id == selectedInstanceId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.tint)
                                        .accessibilityLabel("Selected")
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Switches to this language pack")
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: "\(appVersion) (\(appBuild))")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .task {
                if instances.isEmpty { await onRefreshInstances() }
            }
        }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    private var appBuild: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
    }
}

private enum StudyTab: Hashable {
    case pronunciation, verbs, adjectives, nouns, phrases, adverbs
}

// MARK: - Sections (real EN -> JA content, romaji first for beginners)

struct PronunciationSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: PronunciationPayload? = findLessonPayload(in: bootstrap, type: "pronunciation")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Pronunciation", summary: payload?.summary)
                if let p = payload {
                    ForEach(p.phonemes, id: \.letter) { ph in
                        Card {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(ph.letter).font(.system(size: 36, weight: .bold))
                                    VStack(alignment: .leading) {
                                        Text(ph.name).font(.headline)
                                        Text(ph.ipa).font(.subheadline).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    playButton {
                                        audio.playTarget(bootstrap: bootstrap, text: ph.examples.first?.target ?? "", slowFirst: true)
                                    }
                                }
                                if !ph.englishApproximation.isEmpty {
                                    Text(ph.englishApproximation).font(.footnote)
                                }
                                if let desc = ph.description, !desc.isEmpty {
                                    Text(desc).font(.footnote).foregroundStyle(.secondary)
                                }
                                if !ph.examples.isEmpty {
                                    Text("Examples").font(.caption.bold()).padding(.top, 4)
                                    FlowLayout {
                                    ForEach(ph.examples, id: \.target) { ex in
                                        let reading = p.readings?[ex.target]
                                        Button {
                                                audio.playTarget(bootstrap: bootstrap, text: ex.target, slowFirst: true)
                                        } label: {
                                                VStack(spacing: 2) {
                                                    Text(reading?.romaji ?? ex.target).font(.body)
                                                    if let support = readingSupport(reading) {
                                                        Text(support).font(.caption2).foregroundStyle(.secondary)
                                                    }
                                                }
                                            }
                                            .buttonStyle(.bordered)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    emptyLesson
                }
            }
            .padding()
        }
    }
}

struct VerbsSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: VerbsPayload? = findLessonPayload(in: bootstrap, type: "verbs")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Verbs", summary: payload?.summary)
                if let p = payload, !p.verbs.isEmpty {
                    ForEach(p.verbs, id: \.lemma) { v in
                        let reading = p.readings?[v.lemma]
                        Card {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(reading?.romaji ?? v.lemma)
                                            .font(.title3.bold())
                                            .lineLimit(1)
                                        if let support = readingSupport(reading) {
                                            Text(support)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                        }
                                        Text(v.en)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    Spacer()
                                    playButton {
                                        audio.playTarget(bootstrap: bootstrap, text: v.lemma, slowFirst: true)
                                    }
                                }
                                if !v.forms.isEmpty {
                                    FormGrid(title: "Present", forms: v.forms, readings: p.readings ?? [:]) { form in
                                        audio.playTarget(bootstrap: bootstrap, text: form, slowFirst: false)
                                    }
                                }
                                if let past = v.past_forms, !past.isEmpty {
                                    FormGrid(title: "Past", forms: past, readings: p.readings ?? [:]) { form in
                                        audio.playTarget(bootstrap: bootstrap, text: form, slowFirst: false)
                                    }
                                }
                            }
                        }
                    }
                } else { emptyLesson }
            }
            .padding()
        }
    }
}

struct AdjectivesSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: AdjectivesPayload? = findLessonPayload(in: bootstrap, type: "adjectives")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Adjectives", summary: payload?.summary)
                if let p = payload {
                    ForEach(p.adjectives, id: \.lemma) { a in
                        let reading = p.readings?[a.lemma]
                        Card {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(reading?.romaji ?? a.lemma).font(.title3.bold())
                                    if let support = readingSupport(reading) {
                                        Text(support).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Text(a.en).foregroundStyle(.secondary)
                                }
                                Spacer()
                                playButton { audio.playTarget(bootstrap: bootstrap, text: a.nom.values.first ?? a.lemma, slowFirst: true) }
                            }
                            FormGrid(title: "Forms", forms: a.nom.merging(a.acc, uniquingKeysWith: { $1 }), readings: p.readings ?? [:]) { form in
                                audio.playTarget(bootstrap: bootstrap, text: form)
                            }
                        }
                    }
                } else { emptyLesson }
            }
            .padding()
        }
    }
}

struct NounsSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: NounsPayload? = findLessonPayload(in: bootstrap, type: "nouns")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Nouns", summary: payload?.summary)
                if let p = payload {
                    ForEach(p.nouns, id: \.lemma) { n in
                        let reading = p.readings?[n.lemma]
                        Card {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(reading?.romaji ?? n.lemma).font(.title3.bold())
                                    if let support = readingSupport(reading) {
                                        Text(support).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Text(n.en).foregroundStyle(.secondary)
                                }
                                Spacer()
                                playButton { audio.playTarget(bootstrap: bootstrap, text: n.nom.values.first ?? n.lemma) }
                            }
                        }
                    }
                } else { emptyLesson }
            }
            .padding()
        }
    }
}

struct AdverbsSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: AdverbsPayload? = findLessonPayload(in: bootstrap, type: "adverbs")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Adverbs", summary: payload?.summary)
                if let p = payload {
                    ForEach(p.adverbs, id: \.lemma) { a in
                        let reading = p.readings?[a.lemma]
                        Card {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(reading?.romaji ?? a.lemma).font(.title3.bold())
                                    if let support = readingSupport(reading) {
                                        Text(support).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Text(a.en).foregroundStyle(.secondary)
                                }
                                Spacer()
                                playButton { audio.playTarget(bootstrap: bootstrap, text: a.lemma) }
                            }
                        }
                    }
                } else { emptyLesson }
            }
            .padding()
        }
    }
}

struct PhrasesSection: View {
    let bootstrap: CloudBootstrap
    @StateObject private var audio = AudioManager.shared

    var body: some View {
        let payload: PhrasesPayload? = findLessonPayload(in: bootstrap, type: "phrases")
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sectionHeader(title: "Phrases", summary: payload?.summary ?? "25 essential phrases organized by group.")
                if let p = payload, !p.groups.isEmpty {
                    ForEach(p.groups) { g in
                        Card {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(g.title).font(.headline)
                                if let sub = g.subtitle { Text(sub).font(.subheadline).foregroundStyle(.secondary) }
                                ForEach(Array(g.sentences.enumerated()), id: \.offset) { _, s in
                                    let reading = p.readings?[s.target]
                                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(reading?.romaji ?? s.target).font(.body.weight(.semibold))
                                            if let support = readingSupport(reading) {
                                                Text(support).font(.caption).foregroundStyle(.secondary)
                                            }
                                            Text(s.en).font(.subheadline).foregroundStyle(.secondary)
                                            if let lit = s.literal, !lit.isEmpty {
                                                Text(lit).font(.caption.italic()).foregroundStyle(.tertiary)
                                            }
                                        }
                                        Spacer()
                                        Button {
                                            audio.playTarget(bootstrap: bootstrap, text: s.target, slowFirst: true)
                                        } label: {
                                            Image(systemName: "speaker.wave.2")
                                        }
                                        .buttonStyle(.bordered)
                                        .accessibilityLabel("Play \(s.en)")
                                    }
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                    }
                } else { emptyLesson }
            }
            .padding()
        }
    }
}

// MARK: - Small UI helpers

private func sectionHeader(title: String, summary: String?) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(title).font(.title2.bold())
        if let s = summary, !s.isEmpty {
            Text(s).font(.footnote).foregroundStyle(.secondary)
        }
    }
}

private func readingSupport(_ reading: JapaneseReading?) -> String? {
    guard let reading else { return nil }
    return reading.kana == reading.japanese
        ? reading.kana
        : "\(reading.kana) · \(reading.japanese)"
}

private func playButton(action: @escaping () -> Void) -> some View {
    Button(action: action) {
        Image(systemName: "speaker.wave.2.fill")
    }
    .buttonStyle(.borderedProminent)
}

private var emptyLesson: some View {
    Text("This lesson could not load. Refresh the pack and try again.")
        .foregroundStyle(.secondary)
        .padding()
}

struct Card<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

struct FormGrid: View {
    let title: String
    let forms: [String: String]
    let readings: [String: JapaneseReading]
    let onPlay: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.bold()).foregroundStyle(.secondary)
            let items = forms.sorted { $0.key < $1.key }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], spacing: 8) {
                ForEach(items, id: \.key) { key, value in
                    let reading = readings[value]
                    Button {
                        onPlay(value)
                    } label: {
                        VStack(spacing: 2) {
                            Text(reading?.romaji ?? value).font(.callout.weight(.semibold))
                            if let support = readingSupport(reading) {
                                Text(support).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Text(key).font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(8)
                        .background(Color(.tertiarySystemFill))
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// Simple flow layout for chips
struct FlowLayout<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        // Minimal horizontal wrapping using a LazyVGrid trick
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], spacing: 8) {
            content
        }
    }
}
