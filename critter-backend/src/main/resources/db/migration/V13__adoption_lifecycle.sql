ALTER TABLE adoption
    ADD COLUMN certificate_no VARCHAR(32) NULL AFTER audit_reason,
    ADD COLUMN pay_deadline DATETIME NULL AFTER certificate_no,
    ADD COLUMN termination_reason VARCHAR(255) NULL AFTER pay_deadline;

CREATE UNIQUE INDEX uk_adoption_certificate ON adoption (certificate_no);
