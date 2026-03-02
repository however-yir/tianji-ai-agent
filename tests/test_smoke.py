from pathlib import Path


def test_repo_smoke():
    assert Path("README.md").exists()

