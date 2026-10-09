-- =============================================================
-- 医院信息系统 HIS 数据库初始化脚本
-- 数据库: MySQL 5.7+ / 8.0   字符集: utf8mb4
-- 约定：表不使用外键，关系通过业务字段维护；查询分页排序走主键索引
-- 默认账号: admin / doctor / pharmacist，密码均为 admin123
-- =============================================================
CREATE DATABASE IF NOT EXISTS his DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE his;

-- ---------- 系统用户 ----------
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    username     VARCHAR(64)  NOT NULL COMMENT '登录名',
    password     VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    real_name    VARCHAR(64)           DEFAULT NULL,
    role         VARCHAR(32)  NOT NULL COMMENT 'admin/doctor/pharmacist/nurse',
    phone        VARCHAR(64)           DEFAULT NULL COMMENT '加密存储',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- 密码 admin123 的 BCrypt 哈希（rounds=10）
INSERT INTO sys_user (username, password, real_name, role, status) VALUES
('admin',      '$2b$10$4NAp0XtPfhj8z75hz5Lm4e3.HKt8mu9IMTy.nLR03xIv.RZckn/qe', '系统管理员', 'admin',      1),
('doctor',     '$2b$10$4NAp0XtPfhj8z75hz5Lm4e3.HKt8mu9IMTy.nLR03xIv.RZckn/qe', '张医生',     'doctor',     1),
('pharmacist', '$2b$10$4NAp0XtPfhj8z75hz5Lm4e3.HKt8mu9IMTy.nLR03xIv.RZckn/qe', '李药师',     'pharmacist', 1),
('nurse',      '$2b$10$4NAp0XtPfhj8z75hz5Lm4e3.HKt8mu9IMTy.nLR03xIv.RZckn/qe', '王护士',     'nurse',      1);

