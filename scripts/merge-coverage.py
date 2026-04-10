#!/usr/bin/env python3
"""Merge ganglia-harness unit-test + integration-test JaCoCo exec files,
generate a combined HTML/XML report, and print a summary.

Usage (called by justfile):
    python3 scripts/merge-coverage.py
"""

import subprocess
import sys
import csv
import glob
import shutil
import os

HARNESS_EXEC = "ganglia-harness/target/jacoco.exec"
IT_EXEC = "integration-test/target/jacoco.exec"
MERGED_EXEC = "ganglia-harness/target/jacoco.exec"   # overwrite in-place so jacoco:report picks it up
REPORT_DIR = "ganglia-harness/target/site/jacoco"
REPORT_CSV = f"{REPORT_DIR}/jacoco.csv"


def find_jacoco_jar():
    import glob as g
    jars = g.glob(os.path.expanduser(
        "~/.m2/repository/org/jacoco/org.jacoco.core/*/org.jacoco.core-*.jar"))
    jars = [j for j in jars if "sources" not in j]
    if not jars:
        print("❌  jacoco-core jar not found in ~/.m2. Run 'mvn test' first.")
        sys.exit(1)
    return sorted(jars)[-1]


def merge_exec():
    missing = [p for p in (HARNESS_EXEC, IT_EXEC) if not os.path.exists(p)]
    if missing:
        print(f"❌  Missing exec file(s): {', '.join(missing)}")
        print("    Run 'just coverage-combined' to generate both.")
        sys.exit(1)

    jacoco_jar = find_jacoco_jar()

    # Write a tiny Java helper inline and compile+run it
    merge_src = "/tmp/_MergeExec.java"
    merge_cls = "/tmp"
    with open(merge_src, "w") as f:
        f.write("""\
import org.jacoco.core.tools.ExecFileLoader;
import java.io.File;
public class _MergeExec {
    public static void main(String[] args) throws Exception {
        ExecFileLoader loader = new ExecFileLoader();
        for (int i = 0; i < args.length - 1; i++) loader.load(new File(args[i]));
        loader.save(new File(args[args.length - 1]), false);
    }
}
""")
    subprocess.run(
        ["javac", "-cp", jacoco_jar, merge_src, "-d", merge_cls],
        check=True, capture_output=True)
    subprocess.run(
        ["java", "-cp", f"{merge_cls}:{jacoco_jar}", "_MergeExec",
         HARNESS_EXEC, IT_EXEC, MERGED_EXEC],
        check=True, capture_output=True)
    print(f"✅  Merged exec (unit + IT) → {MERGED_EXEC}")


def generate_report():
    result = subprocess.run(
        ["mvn", "jacoco:report", "-pl", "ganglia-harness", "-q"],
        capture_output=True, text=True)
    if result.returncode != 0:
        print("❌  jacoco:report failed:")
        print(result.stderr[-2000:])
        sys.exit(1)
    print(f"✅  Report → {REPORT_DIR}/index.html")


def print_summary():
    if not os.path.exists(REPORT_CSV):
        print("⚠️  CSV report not found — skipping summary.")
        return

    im = ic = bm = bc = lm = lc = 0
    with open(REPORT_CSV, newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            im += int(row["INSTRUCTION_MISSED"])
            ic += int(row["INSTRUCTION_COVERED"])
            bm += int(row["BRANCH_MISSED"])
            bc += int(row["BRANCH_COVERED"])
            lm += int(row["LINE_MISSED"])
            lc += int(row["LINE_COVERED"])

    def pct(cov, miss):
        return cov / (cov + miss) * 100 if (cov + miss) else 0

    print()
    print("📊 Combined Coverage Report  (unit + integration tests)")
    print("=========================================================")
    print(f"🟢 Line Coverage:   {pct(lc,lm):6.2f}%  ({lc}/{lc+lm} lines)")
    print(f"🌿 Branch Coverage: {pct(bc,bm):6.2f}%  ({bc}/{bc+bm} branches)")
    print(f"⚙️  Instr Coverage:  {pct(ic,im):6.2f}%  ({ic}/{ic+im} instructions)")
    print("=========================================================")
    print()


if __name__ == "__main__":
    merge_exec()
    generate_report()
    print_summary()
