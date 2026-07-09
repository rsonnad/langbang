import base64
import unittest

from langbangtrans.config import GeminiConfig
from langbangtrans.gemini_live import GeminiLiveClient, parse_gemini_events


class GeminiLiveTests(unittest.TestCase):
    def test_setup_message_uses_live_translate_generation_config(self) -> None:
        client = GeminiLiveClient(
            GeminiConfig(
                api_key="secret",
                target_language_code="en",
                response_modalities=("AUDIO",),
            )
        )

        setup = client.setup_message()["setup"]
        generation = setup["generationConfig"]

        self.assertEqual(setup["model"], "models/gemini-3.5-live-translate-preview")
        self.assertEqual(generation["responseModalities"], ["AUDIO"])
        self.assertEqual(generation["translationConfig"]["targetLanguageCode"], "en")
        self.assertIn("inputAudioTranscription", generation)
        self.assertIn("outputAudioTranscription", generation)

    def test_parse_transcripts_and_audio(self) -> None:
        audio = b"\x01\x02\x03\x04"
        response = {
            "serverContent": {
                "inputTranscription": {"text": "Dzien dobry", "languageCode": "pl"},
                "outputTranscription": {"text": "Good morning", "languageCode": "en"},
                "modelTurn": {
                    "parts": [
                        {
                            "inlineData": {
                                "data": base64.b64encode(audio).decode("ascii"),
                                "mimeType": "audio/pcm;rate=24000",
                            }
                        }
                    ]
                },
            },
            "usageMetadata": {"totalTokenCount": 42},
        }

        events = list(parse_gemini_events(response))

        self.assertEqual(events[0].input_text, "Dzien dobry")
        self.assertEqual(events[0].output_text, "Good morning")
        self.assertEqual(events[0].total_tokens, 42)
        self.assertEqual(events[1].output_audio, audio)


if __name__ == "__main__":
    unittest.main()

