#!/usr/bin/env python3
"""
scripts/generate_repo.py:
User-facing script to generate Kaziyami repository indexes.
Supports Mode A (Aniyomi APK repo) and Mode B (Kototoro JAR repo).
"""
import os
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import generate_repo_index

if __name__ == "__main__":
    sys.exit(generate_repo_index.main())
