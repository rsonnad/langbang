INSERT OR REPLACE INTO language_pairs (
  id, source_language, target_language, source_locale, target_locale, ui_locale,
  source_voice, target_voice, target_slow_voices_json, description, active
) VALUES
  (
    'en-pl',
    'English',
    'Polish',
    'en-US',
    'pl-PL',
    'en-US',
    'en-US-JennyNeural',
    'pl-PL-ZofiaNeural',
    '["pl-PL-ZofiaNeural|slow60v1","pl-PL-ZofiaNeural|slowart1"]',
    'Polish study for English speakers',
    1
  ),
  (
    'pl-en',
    'Polish',
    'English',
    'pl-PL',
    'en-US',
    'pl-PL',
    'pl-PL-ZofiaNeural',
    'en-US-JennyNeural',
    '["en-US-JennyNeural|slow60v1","en-US-JennyNeural|slowart1"]',
    'English study for Polish speakers',
    1
  );

INSERT OR REPLACE INTO content_versions (id, language_pair_id, version, title, summary, active)
VALUES
  ('en-pl-v1', 'en-pl', 1, 'Polish Study for English Speakers', 'Initial LangBang content mirrored from bundled lessons.', 1),
  ('pl-en-v1', 'pl-en', 1, 'English Study for Polish Speakers', 'Initial reversed phrase content for Polish speakers studying English.', 1);

INSERT OR REPLACE INTO app_instances (
  id, display_name, language_pair_id, ui_locale, content_version_id, settings_json, active
) VALUES
  (
    'langbangml-en-pl',
    'LangBangML — Polish Study',
    'en-pl',
    'en-US',
    'en-pl-v1',
    '{"audio":{"slowFirst":true,"autoDownload":true},"features":{"cloudContent":true,"cloudLabels":true},"content":{"fallbackBundledAssets":true}}',
    1
  ),
  (
    'langbangml-pl-en',
    'LangBangML — English Study',
    'pl-en',
    'pl-PL',
    'pl-en-v1',
    '{"audio":{"slowFirst":true,"autoDownload":true},"features":{"cloudContent":true,"cloudLabels":true},"content":{"fallbackBundledAssets":true}}',
    1
  );

INSERT OR REPLACE INTO ui_labels (locale, label_key, label_value) VALUES
  ('en-US', 'app.title', 'LangBangML'),
  ('en-US', 'app.version_prefix', 'v'),
  ('en-US', 'tabs.pronunciation', 'Pronu'),
  ('en-US', 'tabs.verbs', 'Verbs'),
  ('en-US', 'tabs.adjectives', 'Adj'),
  ('en-US', 'tabs.adverbs', 'Adv'),
  ('en-US', 'tabs.nouns', 'Nouns'),
  ('en-US', 'tabs.phrases', 'Phrases'),
  ('en-US', 'tabs.quizzes', 'Quiz'),
  ('en-US', 'tabs.numbers', 'Num'),
  ('en-US', 'tabs.settings', 'Settings'),
  ('en-US', 'status.offline', 'Offline — cached content only. Generation + audio synth disabled.'),
  ('en-US', 'status.checking_updates', 'Checking for updates…'),
  ('en-US', 'status.up_to_date', 'LangBangML is up to date'),
  ('en-US', 'status.downloading', 'Downloading'),
  ('en-US', 'status.update_download_failed', 'Update download failed'),
  ('en-US', 'status.install_permission_missing', 'Install permission is not enabled.'),
  ('en-US', 'install.preparing', 'Preparing update…'),
  ('en-US', 'install.opening', 'Opening Android installer…'),
  ('en-US', 'actions.configure_listening', 'Configure listening mix'),
  ('en-US', 'actions.voicing', 'Voicing'),
  ('en-US', 'settings.cloud.title', 'Cloud configuration'),
  ('en-US', 'settings.cloud.description', 'Instance, language pair, content version, and UX labels are downloaded from Cloudflare. Bundled lessons stay as fallback content.'),
  ('en-US', 'settings.cloud.instance', 'Instance'),
  ('en-US', 'settings.cloud.language_pair', 'Language pair'),
  ('en-US', 'settings.cloud.content_version', 'Content version'),
  ('en-US', 'settings.cloud.labels', 'UX labels'),
  ('en-US', 'settings.cloud.last_sync', 'Last sync'),
  ('en-US', 'settings.cloud.not_synced', 'Not synced yet'),
  ('en-US', 'settings.cloud.sync_now', 'Sync now'),
  ('en-US', 'settings.cloud.syncing', 'Syncing…'),
  ('en-US', 'settings.cloud.error', 'Cloud sync failed'),
  ('pl-PL', 'app.title', 'LangBangML'),
  ('pl-PL', 'app.version_prefix', 'v'),
  ('pl-PL', 'tabs.pronunciation', 'Wym.'),
  ('pl-PL', 'tabs.verbs', 'Czas.'),
  ('pl-PL', 'tabs.adjectives', 'Przym.'),
  ('pl-PL', 'tabs.adverbs', 'Przys.'),
  ('pl-PL', 'tabs.nouns', 'Rzecz.'),
  ('pl-PL', 'tabs.phrases', 'Zwroty'),
  ('pl-PL', 'tabs.quizzes', 'Quiz'),
  ('pl-PL', 'tabs.numbers', 'Liczby'),
  ('pl-PL', 'tabs.settings', 'Ustaw.'),
  ('pl-PL', 'status.offline', 'Offline — tylko zapisana treść. Generowanie i synteza audio są wyłączone.'),
  ('pl-PL', 'status.checking_updates', 'Sprawdzam aktualizacje…'),
  ('pl-PL', 'status.up_to_date', 'LangBangML jest aktualny'),
  ('pl-PL', 'status.downloading', 'Pobieram'),
  ('pl-PL', 'status.update_download_failed', 'Pobieranie aktualizacji nie powiodło się'),
  ('pl-PL', 'status.install_permission_missing', 'Brak uprawnienia do instalacji.'),
  ('pl-PL', 'install.preparing', 'Przygotowuję aktualizację…'),
  ('pl-PL', 'install.opening', 'Otwieram instalator Androida…'),
  ('pl-PL', 'actions.configure_listening', 'Konfiguruj odsłuch'),
  ('pl-PL', 'actions.voicing', 'Czytam'),
  ('pl-PL', 'settings.cloud.title', 'Konfiguracja Cloudflare'),
  ('pl-PL', 'settings.cloud.description', 'Instancja, para językowa, wersja treści i etykiety UX są pobierane z Cloudflare. Wbudowane lekcje zostają jako zapas.'),
  ('pl-PL', 'settings.cloud.instance', 'Instancja'),
  ('pl-PL', 'settings.cloud.language_pair', 'Para językowa'),
  ('pl-PL', 'settings.cloud.content_version', 'Wersja treści'),
  ('pl-PL', 'settings.cloud.labels', 'Etykiety UX'),
  ('pl-PL', 'settings.cloud.last_sync', 'Ostatnia synchronizacja'),
  ('pl-PL', 'settings.cloud.not_synced', 'Jeszcze nie zsynchronizowano'),
  ('pl-PL', 'settings.cloud.sync_now', 'Synchronizuj'),
  ('pl-PL', 'settings.cloud.syncing', 'Synchronizuję…'),
  ('pl-PL', 'settings.cloud.error', 'Synchronizacja z Cloudflare nie powiodła się');
