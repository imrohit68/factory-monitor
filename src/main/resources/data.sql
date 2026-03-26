-- Default factory grid (replaces former DefaultWorkstationSeedService Java seed).
-- 16 columns WC1..WC16: Engineer bits 0..15 / Leader 16..31 / Quality 32..47;
-- relays 1..32 on output slave 2, 33..48 on output slave 3 (Quality uses relay 1..16 on slave 3).
-- Idempotent: skips workstation insert if any workstation exists; skips slot insert if any slot exists.
-- Edit output slave IDs (2 and 3 in the UNION below) if they differ from your Modbus relay boards.
-- SQL written for SQLite (scalar subquery for workstation_id avoids JOIN/UNION quirks).

INSERT INTO workstation (name, sort_order, enabled)
SELECT * FROM (
    SELECT 'WC1' AS name, 0 AS sort_order, 1 AS enabled UNION ALL
    SELECT 'WC2', 1, 1 UNION ALL
    SELECT 'WC3', 2, 1 UNION ALL
    SELECT 'WC4', 3, 1 UNION ALL
    SELECT 'WC5', 4, 1 UNION ALL
    SELECT 'WC6', 5, 1 UNION ALL
    SELECT 'WC7', 6, 1 UNION ALL
    SELECT 'WC8', 7, 1 UNION ALL
    SELECT 'WC9', 8, 1 UNION ALL
    SELECT 'WC10', 9, 1 UNION ALL
    SELECT 'WC11', 10, 1 UNION ALL
    SELECT 'WC12', 11, 1 UNION ALL
    SELECT 'WC13', 12, 1 UNION ALL
    SELECT 'WC14', 13, 1 UNION ALL
    SELECT 'WC15', 14, 1 UNION ALL
    SELECT 'WC16', 15, 1
) AS t
WHERE NOT EXISTS (SELECT 1 FROM workstation);

INSERT INTO workstation_slot (workstation_id, role, input_bit_index, output_slave_id, output_channel, audio_path)
SELECT
    (SELECT w.id FROM workstation w WHERE w.name = slots.ws_name),
    slots.role,
    slots.input_bit,
    slots.slave,
    slots.relay,
    NULL
FROM (
    SELECT 'WC1' AS ws_name, 'ENGINEER' AS role, 0 AS input_bit, 2 AS slave, 1 AS relay UNION ALL
    SELECT 'WC1', 'LEADER', 16, 2, 17 UNION ALL
    SELECT 'WC1', 'QUALITY', 32, 3, 1 UNION ALL
    SELECT 'WC2', 'ENGINEER', 1, 2, 2 UNION ALL
    SELECT 'WC2', 'LEADER', 17, 2, 18 UNION ALL
    SELECT 'WC2', 'QUALITY', 33, 3, 2 UNION ALL
    SELECT 'WC3', 'ENGINEER', 2, 2, 3 UNION ALL
    SELECT 'WC3', 'LEADER', 18, 2, 19 UNION ALL
    SELECT 'WC3', 'QUALITY', 34, 3, 3 UNION ALL
    SELECT 'WC4', 'ENGINEER', 3, 2, 4 UNION ALL
    SELECT 'WC4', 'LEADER', 19, 2, 20 UNION ALL
    SELECT 'WC4', 'QUALITY', 35, 3, 4 UNION ALL
    SELECT 'WC5', 'ENGINEER', 4, 2, 5 UNION ALL
    SELECT 'WC5', 'LEADER', 20, 2, 21 UNION ALL
    SELECT 'WC5', 'QUALITY', 36, 3, 5 UNION ALL
    SELECT 'WC6', 'ENGINEER', 5, 2, 6 UNION ALL
    SELECT 'WC6', 'LEADER', 21, 2, 22 UNION ALL
    SELECT 'WC6', 'QUALITY', 37, 3, 6 UNION ALL
    SELECT 'WC7', 'ENGINEER', 6, 2, 7 UNION ALL
    SELECT 'WC7', 'LEADER', 22, 2, 23 UNION ALL
    SELECT 'WC7', 'QUALITY', 38, 3, 7 UNION ALL
    SELECT 'WC8', 'ENGINEER', 7, 2, 8 UNION ALL
    SELECT 'WC8', 'LEADER', 23, 2, 24 UNION ALL
    SELECT 'WC8', 'QUALITY', 39, 3, 8 UNION ALL
    SELECT 'WC9', 'ENGINEER', 8, 2, 9 UNION ALL
    SELECT 'WC9', 'LEADER', 24, 2, 25 UNION ALL
    SELECT 'WC9', 'QUALITY', 40, 3, 9 UNION ALL
    SELECT 'WC10', 'ENGINEER', 9, 2, 10 UNION ALL
    SELECT 'WC10', 'LEADER', 25, 2, 26 UNION ALL
    SELECT 'WC10', 'QUALITY', 41, 3, 10 UNION ALL
    SELECT 'WC11', 'ENGINEER', 10, 2, 11 UNION ALL
    SELECT 'WC11', 'LEADER', 26, 2, 27 UNION ALL
    SELECT 'WC11', 'QUALITY', 42, 3, 11 UNION ALL
    SELECT 'WC12', 'ENGINEER', 11, 2, 12 UNION ALL
    SELECT 'WC12', 'LEADER', 27, 2, 28 UNION ALL
    SELECT 'WC12', 'QUALITY', 43, 3, 12 UNION ALL
    SELECT 'WC13', 'ENGINEER', 12, 2, 13 UNION ALL
    SELECT 'WC13', 'LEADER', 28, 2, 29 UNION ALL
    SELECT 'WC13', 'QUALITY', 44, 3, 13 UNION ALL
    SELECT 'WC14', 'ENGINEER', 13, 2, 14 UNION ALL
    SELECT 'WC14', 'LEADER', 29, 2, 30 UNION ALL
    SELECT 'WC14', 'QUALITY', 45, 3, 14 UNION ALL
    SELECT 'WC15', 'ENGINEER', 14, 2, 15 UNION ALL
    SELECT 'WC15', 'LEADER', 30, 2, 31 UNION ALL
    SELECT 'WC15', 'QUALITY', 46, 3, 15 UNION ALL
    SELECT 'WC16', 'ENGINEER', 15, 2, 16 UNION ALL
    SELECT 'WC16', 'LEADER', 31, 2, 32 UNION ALL
    SELECT 'WC16', 'QUALITY', 47, 3, 16
) AS slots
WHERE NOT EXISTS (SELECT 1 FROM workstation_slot);
