import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRACKET = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoMatchFactorBracket.kt"


class Owner114AutoMatchFactorBracket(unittest.TestCase):
    def test_bracket_states(self):
        with tempfile.TemporaryDirectory(prefix="owner114-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent("""
                import com.omegas.prohub.autocal.NativeAutoMatchFactorBracket
                import com.omegas.prohub.autocal.NativeAutoMatchFactorBracket.State
                fun main() {
                    check(NativeAutoMatchFactorBracket.evaluate(null, "BB").state == State.INCONCLUSIVE)
                    check(NativeAutoMatchFactorBracket.evaluate("AA", "").state == State.INCONCLUSIVE)
                    check(NativeAutoMatchFactorBracket.evaluate("AA", "AA").state == State.NO_FACTOR_CHANGE_OBSERVED)
                    val changed = NativeAutoMatchFactorBracket.evaluate("AA", "BB")
                    check(changed.state == State.FACTOR_CHANGE_CONFIRMED)
                    check(changed.physicalChangeKnown)
                    check(changed.beforeHash == "AA" && changed.afterHash == "BB")
                    println("OWNER_114_AUTOMATCH_BRACKET=PASS")
                }
            """), encoding="utf-8")
            jar = tmp / "owner114.jar"
            subprocess.run(
                ["kotlinc", str(BRACKET), str(main), "-include-runtime", "-d", str(jar)],
                check=True, capture_output=True, text=True, timeout=30,
            )
            result = subprocess.run(["java", "-jar", str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn("OWNER_114_AUTOMATCH_BRACKET=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
