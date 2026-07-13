"""Focused unit tests for the offline PMD corpus auditor."""

from __future__ import annotations

import struct
import sys
import unittest
import hashlib
import json
from pathlib import Path


TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import pmd_corpus_audit as audit


class PmdCorpusAuditTest(unittest.TestCase):
    def test_command_table_covers_compiled_opcode_range(self) -> None:
        audit.validate_command_table()

    def test_listing_parser_keeps_only_safe_music_entries(self) -> None:
        listing = """
            ST00.M86 1234 900
            README.TXT 42 42
            ST00.M26 1000 700
        """

        self.assertEqual(
            audit.parse_thdat_listing(listing),
            [
                audit.ListedEntry("ST00.M86", 1234, 900),
                audit.ListedEntry("ST00.M26", 1000, 700),
            ],
        )

    def test_listing_parser_rejects_path_traversal(self) -> None:
        with self.assertRaisesRegex(audit.PmdScanError, "Unsafe archive entry"):
            audit.parse_thdat_listing("../ST00.M86 12 10")

    def test_k_part_uses_one_byte_pattern_indices(self) -> None:
        result = audit.scan_part(bytes((0x00, 0x05, 0x7F, 0x80)), 0, "K")

        self.assertEqual(result["rhythm_pattern_selects"], 3)
        self.assertEqual(result["notes"], 0)
        self.assertEqual(result["end_offset"], 3)
        self.assertIsNone(result["error"])

    def test_rhythm_pointer_table_and_patterns_are_bounded(self) -> None:
        data = bytearray(12)
        struct.pack_into("<HH", data, 4, 8, 10)
        data[8:12] = bytes((0x01, 0xFF, 0x02, 0xFF))

        result = audit.scan_rhythm_table(bytes(data), 4, 12, 2)

        self.assertEqual(result["pattern_count"], 2)
        self.assertEqual([pattern["size"] for pattern in result["patterns"]], [2, 2])
        self.assertIsNone(result["error"])

    def test_invalid_rhythm_table_is_absent_when_k_selects_nothing(self) -> None:
        data = bytearray(12)
        struct.pack_into("<H", data, 4, 3)

        result = audit.scan_rhythm_table(bytes(data), 4, 12, 0)

        self.assertTrue(result["absent"])
        self.assertIsNone(result["error"])

    def test_normalized_trace_retains_patch_volume_gate_and_transpose(self) -> None:
        data = bytearray(40)
        struct.pack_into("<H", data, 0, 26)
        data[26:38] = bytes((
            0xFF, 3,
            0xFD, 90,
            0xFE, 2,
            0xF5, 1,
            0x40, 6,
            0x80,
        ))

        result = audit.trace_part(bytes((0,)) + bytes(data), 0, 1)

        self.assertEqual(
            result["notes"],
            [{
                "tick": 0,
                "duration": 6,
                "midi": 61,
                "patch": 3,
                "volume": 90,
                "gate_tail": 2,
                "shift": 1,
                "detune": 0,
            }],
        )

    def test_logo_oracle_is_canonical_and_covers_all_authored_lanes(self) -> None:
        path = TOOLS / "oracles/logo_m86_normalized.json"
        oracle = json.loads(path.read_text(encoding="utf-8"))
        stored_hash = oracle.pop("oracle_sha256")
        canonical = json.dumps(
            oracle, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")

        self.assertEqual(oracle["schema_version"], 1)
        self.assertEqual(
            oracle["source_sha256"],
            "1e572f2677129bdc16bc79323c2e8369ca1c958d9c2685e3c48e21e74c2e66f7",
        )
        self.assertEqual(stored_hash, hashlib.sha256(canonical).hexdigest())
        self.assertEqual([part["part"] for part in oracle["parts"]], ["A", "B", "G", "H", "I"])
        self.assertEqual([part["note_count"] for part in oracle["parts"]], [2, 2, 64, 64, 64])
        self.assertEqual(oracle["summary"]["note_count"], 196)
        self.assertEqual(oracle["summary"]["control_counts"]["tempo"], 9)
        self.assertEqual(oracle["summary"]["control_counts"]["software_lfo_1"], 5)

    def test_logo_oracle_patch_79_is_decoded_register_state(self) -> None:
        oracle = json.loads((TOOLS / "oracles/logo_m86_normalized.json").read_text(encoding="utf-8"))
        patch = oracle["patches"][0]

        self.assertEqual((patch["id"], patch["algorithm"], patch["feedback"]), (79, 4, 7))
        self.assertEqual([op["mul"] for op in patch["operators"]], [2, 2, 4, 4])
        self.assertEqual([op["detune"] for op in patch["operators"]], [3, 7, 3, 7])
        self.assertEqual([op["tl"] for op in patch["operators"]], [30, 31, 0, 0])

    def test_represented_logo_commands_are_not_ranked_unpreserved(self) -> None:
        represented = {0xFC, 0xFA, 0xF0, 0xF2, 0xF1, 0xCB, 0xCA, 0xBB}
        self.assertTrue(represented <= audit.CURRENT_IMPORT_PRESERVED)

    def test_unsupported_ranking_deduplicates_identical_payloads(self) -> None:
        location = {"part": "A", "offset": 30, "opcode": 0xFC, "name": "tempo"}
        songs = [
            {"name": "ONE.M86", "sha256": "same", "unpreserved_locations": [location]},
            {"name": "ONE.M26", "sha256": "same", "unpreserved_locations": [location]},
            {"name": "TWO.M86", "sha256": "other", "unpreserved_locations": [location, location]},
        ]

        ranking = audit.build_unsupported_ranking(songs)

        self.assertEqual(ranking[0]["opcode"], "0xFC")
        self.assertEqual(ranking[0]["unique_songs"], 2)
        self.assertEqual(ranking[0]["occurrences"], 3)

    def test_variable_width_commands_follow_compiled_value(self) -> None:
        self.assertEqual(audit.command_parameter_count(bytes((0x10,)), 0, 0xFC), 1)
        self.assertEqual(audit.command_parameter_count(bytes((0xFB,)), 0, 0xFC), 2)
        self.assertEqual(audit.command_parameter_count(bytes((0x10,)), 0, 0xC0), 1)
        self.assertEqual(audit.command_parameter_count(bytes((0xF5,)), 0, 0xC0), 2)

    def test_production_trace_ignores_authored_envelope_controls(self) -> None:
        events, ticks = audit.parse_production_mml(
            "@lls_square EX0 E2,-1,24,1 o4 c4 E31,20,10,5,7,3 d4"
        )

        self.assertEqual(ticks, 960)
        self.assertEqual([(event[0], event[1], event[2]) for event in events], [(0, 480, 60), (480, 480, 62)])


if __name__ == "__main__":
    unittest.main()
