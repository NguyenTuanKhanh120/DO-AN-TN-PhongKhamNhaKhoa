-- Add two common services for the demo clinic without overwriting any
-- administrator-managed service that already uses the same name.

INSERT INTO dental_services (
    name,
    description,
    price,
    is_active
)
VALUES
    (
        'Trám răng',
        'Phục hồi răng sâu hoặc răng sứt mẻ bằng vật liệu trám phù hợp.',
        450000.00,
        TRUE
    ),
    (
        'Nhổ răng thường',
        'Loại bỏ răng hư tổn không thể bảo tồn sau khi bác sĩ thăm khám.',
        500000.00,
        TRUE
    )
ON CONFLICT (name) DO NOTHING;

-- Keep the new demo services immediately bookable with the doctors already
-- present in the database. Existing assignments are preserved.
INSERT INTO doctor_services (doctor_id, service_id)
SELECT doctor.id, dental_service.id
FROM doctors doctor
CROSS JOIN dental_services dental_service
WHERE dental_service.name IN ('Trám răng', 'Nhổ răng thường')
ON CONFLICT (doctor_id, service_id) DO NOTHING;
