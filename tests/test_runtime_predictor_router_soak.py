from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
LATEST = ROOT / "app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt"
SCIENCE = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"
REVISION = ROOT / "app/src/main/java/com/omegas/prohub/learning/AdvisorRevisionGate.kt"
APP = ROOT / "app/src/main/assets/ui/app.js"


class RuntimePredictorRouterSoak(unittest.TestCase):
    def test_static_owners_match_soak_model(self):
        latest = LATEST.read_text("utf-8")
        science = SCIENCE.read_text("utf-8")
        revision = REVISION.read_text("utf-8")
        app = APP.read_text("utf-8")
        self.assertIn("pending = task", latest)
        self.assertIn("coalesced.incrementAndGet()", latest)
        self.assertIn("MAX_HOT_EVIDENCE = 3", science)
        self.assertIn("SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING", science)
        self.assertIn("if (normalized == lastToken) return null", revision)
        self.assertIn("revision = saturatingIncrement(revision)", revision)
        self.assertEqual(1, app.count("new ui.Scheduler("))
        self.assertNotIn("startMapRead", app[app.index("function activateRoute"):app.index("router.onNavigate")])

    def test_100k_frame_soak_stays_bounded_and_route_independent(self):
        total_frames = 100_000
        generation = 1
        acquired = 0
        stale_rejected = 0
        route_changes = 0
        structural_reads = 0
        visual_pending = None
        visual_active = None
        max_visual_pending = 0
        visual_coalesced = 0
        science_pending = []
        max_science_pending = 0
        science_superseded = 0
        science_rejected_low_value = 0
        requested_revision = 0
        published_revision = 0
        last_token = None
        pending_predictor_revision = None
        predictor_computes = 0
        values = {"STATIC": 40, "DYNAMIC": 60, "FAST": 80, "POST": 100}

        def admit_science(frame, klass, gen):
            nonlocal max_science_pending, science_superseded, science_rejected_low_value
            if gen != generation:
                return False
            if len(science_pending) < 3:
                science_pending.append((frame, klass, gen))
                max_science_pending = max(max_science_pending, len(science_pending))
                return True
            lowest_index = min(range(len(science_pending)), key=lambda i: (values[science_pending[i][1]], science_pending[i][0]))
            lowest = science_pending[lowest_index]
            if values[klass] < values[lowest[1]]:
                science_rejected_low_value += 1
                return False
            science_pending.pop(lowest_index)
            science_pending.append((frame, klass, gen))
            science_superseded += 1
            max_science_pending = max(max_science_pending, len(science_pending))
            return True

        for frame in range(1, total_frames + 1):
            acquired += 1

            # Route churn is deliberately unrelated to acquisition.
            if frame % 137 == 0:
                route_changes += 1

            # Periodic read-only structural work widens a planned interval but does not drop acquisition.
            if frame % 5_000 == 0:
                structural_reads += 1

            # Visual consumer is intentionally slow: one active + one latest pending only.
            if visual_active is None:
                visual_active = (frame, generation)
            else:
                if visual_pending is not None:
                    visual_coalesced += 1
                visual_pending = (frame, generation)
                max_visual_pending = max(max_visual_pending, 1)
            if frame % 17 == 0:
                visual_active = visual_pending
                visual_pending = None

            # Scientific workload mixes values; worker consumes slower than acquisition.
            if frame % 101 == 0:
                admit_science(frame, "POST", generation)
            elif frame % 29 == 0:
                admit_science(frame, "FAST", generation)
            elif frame % 7 == 0:
                admit_science(frame, "DYNAMIC", generation)
            elif frame % 31 == 0:
                admit_science(frame, "STATIC", generation)
            if frame % 11 == 0 and science_pending:
                best = max(range(len(science_pending)), key=lambda i: (values[science_pending[i][1]], science_pending[i][0]))
                science_pending.pop(best)

            # Predictor revision only changes on a material semantic token, never per visual frame.
            token = f"evidence:{frame // 997}" if frame % 997 == 0 else last_token
            if token is not None and token != last_token:
                last_token = token
                requested_revision += 1
                pending_predictor_revision = requested_revision
            # Slow predictor worker coalesces to newest requested revision.
            if frame % 2_003 == 0 and pending_predictor_revision is not None:
                published_revision = pending_predictor_revision
                pending_predictor_revision = None
                predictor_computes += 1

            # Reconnect at midpoint: invalidate queued work from N before N+1 continues.
            if frame == total_frames // 2:
                old_generation = generation
                generation += 1
                kept = []
                for item in science_pending:
                    if item[2] == generation:
                        kept.append(item)
                    else:
                        stale_rejected += 1
                science_pending = kept
                if visual_pending is not None and visual_pending[1] == old_generation:
                    visual_pending = None
                    stale_rejected += 1
                if visual_active is not None and visual_active[1] == old_generation:
                    visual_active = None
                    stale_rejected += 1

        if pending_predictor_revision is not None:
            published_revision = pending_predictor_revision
            predictor_computes += 1

        self.assertEqual(total_frames, acquired)
        self.assertGreater(route_changes, 700)
        self.assertEqual(20, structural_reads)
        self.assertLessEqual(max_visual_pending, 1)
        self.assertLessEqual(max_science_pending, 3)
        self.assertGreater(visual_coalesced, 0)
        self.assertGreater(science_superseded + science_rejected_low_value, 0)
        self.assertGreater(stale_rejected, 0)
        self.assertEqual(requested_revision, published_revision)
        self.assertLess(predictor_computes, requested_revision)
        self.assertLess(predictor_computes, total_frames // 1000)


if __name__ == "__main__":
    unittest.main()
