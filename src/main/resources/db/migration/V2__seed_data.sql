-- V2: Seed 5 machines for development
INSERT INTO machines (id, code, name, type, vendor, model, line_id, plant_id, status) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'MC-01', 'CNC Lathe 01',    'CNC_MACHINE',    'SIEMENS',  'SINUMERIK 840D', 'LINE-A', 'PLANT-01', 'RUNNING'),
    ('a1000000-0000-0000-0000-000000000002', 'MC-02', 'CNC Milling 01',  'CNC_MACHINE',    'FANUC',    'Series 0i-MF',   'LINE-A', 'PLANT-01', 'RUNNING'),
    ('a1000000-0000-0000-0000-000000000003', 'MC-03', 'Robot Arm 01',    'ROBOT_ONLY',     'KUKA',     'KR 10 R1100',    'LINE-B', 'PLANT-01', 'IDLE'),
    ('a1000000-0000-0000-0000-000000000004', 'MC-04', 'Robot CNC Cell',  'ROBOT_CNC_CELL', 'LEANTEC',  'LT-500',         'LINE-B', 'PLANT-01', 'RUNNING'),
    ('a1000000-0000-0000-0000-000000000005', 'MC-05', 'CNC Grinding 01', 'CNC_MACHINE',    'SIEMENS',  'SINUMERIK 828D', 'LINE-C', 'PLANT-01', 'STOPPED');

-- Seed thresholds
INSERT INTO machine_thresholds (machine_id, metric_code, warning_value, critical_value, unit) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'TEMPERATURE', 65, 80, '°C'),
    ('a1000000-0000-0000-0000-000000000001', 'VIBRATION',   4.5, 7.0, 'mm/s'),
    ('a1000000-0000-0000-0000-000000000001', 'POWER',       15, 20, 'kW'),
    ('a1000000-0000-0000-0000-000000000002', 'TEMPERATURE', 60, 75, '°C'),
    ('a1000000-0000-0000-0000-000000000002', 'VIBRATION',   5.0, 8.0, 'mm/s'),
    ('a1000000-0000-0000-0000-000000000002', 'POWER',       18, 25, 'kW'),
    ('a1000000-0000-0000-0000-000000000003', 'TEMPERATURE', 55, 70, '°C'),
    ('a1000000-0000-0000-0000-000000000003', 'VIBRATION',   3.0, 5.0, 'mm/s'),
    ('a1000000-0000-0000-0000-000000000004', 'TEMPERATURE', 60, 78, '°C'),
    ('a1000000-0000-0000-0000-000000000004', 'VIBRATION',   4.0, 6.5, 'mm/s'),
    ('a1000000-0000-0000-0000-000000000005', 'TEMPERATURE', 65, 82, '°C'),
    ('a1000000-0000-0000-0000-000000000005', 'VIBRATION',   4.5, 7.5, 'mm/s');

-- Seed tool catalogs
INSERT INTO tool_catalogs (machine_id, tool_code, tool_name, tool_type, life_limit_minutes, life_limit_cycles, warning_threshold_pct, critical_threshold_pct) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'T01', 'Turning Insert CNMG', 'INSERT', 120, 5000, 20, 10),
    ('a1000000-0000-0000-0000-000000000001', 'T02', 'Drill Bit 10mm',     'DRILL',  90,  3000, 25, 10),
    ('a1000000-0000-0000-0000-000000000002', 'T01', 'End Mill 12mm',      'ENDMILL', 100, 4000, 20, 10),
    ('a1000000-0000-0000-0000-000000000002', 'T02', 'Face Mill 50mm',     'FACEMILL',150, 6000, 20, 10),
    ('a1000000-0000-0000-0000-000000000004', 'T01', 'Robot Gripper',      'GRIPPER', 500, 20000, 15, 5),
    ('a1000000-0000-0000-0000-000000000005', 'T01', 'Grinding Wheel A60', 'WHEEL',   200, 8000, 20, 10);

