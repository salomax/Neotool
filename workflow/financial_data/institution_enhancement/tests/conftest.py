"""
Pytest configuration for institution enhancement tests.

This file ensures that the parent directory is in the Python path
so that tests can import from the 'tasks' module.
"""

import sys
from pathlib import Path

# Add parent directory to Python path for imports
parent_dir = Path(__file__).parent.parent
if str(parent_dir) not in sys.path:
    sys.path.insert(0, str(parent_dir))

# Ensure project root is also available so tests can import workflow.common
project_root = Path(__file__).resolve().parents[4]
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))
