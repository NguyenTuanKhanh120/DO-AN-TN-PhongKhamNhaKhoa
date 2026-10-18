-- PHASE 26.2: direct payment collection at the clinic counter.
-- Service names and prices are stored as JSONB snapshots in payments.items.

DO $$ BEGIN
    CREATE TYPE payment_status_enum AS ENUM ('DRAFT', 'UNPAID', 'PAID');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE payment_method_enum AS ENUM ('CASH', 'BANK_TRANSFER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id      UUID NOT NULL,
    items               JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              payment_status_enum NOT NULL DEFAULT 'DRAFT',
    payment_method      payment_method_enum,
    confirmed_by        UUID,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_payments_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_payments_total_amount_non_negative
        CHECK (total_amount >= 0),
    CONSTRAINT ck_payments_items_array
        CHECK (jsonb_typeof(items) = 'array'),
    CONSTRAINT ck_payments_paid_metadata
        CHECK (
            (status IN ('DRAFT', 'UNPAID')
                AND payment_method IS NULL
                AND confirmed_by IS NULL
                AND paid_at IS NULL)
            OR
            (status = 'PAID'
                AND payment_method IS NOT NULL
                AND confirmed_by IS NOT NULL
                AND paid_at IS NOT NULL)
        ),
    CONSTRAINT ck_payments_paid_items_non_empty
        CHECK (
            status <> 'PAID'
            OR CASE
                WHEN jsonb_typeof(items) = 'array'
                    THEN jsonb_array_length(items) > 0
                ELSE FALSE
            END
        )
);

CREATE INDEX ix_payments_status_created_at
    ON payments (status, created_at DESC);

CREATE INDEX ix_payments_paid_at
    ON payments (paid_at DESC)
    WHERE status = 'PAID';

CREATE TRIGGER trg_payments_updated_at
BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
