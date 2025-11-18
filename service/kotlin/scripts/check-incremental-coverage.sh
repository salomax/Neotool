#!/bin/bash
# Script to check incremental code coverage for PRs
# Only validates coverage for lines changed in the PR, not the entire codebase

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
COVERAGE_THRESHOLD=${COVERAGE_THRESHOLD:-80}
BASE_BRANCH=${BASE_BRANCH:-main}
MODULE=${1:-""}

if [ -z "$MODULE" ]; then
    echo -e "${RED}Error: Module name is required${NC}"
    echo "Usage: $0 <module-name> [base-branch]"
    exit 1
fi

# Get base branch from argument or use default
if [ -n "$2" ]; then
    BASE_BRANCH=$2
fi

echo -e "${YELLOW}Checking incremental coverage for module: $MODULE${NC}"
echo -e "${YELLOW}Base branch: $BASE_BRANCH${NC}"
echo -e "${YELLOW}Coverage threshold: ${COVERAGE_THRESHOLD}%${NC}"

# Check if we're in a CI environment (GitHub Actions)
if [ -n "$GITHUB_BASE_REF" ]; then
    BASE_BRANCH=$GITHUB_BASE_REF
    echo -e "${YELLOW}Detected GitHub Actions, using base ref: $BASE_BRANCH${NC}"
fi

# Get the current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "HEAD")

# Check if we're in a PR context
if [ -n "$GITHUB_HEAD_REF" ]; then
    CURRENT_BRANCH=$GITHUB_HEAD_REF
    echo -e "${YELLOW}Detected PR branch: $CURRENT_BRANCH${NC}"
fi

# Get changed files in the module
echo -e "${YELLOW}Finding changed files in module: $MODULE${NC}"
CHANGED_FILES=$(git diff --name-only "$BASE_BRANCH...$CURRENT_BRANCH" -- "service/kotlin/$MODULE/src/main/**/*.kt" 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
    echo -e "${GREEN}No Kotlin files changed in module $MODULE, skipping coverage check${NC}"
    exit 0
fi

echo -e "${YELLOW}Changed files:${NC}"
echo "$CHANGED_FILES" | sed 's/^/  - /'

# Check if coverage report exists
COVERAGE_XML="service/kotlin/$MODULE/build/reports/jacoco/test/jacocoTestReport.xml"
if [ ! -f "$COVERAGE_XML" ]; then
    echo -e "${RED}Error: Coverage report not found at $COVERAGE_XML${NC}"
    echo -e "${YELLOW}Please run: ./gradlew :$MODULE:test :$MODULE:jacocoTestReport${NC}"
    exit 1
fi

# Use Python to parse JaCoCo XML and check coverage for changed lines
python3 << EOF
import xml.etree.ElementTree as ET
import subprocess
import sys
import re
from pathlib import Path

# Parse JaCoCo XML report
tree = ET.parse('$COVERAGE_XML')
root = tree.getroot()

# Get changed files
changed_files = """$CHANGED_FILES""".strip().split('\n')
changed_files = [f for f in changed_files if f.endswith('.kt')]

if not changed_files:
    print("✅ No changed files to check")
    sys.exit(0)

# Get diff to find changed lines
base_branch = "$BASE_BRANCH"
current_branch = "$CURRENT_BRANCH"

total_covered = 0
total_missed = 0
files_checked = []

for changed_file in changed_files:
    # Get changed lines from git diff
    try:
        diff_output = subprocess.check_output(
            ['git', 'diff', f'{base_branch}...{current_branch}', '--', changed_file],
            stderr=subprocess.DEVNULL
        ).decode('utf-8')
    except:
        continue
    
    # Extract line numbers that were added/modified
    changed_lines = set()
    current_line = 0
    for line in diff_output.split('\n'):
        if line.startswith('@@'):
            # Parse hunk header: @@ -start,count +start,count @@
            match = re.search(r'\+(\d+)', line)
            if match:
                current_line = int(match.group(1)) - 1
        elif line.startswith('+') and not line.startswith('+++'):
            current_line += 1
            changed_lines.add(current_line)
        elif not line.startswith('-') and not line.startswith('\\'):
            current_line += 1
    
    if not changed_lines:
        continue
    
    # Find package in JaCoCo report
    # Convert file path to package path
    # service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/service/Services.kt
    # -> io/github/salomax/neotool/example/service/Services
    file_path = Path(changed_file)
    if 'src/main/kotlin/' in str(file_path):
        package_path = str(file_path).split('src/main/kotlin/')[1].replace('.kt', '').replace('/', '.')
    else:
        continue
    
    # Find class in JaCoCo report
    class_name = file_path.stem
    found_class = False
    
    for package_elem in root.findall('.//package'):
        if package_elem.get('name') in package_path or package_path.endswith(package_elem.get('name')):
            for class_elem in package_elem.findall('.//class'):
                if class_name in class_elem.get('name', ''):
                    found_class = True
                    # Check coverage for changed lines
                    for sourcefile in package_elem.findall('.//sourcefile'):
                        if sourcefile.get('name') == file_path.name:
                            for line_elem in sourcefile.findall('.//line'):
                                line_num = int(line_elem.get('nr'))
                                if line_num in changed_lines:
                                    mi = int(line_elem.get('mi', 0))  # missed instructions
                                    ci = int(line_elem.get('ci', 0))  # covered instructions
                                    total_missed += mi
                                    total_covered += ci
                                    files_checked.append(f"{changed_file}:{line_num}")
                            break
                    break
    
    if not found_class:
        print(f"⚠️  Warning: Could not find coverage data for {changed_file}")

if total_covered + total_missed == 0:
    print("✅ No executable lines changed, coverage check passed")
    sys.exit(0)

coverage_percent = (total_covered / (total_covered + total_missed)) * 100
threshold = float("$COVERAGE_THRESHOLD")

print(f"\n{'='*80}")
print(f"Incremental Coverage Report for Module: $MODULE")
print(f"{'='*80}")
print(f"Files checked: {len(set(f.split(':')[0] for f in files_checked))}")
print(f"Lines checked: {len(files_checked)}")
print(f"Covered instructions: {total_covered}")
print(f"Missed instructions: {total_missed}")
print(f"Coverage: {coverage_percent:.2f}%")
print(f"Threshold: {threshold}%")
print(f"{'='*80}")

if coverage_percent < threshold:
    print(f"❌ Coverage {coverage_percent:.2f}% is below threshold {threshold}%")
    print(f"Please add tests for the changed lines")
    sys.exit(1)
else:
    print(f"✅ Coverage {coverage_percent:.2f}% meets threshold {threshold}%")
    sys.exit(0)
EOF

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Incremental coverage check passed${NC}"
else
    echo -e "${RED}❌ Incremental coverage check failed${NC}"
fi

exit $EXIT_CODE

