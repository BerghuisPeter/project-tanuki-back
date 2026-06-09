INSERT INTO goshuin_schema.goshuin
(id,
 format,
 temple_id,
 pages,
 start_date,
 user_id)

-- add i18n en
SELECT gen_random_uuid(),
       (ARRAY ['paper','written','cut','other'])
           [1 + floor(random() * 4)::int],
       t.temple_id,
       1 + floor(random() * 2)::int,
       DATE '2025-01-01' + (g.n * 7),
       '00000000-0000-0000-0000-000000000000'
FROM (VALUES ('11111111-1111-1111-1111-111111111111'::uuid),
             ('22222222-2222-2222-2222-222222222222'::uuid),
             ('33333333-3333-3333-3333-333333333333'::uuid),
             ('44444444-4444-4444-4444-444444444444'::uuid),
             ('55555555-5555-5555-5555-555555555555'::uuid)) AS t(temple_id)
         CROSS JOIN generate_series(1, 10) g(n);

INSERT INTO goshuin_schema.goshuin_i18n
(goshuin_id,
 locale,
 label,
 description)
SELECT g.id,
       'en',
       CASE g.format
           WHEN 'written' THEN 'Handwritten Goshuin'
           WHEN 'paper' THEN 'Paper Goshuin'
           WHEN 'cut' THEN 'Kirie Goshuin'
           WHEN 'other' THEN 'Special Goshuin'
           ELSE 'Goshuin'
           END,
       CASE g.format
           WHEN 'written'
               THEN 'A goshuin written directly into a goshuincho by the temple or shrine.'
           WHEN 'paper'
               THEN 'A pre-written goshuin provided on a separate sheet of paper.'
           WHEN 'cut'
               THEN 'An artistic kirie goshuin featuring decorative cut-paper designs.'
           WHEN 'other'
               THEN 'A unique goshuin with a special design or commemorative theme.'
           ELSE
               'Traditional Japanese temple or shrine seal.'
           END
FROM goshuin_schema.goshuin g;

-- add i18n jp
INSERT INTO goshuin_schema.goshuin_i18n
(goshuin_id,
 locale,
 label,
 description)
SELECT g.id,
       'ja',
       CASE g.format
           WHEN 'written' THEN '直書き御朱印'
           WHEN 'paper' THEN '書き置き御朱印'
           WHEN 'cut' THEN '切り絵御朱印'
           WHEN 'other' THEN '特別御朱印'
           ELSE '御朱印'
           END,
       CASE g.format
           WHEN 'written'
               THEN '御朱印帳に直接書いていただく御朱印です。'
           WHEN 'paper'
               THEN 'あらかじめ書かれた紙で授与される御朱印です。'
           WHEN 'cut'
               THEN '切り絵を用いた装飾的な御朱印です。'
           WHEN 'other'
               THEN '特別な行事や記念日に授与される御朱印です。'
           ELSE
               '寺社で授与される伝統的な御朱印です。'
           END
FROM goshuin_schema.goshuin g;

-- goshuin img
INSERT INTO goshuin_schema.goshuin_image
(id,
 goshuin_id,
 image_url)
SELECT gen_random_uuid(),
       g.id,
       CASE g.format
           WHEN 'written'
               THEN 'https://storage.googleapis.com/tanuki-dev-assets/goshuin/Goshuin-Shikoku.png'
           WHEN 'paper'
               THEN 'https://storage.googleapis.com/tanuki-dev-assets/goshuin/08_koudaiji_7-1024x1024.webp'
           WHEN 'cut'
               THEN 'https://storage.googleapis.com/tanuki-dev-assets/goshuin/70047-40-0dadd6d61e93351e693dd871d56d6737-1902x1902.jpg'
           WHEN 'other'
               THEN 'https://storage.googleapis.com/tanuki-dev-assets/goshuin/04_koudaiji_3-1024x1024.webp'
           END
FROM goshuin_schema.goshuin g;