-- ---------- 患者档案 ----------
CREATE TABLE IF NOT EXISTS patient (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    patient_no      VARCHAR(32)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    id_card         VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM 加密',
    phone           VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM 加密',
    gender          VARCHAR(8)            DEFAULT NULL,
    birth_date      DATE                  DEFAULT NULL,
    address         VARCHAR(255)          DEFAULT NULL,
    allergy_history VARCHAR(500)          DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_no (patient_no),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者档案';

-- ---------- 医生排班号源 ----------
CREATE TABLE IF NOT EXISTS doctor_schedule (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    doctor_id    BIGINT        NOT NULL,
    doctor_name  VARCHAR(64)   NOT NULL,
    department   VARCHAR(64)   NOT NULL,
    visit_date   DATE          NOT NULL,
    time_slot    VARCHAR(32)   NOT NULL COMMENT '上午/下午',
    total_count  INT           NOT NULL DEFAULT 20,
    booked_count INT           NOT NULL DEFAULT 0,
    fee          DECIMAL(10,2) NOT NULL DEFAULT 20.00,
    status       VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED/FULL',
    PRIMARY KEY (id),
    KEY idx_date_dept (visit_date, department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生排班';

INSERT INTO doctor_schedule (doctor_id, doctor_name, department, visit_date, time_slot, total_count, booked_count, fee, status) VALUES
(2, '张医生', '内科', CURDATE(), '上午', 20, 0, 20.00, 'OPEN'),
(2, '张医生', '内科', CURDATE(), '下午', 20, 0, 20.00, 'OPEN'),
(2, '张医生', '儿科', CURDATE(), '上午', 15, 0, 30.00, 'OPEN');

-- ---------- 挂号记录 ----------
CREATE TABLE IF NOT EXISTS registration (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    reg_no        VARCHAR(32)   NOT NULL,
    patient_id    BIGINT        NOT NULL,
    patient_name  VARCHAR(64)   NOT NULL,
    schedule_id   BIGINT        NOT NULL,
    doctor_id     BIGINT        NOT NULL,
    doctor_name   VARCHAR(64)   NOT NULL,
    department    VARCHAR(64)   NOT NULL,
    fee           DECIMAL(10,2) NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'REGISTERED',
    visit_date    DATE                   DEFAULT NULL,
    time_slot     VARCHAR(32)            DEFAULT NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_no (reg_no),
    -- 幂等兜底：同患者同排班不重复挂号
    UNIQUE KEY uk_patient_schedule (patient_id, schedule_id, status),
    KEY idx_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='挂号记录';

-- ---------- 处方 ----------
CREATE TABLE IF NOT EXISTS prescription (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    prescription_no VARCHAR(32)   NOT NULL,
    patient_id      BIGINT        NOT NULL,
    patient_name    VARCHAR(64)   NOT NULL,
    doctor_id       BIGINT        NOT NULL,
    doctor_name     VARCHAR(64)   NOT NULL,
    registration_id BIGINT                 DEFAULT NULL,
    type            VARCHAR(16)   NOT NULL DEFAULT 'RX',
    total_amount    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status          VARCHAR(24)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PAID/DISPENSED/REVIEW_REJECTED/REVOKED',
    diagnosis       VARCHAR(500)           DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prescription_no (prescription_no),
    KEY idx_status (status),
    KEY idx_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方';

-- ---------- 处方明细 ----------
CREATE TABLE IF NOT EXISTS prescription_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT        NOT NULL,
    drug_id         BIGINT        NOT NULL,
    drug_name       VARCHAR(128)  NOT NULL,
    specification   VARCHAR(64)            DEFAULT NULL,
    quantity        DECIMAL(10,2) NOT NULL,
    unit            VARCHAR(16)            DEFAULT NULL,
    price           DECIMAL(10,2) NOT NULL,
    subtotal        DECIMAL(10,2) NOT NULL,
    usage           VARCHAR(255)           DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_prescription (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方明细';

-- ---------- 药品目录 ----------
CREATE TABLE IF NOT EXISTS drug (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    drug_code     VARCHAR(32)   NOT NULL,
    drug_name     VARCHAR(128)  NOT NULL,
    specification VARCHAR(64)            DEFAULT NULL,
    manufacturer  VARCHAR(128)           DEFAULT NULL,
    dosage_form   VARCHAR(32)            DEFAULT NULL,
    price         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    unit          VARCHAR(16)            DEFAULT NULL,
    category      VARCHAR(16)   NOT NULL DEFAULT 'RX',
    status        TINYINT       NOT NULL DEFAULT 1,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_code (drug_code),
    KEY idx_name (drug_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品目录';

INSERT INTO drug (drug_code, drug_name, specification, manufacturer, dosage_form, price, unit, category, status) VALUES
('D001', '阿莫西林胶囊', '0.25g*24粒', '华北制药', '胶囊剂', 12.50, '盒', 'RX', 1),
('D002', '布洛芬缓释胶囊', '0.3g*20粒', '中美史克', '胶囊剂', 18.00, '盒', 'RX', 1),
('D003', '复方甘草片', '100片',     '广州白云山', '片剂',  8.50, '瓶', 'ZCY', 1),
('D004', '维生素C片',    '0.1g*100片','东北制药',   '片剂',  3.50, '瓶', 'RX', 1),
('D005', '感冒灵颗粒',    '10g*9袋',   '华润三九',   '颗粒剂',11.00, '盒', 'ZCY', 1);

-- ---------- 药品库存（乐观锁） ----------
CREATE TABLE IF NOT EXISTS drug_stock (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    drug_id     BIGINT   NOT NULL,
    batch_no    VARCHAR(32) NOT NULL,
    quantity    INT      NOT NULL DEFAULT 0,
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    expire_date DATE              DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_batch (drug_id, batch_no),
    KEY idx_drug (drug_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品库存';

INSERT INTO drug_stock (drug_id, batch_no, quantity, version, expire_date) VALUES
(1, 'B20260001', 500, 0, DATE_ADD(CURDATE(), INTERVAL 365 DAY)),
(2, 'B20260002', 300, 0, DATE_ADD(CURDATE(), INTERVAL 365 DAY)),
(3, 'B20260003', 800, 0, DATE_ADD(CURDATE(), INTERVAL 365 DAY)),
(4, 'B20260004', 1000, 0, DATE_ADD(CURDATE(), INTERVAL 365 DAY)),
(5, 'B20260005', 400, 0, DATE_ADD(CURDATE(), INTERVAL 365 DAY));

-- ---------- 发药流水 ----------
CREATE TABLE IF NOT EXISTS dispense_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    dispense_no     VARCHAR(32)   NOT NULL,
    prescription_id BIGINT        NOT NULL,
    prescription_no VARCHAR(32)   NOT NULL,
    patient_id      BIGINT        NOT NULL,
    patient_name    VARCHAR(64)   NOT NULL,
    drug_id         BIGINT        NOT NULL,
    drug_name       VARCHAR(128)  NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL,
    unit            VARCHAR(16)            DEFAULT NULL,
    pharmacist_id   BIGINT        NOT NULL,
    pharmacist_name VARCHAR(64)   NOT NULL,
    window_no       VARCHAR(16)            DEFAULT NULL,
    dispense_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dispense_time (dispense_time),
    KEY idx_prescription (prescription_id),
    KEY idx_drug (drug_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发药流水';

-- ---------- 缴费记录 ----------
CREATE TABLE IF NOT EXISTS payment (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    payment_no     VARCHAR(32)   NOT NULL,
    prescription_id BIGINT       NOT NULL,
    amount         DECIMAL(10,2) NOT NULL,
    pay_method     VARCHAR(16)   NOT NULL DEFAULT 'CASH',
    status         VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缴费记录';

-- ---------- 床位 ----------
CREATE TABLE IF NOT EXISTS bed (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    bed_no      VARCHAR(16) NOT NULL,
    department  VARCHAR(64) NOT NULL,
    patient_id  BIGINT               DEFAULT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'FREE' COMMENT 'FREE/OCCUPIED/CLEANING',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bed_no (bed_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='床位';

INSERT INTO bed (bed_no, department, status) VALUES
('301-1','内科','FREE'),('301-2','内科','FREE'),('301-3','内科','FREE'),
('302-1','儿科','FREE'),('302-2','儿科','FREE');

-- ---------- 入院记录 ----------
CREATE TABLE IF NOT EXISTS admission (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    admission_no  VARCHAR(32)   NOT NULL,
    patient_id    BIGINT        NOT NULL,
    patient_name  VARCHAR(64)   NOT NULL,
    department    VARCHAR(64)   NOT NULL,
    bed_id        BIGINT        NOT NULL,
    bed_no        VARCHAR(16)            DEFAULT NULL,
    doctor_id     BIGINT                 DEFAULT NULL,
    doctor_name   VARCHAR(64)            DEFAULT NULL,
    diagnosis     VARCHAR(500)           DEFAULT NULL,
    deposit       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status        VARCHAR(16)   NOT NULL DEFAULT 'ADMITTED',
    admit_time    DATETIME               DEFAULT NULL,
    discharge_time DATETIME              DEFAULT NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_no (admission_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入院记录';

-- ---------- 电子病历 ----------
CREATE TABLE IF NOT EXISTS medical_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    record_no        VARCHAR(32)  NOT NULL,
    patient_id       BIGINT       NOT NULL,
    patient_name     VARCHAR(64)  NOT NULL,
    doctor_id        BIGINT                DEFAULT NULL,
    doctor_name      VARCHAR(64)           DEFAULT NULL,
    registration_id  BIGINT                DEFAULT NULL,
    type             VARCHAR(16)           DEFAULT 'OUTPATIENT',
    chief_complaint  VARCHAR(500)          DEFAULT NULL,
    present_illness  TEXT,
    past_history     VARCHAR(500)          DEFAULT NULL,
    diagnosis        VARCHAR(500)          DEFAULT NULL,
    treatment        VARCHAR(500)          DEFAULT NULL,
    document_key     VARCHAR(64)           DEFAULT NULL,
    document_url     VARCHAR(255)          DEFAULT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子病历';
