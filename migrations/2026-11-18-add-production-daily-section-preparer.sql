-- 制表人按车间记录；历史整表姓名和审计姓名无法可靠还原，不做回填。
ALTER TABLE production_daily_report_section
    ADD COLUMN prepared_by_name VARCHAR(100) NULL COMMENT '本车间最近一次保存的填写人' AFTER version;
