from pathlib import Path



def test_schema_contains_required_comments_and_defaults() -> None:
    sql = Path("docs/schema.sql").read_text(encoding="utf-8")

    assert "comment on table ace_source" in sql
    assert "comment on column ace_source.workspace_key" in sql
    assert "not null" in sql
    assert "default ''" in sql
    assert "create index" in sql
