import SwiftUI
import LangBangShared

@MainActor
final class PracticeViewState: ObservableObject {
    private let model: LangBangShared.PracticeModel

    @Published private(set) var state: LangBangShared.PracticeModel.UiState

    init() {
        let model = LangBangShared.LangBangFactory.shared.createPracticeModel()
        self.model = model
        self.state = Self.readState(from: model)
        self.model.onEvent(event: LangBangShared.PracticeModel.EventStart.shared)
        refresh()
    }

    func send(_ event: LangBangShared.PracticeModel.Event) {
        model.onEvent(event: event)
        refresh()
    }

    func refresh() {
        state = Self.readState(from: model)
    }

    private static func readState(from model: LangBangShared.PracticeModel) -> LangBangShared.PracticeModel.UiState {
        model.state.value
    }
}

struct ContentView: View {
    @StateObject private var practice = PracticeViewState()

    var body: some View {
        NavigationView {
            VStack(spacing: 18) {
                Text("LangBang Practice")
                    .font(.largeTitle.bold())

                Text("One shared model, native SwiftUI renderer")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Divider()

                VStack(alignment: .leading, spacing: 10) {
                    Text("EN cue").font(.caption)

                    if let item = practice.state.current {
                        Text(item.prompt)
                            .font(.title2.weight(.semibold))

                        if practice.state.isRevealed {
                            Text(item.answerPl)
                                .font(.largeTitle.weight(.bold))
                                .foregroundStyle(.blue)
                            Text(item.context)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("Hidden")
                                .font(.title3)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Text("No practice items")
                            .font(.title3)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color(.secondarySystemBackground))
                .cornerRadius(8)

                Text("Item \(practice.state.total == 0 ? 0 : practice.state.index + 1) / \(practice.state.total)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if !practice.state.statusMessage.isEmpty {
                    Text(practice.state.statusMessage)
                        .font(.footnote)
                }

                HStack(spacing: 10) {
                    Button {
                        practice.send(LangBangShared.PracticeModel.EventPrevious.shared)
                    } label: {
                        Label("Previous", systemImage: "chevron.left")
                    }

                    Button {
                        practice.send(LangBangShared.PracticeModel.EventReveal.shared)
                    } label: {
                        Label("Reveal", systemImage: "eye")
                    }
                    .disabled(practice.state.current == nil || practice.state.isRevealed)

                    Button {
                        practice.send(LangBangShared.PracticeModel.EventPlayAudio.shared)
                    } label: {
                        Label("Play", systemImage: "speaker.wave.2")
                    }
                    .disabled(practice.state.current == nil)

                    Button {
                        practice.send(LangBangShared.PracticeModel.EventNext.shared)
                    } label: {
                        Label("Next", systemImage: "chevron.right")
                    }
                }
                .labelStyle(.iconOnly)

                Button {
                    practice.send(LangBangShared.PracticeModel.EventRestart.shared)
                } label: {
                    Label("Restart", systemImage: "arrow.clockwise")
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Practice")
            .onAppear {
                practice.refresh()
            }
        }
    }
}
