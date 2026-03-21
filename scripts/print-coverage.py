#!/usr/bin/env python3
import csv
import glob
import sys

def main():
    instructions_missed = 0
    instructions_covered = 0
    branches_missed = 0
    branches_covered = 0
    lines_missed = 0
    lines_covered = 0

    files = glob.glob("*/target/site/jacoco/jacoco.csv")
    if not files:
        print("⚠️  Coverage report not found. Run tests first.")
        sys.exit(0)

    for f in files:
        with open(f, newline="", encoding="utf-8") as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                instructions_missed += int(row["INSTRUCTION_MISSED"])
                instructions_covered += int(row["INSTRUCTION_COVERED"])
                branches_missed += int(row["BRANCH_MISSED"])
                branches_covered += int(row["BRANCH_COVERED"])
                lines_missed += int(row["LINE_MISSED"])
                lines_covered += int(row["LINE_COVERED"])

    def pct(cov, miss):
        return (cov / (cov + miss)) * 100 if (cov + miss) > 0 else 0

    print("\n📊 Test Coverage Report")
    print("=======================")
    print(f"🟢 Line Coverage:   {pct(lines_covered, lines_missed):6.2f}% ({lines_covered}/{lines_covered+lines_missed} lines)")
    print(f"🌿 Branch Coverage: {pct(branches_covered, branches_missed):6.2f}% ({branches_covered}/{branches_covered+branches_missed} branches)")
    print(f"⚙️  Instr Coverage:  {pct(instructions_covered, instructions_missed):6.2f}% ({instructions_covered}/{instructions_covered+instructions_missed} instructions)")
    print("=======================\n")

if __name__ == "__main__":
    main()
