CREATE TABLE system_config (
    config_key       VARCHAR(64)  PRIMARY KEY,
    config_value     VARCHAR(255) NOT NULL,
    description      VARCHAR(512),
    config_group     VARCHAR(64)  NOT NULL DEFAULT 'general',
    requires_restart BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64)  NOT NULL DEFAULT 'system'
);

INSERT INTO system_config (config_key, config_value, description, config_group, requires_restart) VALUES
('ntp.server',                  'pool.ntp.org', 'NTP sunucu adresi veya IP',              'ntp',       false),
('ntp.timeout-ms',              '4000',         'NTP UDP sorgu zaman aşımı (ms)',          'ntp',       false),
('ntp.warn-threshold-ms',       '1000',         'Saat kayması uyarı eşiği (ms)',           'ntp',       false),
('retention.days',              '730',          'Olay kayıtları saklama süresi (gün)',     'retention', false),
('archive.path',                './archive',    'Arşiv ve CSV dizini yolu',               'retention', true),
('security.max-login-attempts', '10',           'IP başına maks. giriş denemesi / dakika', 'security',  false),
('security.login-window-ms',    '60000',        'Giriş denemesi sayım penceresi (ms)',     'security',  false),
('security.jwt-expiry-hours',   '8',            'JWT oturum geçerlilik süresi (saat)',     'security',  true),
('health.disk-warn-pct',        '85',           'Disk doluluk uyarı eşiği (%)',           'health',    false),
('health.check-interval-ms',    '1800000',      'Sistem sağlığı kontrol aralığı (ms)',    'health',    false),
('health.ntp-check-interval-ms','21600000',     'NTP kontrol aralığı (ms)',               'health',    false),
('retention.run-hour',          '2',            'Arşivleme işleminin çalışacağı saat (0-23, UTC)', 'retention', false),
('retention.run-minute',        '0',            'Arşivleme işleminin çalışacağı dakika (0-59)',    'retention', false);
