-- Default factory grid: workstations named 1–12, then 21–25 (17 columns).
-- Input bits: per workstation, 3 consecutive bits — ENGINEER, LEADER, QUALITY (Quality Controller role)
--   (WS "1" → bits 0,1,2; WS "2" → 3,4,5; … up to WS "25" → 48,49,50).
-- Output relays: slave 2 uses channels 1–30 in order (first 10 workstations),
--   then slave 3 uses channels 1–21 in order (remaining 7 workstations).
-- Idempotent: skips if any workstation / slot already exists.
-- Existing DBs: changing this file does not alter rows already in workstation_slot.
-- One-time remap (old slave 2→3, then old 1→2) on SQLite, if you still see Slv:1 in admin:
--   UPDATE workstation_slot SET output_slave_id = 3 WHERE output_slave_id = 2;
--   UPDATE workstation_slot SET output_slave_id = 2 WHERE output_slave_id = 1;
-- Or stop the app, delete the SQLite file under system.data-dir, and restart to re-seed.

INSERT INTO workstation (name, sort_order, enabled)
SELECT * FROM (
    SELECT '1' AS name, 0 AS sort_order, 1 AS enabled UNION ALL
    SELECT '2', 1, 1 UNION ALL
    SELECT '3', 2, 1 UNION ALL
    SELECT '4', 3, 1 UNION ALL
    SELECT '5', 4, 1 UNION ALL
    SELECT '6', 5, 1 UNION ALL
    SELECT '7', 6, 1 UNION ALL
    SELECT '8', 7, 1 UNION ALL
    SELECT '9', 8, 1 UNION ALL
    SELECT '10', 9, 1 UNION ALL
    SELECT '11', 10, 1 UNION ALL
    SELECT '12', 11, 1 UNION ALL
    SELECT '21', 12, 1 UNION ALL
    SELECT '22', 13, 1 UNION ALL
    SELECT '23', 14, 1 UNION ALL
    SELECT '24', 15, 1 UNION ALL
    SELECT '25', 16, 1
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
    SELECT '1' AS ws_name, 'ENGINEER' AS role, 0 AS input_bit, 2 AS slave, 1 AS relay UNION ALL
    SELECT '1', 'LEADER', 1, 2, 2 UNION ALL
    SELECT '1', 'QUALITY', 2, 2, 3 UNION ALL
    SELECT '2', 'ENGINEER', 3, 2, 4 UNION ALL
    SELECT '2', 'LEADER', 4, 2, 5 UNION ALL
    SELECT '2', 'QUALITY', 5, 2, 6 UNION ALL
    SELECT '3', 'ENGINEER', 6, 2, 7 UNION ALL
    SELECT '3', 'LEADER', 7, 2, 8 UNION ALL
    SELECT '3', 'QUALITY', 8, 2, 9 UNION ALL
    SELECT '4', 'ENGINEER', 9, 2, 10 UNION ALL
    SELECT '4', 'LEADER', 10, 2, 11 UNION ALL
    SELECT '4', 'QUALITY', 11, 2, 12 UNION ALL
    SELECT '5', 'ENGINEER', 12, 2, 13 UNION ALL
    SELECT '5', 'LEADER', 13, 2, 14 UNION ALL
    SELECT '5', 'QUALITY', 14, 2, 15 UNION ALL
    SELECT '6', 'ENGINEER', 15, 2, 16 UNION ALL
    SELECT '6', 'LEADER', 16, 2, 17 UNION ALL
    SELECT '6', 'QUALITY', 17, 2, 18 UNION ALL
    SELECT '7', 'ENGINEER', 18, 2, 19 UNION ALL
    SELECT '7', 'LEADER', 19, 2, 20 UNION ALL
    SELECT '7', 'QUALITY', 20, 2, 21 UNION ALL
    SELECT '8', 'ENGINEER', 21, 2, 22 UNION ALL
    SELECT '8', 'LEADER', 22, 2, 23 UNION ALL
    SELECT '8', 'QUALITY', 23, 2, 24 UNION ALL
    SELECT '9', 'ENGINEER', 24, 2, 25 UNION ALL
    SELECT '9', 'LEADER', 25, 2, 26 UNION ALL
    SELECT '9', 'QUALITY', 26, 2, 27 UNION ALL
    SELECT '10', 'ENGINEER', 27, 2, 28 UNION ALL
    SELECT '10', 'LEADER', 28, 2, 29 UNION ALL
    SELECT '10', 'QUALITY', 29, 2, 30 UNION ALL
    SELECT '11', 'ENGINEER', 30, 3, 1 UNION ALL
    SELECT '11', 'LEADER', 31, 3, 2 UNION ALL
    SELECT '11', 'QUALITY', 32, 3, 3 UNION ALL
    SELECT '12', 'ENGINEER', 33, 3, 4 UNION ALL
    SELECT '12', 'LEADER', 34, 3, 5 UNION ALL
    SELECT '12', 'QUALITY', 35, 3, 6 UNION ALL
    SELECT '21', 'ENGINEER', 36, 3, 7 UNION ALL
    SELECT '21', 'LEADER', 37, 3, 8 UNION ALL
    SELECT '21', 'QUALITY', 38, 3, 9 UNION ALL
    SELECT '22', 'ENGINEER', 39, 3, 10 UNION ALL
    SELECT '22', 'LEADER', 40, 3, 11 UNION ALL
    SELECT '22', 'QUALITY', 41, 3, 12 UNION ALL
    SELECT '23', 'ENGINEER', 42, 3, 13 UNION ALL
    SELECT '23', 'LEADER', 43, 3, 14 UNION ALL
    SELECT '23', 'QUALITY', 44, 3, 15 UNION ALL
    SELECT '24', 'ENGINEER', 45, 3, 16 UNION ALL
    SELECT '24', 'LEADER', 46, 3, 17 UNION ALL
    SELECT '24', 'QUALITY', 47, 3, 18 UNION ALL
    SELECT '25', 'ENGINEER', 48, 3, 19 UNION ALL
    SELECT '25', 'LEADER', 49, 3, 20 UNION ALL
    SELECT '25', 'QUALITY', 50, 3, 21
) AS slots
WHERE NOT EXISTS (SELECT 1 FROM workstation_slot);
