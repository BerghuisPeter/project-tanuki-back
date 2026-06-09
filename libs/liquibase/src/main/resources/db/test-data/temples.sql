-- =====================================================
-- TEMPLES
-- =====================================================

INSERT INTO goshuin_schema.temple
(id, longitude, latitude, affiliation_type, website_url, phone_number, goshuin_service_open_until, image_url)
VALUES ('11111111-1111-1111-1111-111111111111',
        139.796655,
        35.714765,
        'buddhist',
        'https://www.zojoji.or.jp',
        '03-3432-1431',
        '16:00',
        'https://example.com/zojoji.jpg'),
       ('22222222-2222-2222-2222-222222222222',
        139.535954,
        35.714844,
        'shinto',
        'https://www.musashino-jinja.jp',
        '0422-51-1234',
        '16:30',
        'https://example.com/musashino.jpg'),
       ('33333333-3333-3333-3333-333333333333',
        139.699325,
        35.689932,
        'shinto',
        'https://www.hie.or.jp',
        '03-3581-2471',
        '17:00',
        'https://example.com/hie.jpg'),
       ('44444444-4444-4444-4444-444444444444',
        139.047583,
        35.232383,
        'buddhist',
        'https://www.hasedera.jp',
        '0467-22-6300',
        '16:00',
        'https://example.com/hasedera.jpg'),
       ('55555555-5555-5555-5555-555555555555',
        139.483840,
        35.360570,
        'shinto',
        'https://www.samukawa-jinja.or.jp',
        '0467-75-0004',
        '16:30',
        'https://example.com/samukawa.jpg');

INSERT INTO goshuin_schema.temple_i18n
    (temple_id, locale, name, address, prefecture, city)
VALUES ('11111111-1111-1111-1111-111111111111', 'en', 'Zojoji Temple', '4-7-35 Shibakoen', 'Tokyo', 'Minato'),
       ('22222222-2222-2222-2222-222222222222', 'en', 'Musashino Hachimangu', '1-1-1 Kichijoji', 'Tokyo', 'Musashino'),
       ('33333333-3333-3333-3333-333333333333', 'en', 'Hie Shrine', '2-10-5 Nagatacho', 'Tokyo', 'Chiyoda'),
       ('44444444-4444-4444-4444-444444444444', 'en', 'Hasedera Temple', '3-11-2 Hase', 'Kanagawa', 'Kamakura'),
       ('55555555-5555-5555-5555-555555555555', 'en', 'Samukawa Shrine', '3916 Miyayama', 'Kanagawa', 'Samukawa');