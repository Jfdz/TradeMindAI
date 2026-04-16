"""create ai_engine schema with model_versions, training_runs, predictions

Revision ID: 0001
Revises:
Create Date: 2026-04-16

"""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE SCHEMA IF NOT EXISTS ai_engine")

    op.create_table(
        "model_versions",
        sa.Column("id", sa.UUID(), server_default=sa.text("gen_random_uuid()"), primary_key=True),
        sa.Column("version_tag", sa.String(64), nullable=False, unique=True),
        sa.Column("architecture", sa.String(64), nullable=False),
        sa.Column("metrics", sa.JSON(), nullable=True),
        sa.Column("artifact_path", sa.Text(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("created_at", sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text("NOW()")),
        schema="ai_engine",
    )

    op.create_table(
        "training_runs",
        sa.Column("id", sa.UUID(), server_default=sa.text("gen_random_uuid()"), primary_key=True),
        sa.Column("model_version_id", sa.UUID(), sa.ForeignKey("ai_engine.model_versions.id"), nullable=True),
        sa.Column("status", sa.String(32), nullable=False, server_default="PENDING"),
        sa.Column("hyperparameters", sa.JSON(), nullable=True),
        sa.Column("metrics", sa.JSON(), nullable=True),
        sa.Column("started_at", sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column("finished_at", sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column("created_at", sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text("NOW()")),
        schema="ai_engine",
    )

    op.create_table(
        "predictions",
        sa.Column("id", sa.BigInteger(), autoincrement=True, primary_key=True),
        sa.Column("model_version_id", sa.UUID(), sa.ForeignKey("ai_engine.model_versions.id"), nullable=False),
        sa.Column("symbol", sa.String(16), nullable=False),
        sa.Column("direction", sa.String(8), nullable=False),
        sa.Column("confidence", sa.Numeric(5, 4), nullable=False),
        sa.Column("raw_logits", sa.JSON(), nullable=True),
        sa.Column("predicted_at", sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text("NOW()")),
        schema="ai_engine",
    )

    op.create_index("idx_model_versions_is_active", "model_versions", ["is_active"], schema="ai_engine")
    op.create_index("idx_predictions_symbol_predicted_at", "predictions", ["symbol", "predicted_at"], schema="ai_engine")
    op.create_index("idx_training_runs_status", "training_runs", ["status"], schema="ai_engine")


def downgrade() -> None:
    op.drop_table("predictions", schema="ai_engine")
    op.drop_table("training_runs", schema="ai_engine")
    op.drop_table("model_versions", schema="ai_engine")
    op.execute("DROP SCHEMA IF EXISTS ai_engine")
