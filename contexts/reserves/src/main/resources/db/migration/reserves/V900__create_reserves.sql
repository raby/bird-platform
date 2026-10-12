-- Reserves context: a geospatial catalogue of nature reserves. The distinctive capability is "find
-- reserves near a point", answered with PostGIS: a geography(Point) column, a GIST index, and
-- ST_DWithin / ST_Distance. Reserves are a read-only seeded catalogue here; the showcase is the query.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE SCHEMA IF NOT EXISTS reserves;

CREATE TABLE reserves.reserve (
    id        UUID                   PRIMARY KEY,
    name      TEXT                   NOT NULL,
    region    TEXT                   NOT NULL,
    latitude  DOUBLE PRECISION       NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION       NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    -- The spatial representation the distance query runs against; kept alongside the plain lat/lng,
    -- which stay the readable source of truth. 4326 is WGS 84 (GPS) lat/lng.
    location  geography(Point, 4326) NOT NULL
);

-- Makes ST_DWithin / nearest-first ordering index-assisted rather than a full scan.
CREATE INDEX idx_reserve_location ON reserves.reserve USING GIST (location);

-- Real UK reserves (the four that the demo hides belong to) plus a Somerset Levels cluster, so a
-- search around Somerset returns several ordered by distance while far-flung reserves are excluded.
-- ST_MakePoint takes (longitude, latitude).
INSERT INTO reserves.reserve (id, name, region, latitude, longitude, location) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Leighton Moss',  'Lancashire', 54.1655, -2.7920, ST_MakePoint(-2.7920, 54.1655)::geography),
    ('20000000-0000-0000-0000-000000000002', 'Loch Garten',    'Cairngorms', 57.2447, -3.6983, ST_MakePoint(-3.6983, 57.2447)::geography),
    ('20000000-0000-0000-0000-000000000003', 'Ham Wall',       'Somerset',   51.1510, -2.7820, ST_MakePoint(-2.7820, 51.1510)::geography),
    ('20000000-0000-0000-0000-000000000004', 'Minsmere',       'Suffolk',    52.2480,  1.6230, ST_MakePoint(1.6230, 52.2480)::geography),
    ('20000000-0000-0000-0000-000000000005', 'Shapwick Heath', 'Somerset',   51.1480, -2.8100, ST_MakePoint(-2.8100, 51.1480)::geography),
    ('20000000-0000-0000-0000-000000000006', 'Greylake',       'Somerset',   51.1100, -2.8800, ST_MakePoint(-2.8800, 51.1100)::geography),
    ('20000000-0000-0000-0000-000000000007', 'Fairburn Ings',  'Yorkshire',  53.7300, -1.3100, ST_MakePoint(-1.3100, 53.7300)::geography);
