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
import pmd_corpus_format as corpus_format


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

    def test_decompiler_stops_after_one_authored_part_loop(self) -> None:
        data = bytearray(40)
        struct.pack_into("<H", data, 0, 26)
        data[26:33] = bytes((
            0x40, 2,
            0xF6,
            0x41, 3,
            0x80,
            0x00,
        ))

        result = audit.trace_part(
            bytes((0,)) + bytes(data), 0, 100, follow_part_loop=False
        )

        self.assertEqual(
            [(note["tick"], note["duration"], note["midi"]) for note in result["notes"]],
            [(0, 2, 60), (2, 3, 61)],
        )
        self.assertEqual(
            result["loop"],
            {"start_offset": 29, "start_tick": 2, "end_tick": 5},
        )

    def test_logo_oracle_is_canonical_and_covers_all_authored_lanes(self) -> None:
        path = TOOLS / "oracles/logo_m86_normalized.json"
        oracle = json.loads(path.read_text(encoding="utf-8"))
        stored_hash = oracle.pop("oracle_sha256")
        canonical = json.dumps(
            oracle, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")

        self.assertEqual(oracle["schema_version"], 2)
        self.assertEqual(
            oracle["source_sha256"],
            "1e572f2677129bdc16bc79323c2e8369ca1c958d9c2685e3c48e21e74c2e66f7",
        )
        self.assertEqual(oracle["entry_sha256"], oracle["source_sha256"])
        self.assertEqual(
            oracle["archive_id"],
            "sha256:ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7",
        )
        self.assertEqual(
            oracle["archive_sha256"],
            "ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7",
        )
        self.assertEqual((oracle["format"], oracle["driver_profile"]), ("M86", "PMD86_YM2608_86PCM"))
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
        self.assertEqual([op["am"] for op in patch["operators"]], [0, 0, 0, 0])

    def test_fm_voice_decode_separates_am_enable_from_decay_rate(self) -> None:
        data = bytearray(52)
        struct.pack_into("<13H", data, 0, *([26] * 13))
        record = memoryview(data)[26:52]
        record[0] = 7
        record[13] = 0x80 | 17
        record[14] = 9
        record[15] = 0x80 | 31
        record[16] = 0

        patch = audit.decode_fm_voice(bytes((0,)) + bytes(data), 7)

        self.assertIsNotNone(patch)
        operators = patch["operators"]
        self.assertEqual([operator["am"] for operator in operators], [1, 0, 1, 0])
        self.assertEqual([operator["dr"] for operator in operators], [17, 9, 31, 0])

    def test_logo_commands_are_not_exact_without_seven_layer_evidence(self) -> None:
        represented = {0xFC, 0xFA, 0xF0, 0xF2, 0xF1, 0xCB, 0xCA, 0xBB}
        for opcode in represented:
            capability = corpus_format.capability_for_command(opcode, [0x10, 0x00])
            self.assertNotEqual(capability["state"], corpus_format.CAPABILITY_EXACT)
            self.assertEqual(
                set(capability["evidence"]), set(corpus_format.CAPABILITY_EVIDENCE_LAYERS)
            )

    def test_non_exact_ranking_deduplicates_identical_payloads(self) -> None:
        location = {
            "part": "A", "offset": 30, "opcode": 0xFC, "name": "tempo",
            "capability_key": "0xFC/value_00_FA", "capability_state": "OBSERVED_ONLY",
        }
        songs = [
            {"name": "ONE.M86", "sha256": "same", "non_exact_locations": [location]},
            {"name": "ONE.M26", "sha256": "same", "non_exact_locations": [location]},
            {"name": "TWO.M86", "sha256": "other", "non_exact_locations": [location, location]},
        ]

        ranking = audit.build_non_exact_ranking(songs)

        self.assertEqual(ranking[0]["capability_key"], "0xFC/value_00_FA")
        self.assertEqual(ranking[0]["unique_songs"], 2)
        self.assertEqual(ranking[0]["occurrences"], 3)

    def test_exact_is_derived_only_when_all_layers_are_connected(self) -> None:
        evidence = {
            layer: {"level": corpus_format.EVIDENCE_CONNECTED, "evidence": "test evidence"}
            for layer in corpus_format.CAPABILITY_EVIDENCE_LAYERS
        }
        self.assertEqual(
            corpus_format.derive_capability_state(evidence), corpus_format.CAPABILITY_EXACT
        )
        evidence["reset_behavior"] = {
            "level": corpus_format.EVIDENCE_MISSING,
            "evidence": "reset is not connected",
        }
        self.assertEqual(
            corpus_format.derive_capability_state(evidence), corpus_format.CAPABILITY_PARTIAL
        )

    def test_hardware_lfo_commands_fail_closed(self) -> None:
        for opcode in (0xE4, 0xE1, 0xE0):
            capability = corpus_format.capability_for_command(opcode, [0])
            self.assertEqual(capability["state"], corpus_format.CAPABILITY_OBSERVED_ONLY)

    def test_missing_independent_checkpoint_traces_are_reported_honestly(self) -> None:
        checkpoint_audit = audit.build_independent_checkpoint_audit()

        self.assertFalse(checkpoint_audit["passed"])
        self.assertEqual(checkpoint_audit["required_trace_count"], 4)
        self.assertEqual(checkpoint_audit["declared_trace_count"], 0)
        self.assertEqual(checkpoint_audit["available_trace_count"], 0)
        self.assertEqual(checkpoint_audit["traces"], [])
        self.assertEqual(checkpoint_audit["validation_errors"], [])

    def test_four_valid_independent_checkpoint_traces_pass_the_schema_gate(self) -> None:
        checkpoint_audit = audit.build_independent_checkpoint_audit(
            self._checkpoint_traces()
        )

        self.assertTrue(checkpoint_audit["passed"])
        self.assertEqual(checkpoint_audit["declared_trace_count"], 4)
        self.assertEqual(checkpoint_audit["available_trace_count"], 4)
        self.assertEqual(checkpoint_audit["validation_errors"], [])
        self.assertEqual(len(checkpoint_audit["traces"]), 4)

    def test_checkpoint_trace_rejects_empty_payload_or_unproven_derivation(self) -> None:
        cases = []
        empty_payload = self._checkpoint_traces()
        empty_payload[0]["checkpoints"] = []
        cases.append((empty_payload, "checkpoints must be a nonempty list"))
        unproven = self._checkpoint_traces()
        unproven[0]["derivation"]["independent_from_timebox_runtime"] = False
        cases.append((unproven, "independent_from_timebox_runtime must be true"))
        missing_state = self._checkpoint_traces()
        missing_state[0]["checkpoints"] = [{"tick": 0}]
        cases.append((missing_state, "must contain nonempty registers or state"))
        invalid_part = self._checkpoint_traces()
        invalid_part[0]["part"] = "X"
        cases.append((invalid_part, "part must be a PMD part A through K"))
        arbitrary_archive_id = self._checkpoint_traces()
        arbitrary_archive_id[0]["archive_id"] = "archive.dat"
        cases.append((arbitrary_archive_id, "matching sha256: archive identity"))
        wrong_driver = self._checkpoint_traces()
        wrong_driver[0]["driver_profile"] = "UNKNOWN_DRIVER"
        cases.append((wrong_driver, "driver_profile must be PMD86_YM2608_86PCM"))

        for traces, expected_error in cases:
            checkpoint_audit = audit.build_independent_checkpoint_audit(traces)
            self.assertFalse(checkpoint_audit["passed"])
            self.assertEqual(checkpoint_audit["available_trace_count"], 3)
            self.assertTrue(
                any(expected_error in error for error in checkpoint_audit["validation_errors"])
            )

    def test_checkpoint_trace_names_songs_parts_and_source_identities_must_be_unique(self) -> None:
        duplicate_name = self._checkpoint_traces()
        duplicate_name[1]["name"] = duplicate_name[0]["name"].swapcase()
        name_audit = audit.build_independent_checkpoint_audit(duplicate_name)
        self.assertFalse(name_audit["passed"])
        self.assertEqual(name_audit["available_trace_count"], 3)
        self.assertTrue(any("name duplicates" in error for error in name_audit["validation_errors"]))

        duplicate_song = self._checkpoint_traces()
        duplicate_song[1]["song"] = duplicate_song[0]["song"].swapcase()
        song_audit = audit.build_independent_checkpoint_audit(duplicate_song)
        self.assertFalse(song_audit["passed"])
        self.assertEqual(song_audit["available_trace_count"], 3)
        self.assertTrue(any("named song" in error for error in song_audit["validation_errors"]))

        duplicate_part = self._checkpoint_traces()
        duplicate_part[1]["part"] = duplicate_part[0]["part"].swapcase()
        part_audit = audit.build_independent_checkpoint_audit(duplicate_part)
        self.assertFalse(part_audit["passed"])
        self.assertEqual(part_audit["available_trace_count"], 3)
        self.assertTrue(any("part duplicates" in error for error in part_audit["validation_errors"]))

        duplicate_source = self._checkpoint_traces()
        duplicate_source[1]["entry_sha256"] = duplicate_source[0]["entry_sha256"]
        source_audit = audit.build_independent_checkpoint_audit(duplicate_source)
        self.assertFalse(source_audit["passed"])
        self.assertEqual(source_audit["available_trace_count"], 3)
        self.assertTrue(
            any("source identity" in error for error in source_audit["validation_errors"])
        )

    def test_checkpoint_trace_rejects_bad_hashes_and_mismatched_provenance(self) -> None:
        traces = self._checkpoint_traces()
        traces[0]["entry_sha256"] = "not-a-hash"
        traces[1]["archive_id"] = "sha256:" + ("f" * 64)

        checkpoint_audit = audit.build_independent_checkpoint_audit(traces)

        self.assertFalse(checkpoint_audit["passed"])
        self.assertEqual(checkpoint_audit["available_trace_count"], 2)
        self.assertTrue(any("64-digit SHA-256" in error for error in checkpoint_audit["validation_errors"]))
        self.assertTrue(any("matching sha256: archive identity" in error for error in checkpoint_audit["validation_errors"]))

    def test_product_candidate_rejects_non_exact_capabilities(self) -> None:
        song = self._assessment_song()
        assessment = audit.build_import_assessments([song], ["CANDIDATE.M86"], [])[0]

        self.assertFalse(assessment["passed"])
        self.assertFalse(assessment["catalog_eligible"])
        self.assertEqual(assessment["non_exact_capabilities"][0]["state"], "OBSERVED_ONLY")

    def test_product_candidate_remains_closed_without_four_independent_traces(self) -> None:
        song = self._assessment_song()
        song["capability_usages"] = []

        assessment = audit.build_import_assessments([song], ["CANDIDATE.M86"], [])[0]

        self.assertTrue(assessment["capabilities_exact"])
        self.assertFalse(assessment["global_checkpoint_ready"])
        self.assertFalse(assessment["passed"])
        self.assertFalse(assessment["catalog_eligible"])
        self.assertIn("global four-trace", assessment["admission_blockers"][0])

    def test_product_candidate_requires_both_exact_capabilities_and_global_trace_gate(self) -> None:
        song = self._assessment_song()
        song["capability_usages"] = []
        checkpoint_audit = audit.build_independent_checkpoint_audit(self._checkpoint_traces())

        assessment = audit.build_import_assessments(
            [song], ["CANDIDATE.M86"], [], checkpoint_audit
        )[0]

        self.assertTrue(assessment["capabilities_exact"])
        self.assertTrue(assessment["global_checkpoint_ready"])
        self.assertTrue(assessment["passed"])
        self.assertTrue(assessment["catalog_eligible"])
        self.assertEqual(assessment["admission_blockers"], [])

    def test_product_candidate_rejects_a_forged_checkpoint_pass_flag(self) -> None:
        song = self._assessment_song()
        song["capability_usages"] = []
        forged = {
            "passed": True,
            "required_trace_count": 4,
            "available_trace_count": 4,
            "traces": [],
        }

        assessment = audit.build_import_assessments(
            [song], ["CANDIDATE.M86"], [], forged
        )[0]

        self.assertFalse(assessment["global_checkpoint_ready"])
        self.assertFalse(assessment["passed"])
        self.assertFalse(assessment["catalog_eligible"])

    def test_product_candidate_rejects_an_audit_with_valid_traces_plus_schema_errors(self) -> None:
        song = self._assessment_song()
        song["capability_usages"] = []
        traces = self._checkpoint_traces()
        invalid_extra = self._checkpoint_traces()[0]
        invalid_extra["name"] = "invalid-extra"
        invalid_extra["song"] = "INVALID.M86"
        invalid_extra["entry_sha256"] = f"{999:064x}"
        invalid_extra["checkpoints"] = []
        traces.append(invalid_extra)
        checkpoint_audit = audit.build_independent_checkpoint_audit(traces)
        self.assertFalse(checkpoint_audit["passed"])
        self.assertEqual(checkpoint_audit["available_trace_count"], 4)

        assessment = audit.build_import_assessments(
            [song], ["CANDIDATE.M86"], [], checkpoint_audit
        )[0]

        self.assertFalse(assessment["global_checkpoint_ready"])
        self.assertFalse(assessment["passed"])
        self.assertFalse(assessment["catalog_eligible"])

    def test_research_exception_is_explicitly_non_catalog(self) -> None:
        song = self._assessment_song()
        assessment = audit.build_import_assessments([song], [], ["CANDIDATE.M86"])[0]

        self.assertTrue(assessment["passed"])
        self.assertFalse(assessment["catalog_eligible"])
        self.assertEqual(assessment["mode"], "NON_CATALOG_RESEARCH")
        self.assertIn("non-catalog", assessment["exception"])

    def test_product_and_research_modes_cannot_overlap(self) -> None:
        with self.assertRaisesRegex(audit.PmdScanError, "both a product candidate"):
            audit.build_import_assessments(
                [self._assessment_song()], ["CANDIDATE.M86"], ["candidate.m86"]
            )

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

    @staticmethod
    def _assessment_song() -> dict:
        return {
            "name": "CANDIDATE.M86",
            "sha256": "entry-hash",
            "archive_id": "archive.dat",
            "archive_sha256": "archive-hash",
            "format": "M86",
            "profile": "PMD86_YM2608_86PCM",
            "errors": [],
            "capability_usages": [
                {
                    "key": "0xE0",
                    "name": "hardware_lfo_global",
                    "state": "OBSERVED_ONLY",
                    "occurrences": 1,
                }
            ],
        }

    @staticmethod
    def _checkpoint_traces() -> list[dict]:
        traces = []
        index = 0
        while index < 4:
            archive_hash = f"{index + 1:064x}"
            entry_hash = f"{index + 101:064x}"
            traces.append(
                {
                    "name": f"independent-trace-{index}",
                    "song": f"TRACE{index}.M86",
                    "part": chr(ord("A") + index),
                    "archive_id": f"sha256:{archive_hash}",
                    "archive_sha256": archive_hash,
                    "entry_sha256": entry_hash,
                    "format": "M86",
                    "driver_profile": "PMD86_YM2608_86PCM",
                    "derivation": {
                        "kind": audit.INDEPENDENT_CHECKPOINT_DERIVATION_KIND,
                        "method": "independent test driver state capture",
                        "source": "test-only independent fixture",
                        "independent_from_timebox_runtime": True,
                    },
                    "checkpoints": [{"tick": index, "registers": {"0x22": index}}],
                }
            )
            index += 1
        return traces


if __name__ == "__main__":
    unittest.main()
