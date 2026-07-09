import unittest

from langbangtrans.config import G2BleConfig
from langbangtrans.g2_protocol import G2Packetizer, crc16_ccitt_false


class ProtocolTests(unittest.TestCase):
    def test_crc16_ccitt_false_known_vector(self) -> None:
        self.assertEqual(crc16_ccitt_false(b"123456789"), 0x29B1)

    def test_packetizer_adds_header_sequence_count_and_crc(self) -> None:
        config = G2BleConfig(
            layout_header=bytes.fromhex("0f01"),
            max_packet_bytes=20,
            crc_encoding="ascii-hex",
        )
        packets = G2Packetizer(config).packetize_text_frame("hello world")

        self.assertEqual(len(packets), 1)
        self.assertTrue(packets[0].startswith(bytes.fromhex("0f01") + b"\x00\x01"))
        self.assertRegex(packets[0][-4:].decode("ascii"), r"^[0-9A-F]{4}$")

    def test_packetizer_fragments_large_frame(self) -> None:
        config = G2BleConfig(
            layout_header=bytes.fromhex("0f01"),
            max_packet_bytes=12,
            crc_encoding="binary-be",
        )
        packets = G2Packetizer(config).packetize_text_frame("abcdefghijklmnopqrstuvwxyz")

        self.assertGreater(len(packets), 1)
        self.assertEqual(packets[0][2], 0)
        self.assertEqual(packets[0][3], len(packets))
        self.assertEqual(packets[-1][2], len(packets) - 1)


if __name__ == "__main__":
    unittest.main()

