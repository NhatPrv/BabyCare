-- Export one row per measurement for manual labeling or model training.
-- Adjust the WHERE clause if you want a date range or a single child.
-- Example psql usage:
--   psql -d babycare -f ml/sql/export_growth_dataset.sql -o ml/data/exported_growth.csv

COPY (
    SELECT
        c.id AS child_id,
        m.measured_at AS measurement_date,
        c.dob,
        c.gender,
        m.weight AS weight_kg,
        m.height AS height_cm,
        m.head_circumference AS head_circumference_cm,
        g.classification AS label
    FROM child_measurements m
    JOIN children c ON c.id = m.child_id
    LEFT JOIN growth_assessments g ON g.measurement_id = m.id
    ORDER BY c.id, m.measured_at
) TO STDOUT WITH CSV HEADER;
