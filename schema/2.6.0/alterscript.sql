ALTER TABLE `logistimo`.`INVNTRY` ADD UON DATETIME NULL;
CREATE TABLE `MATERIALMANUFACTURERS` (
  `KEY` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `MATERIAL_ID` BIGINT(20) NOT NULL,
  `MFR_CODE` BIGINT(20) NOT NULL,
  `MFR_NAME` VARCHAR(255) NOT NULL,
  `MATERIAL_CODE` BIGINT(20) NOT NULL,
  `QTY` FLOAT NULL,
  PRIMARY KEY (`KEY`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

UPDATE TRANSACTION SET TOT = "ts" WHERE TID IN ( SELECT ID FROM `ORDER` WHERE OTY = 0);
UPDATE TRANSACTION SET TOT = "os" WHERE TOT = "s";;

DROP TABLE `logistimo`.`DASHBOARD`;

CREATE TABLE `logistimo`.`DASHBOARD` (
  `DBID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `DID` BIGINT(20) NULL DEFAULT NULL,
  `DESC` VARCHAR(255) NULL DEFAULT NULL,
  `CONF` VARCHAR(4096) NULL DEFAULT NULL,
  `NAME` VARCHAR(255) NULL DEFAULT NULL,
  `TITLE` VARCHAR(255) NULL DEFAULT NULL,
  `CON` DATETIME NULL DEFAULT NULL,
  `CBY` VARCHAR(255) NULL DEFAULT NULL,
  `UON` DATETIME NULL DEFAULT NULL,
  `UBY` VARCHAR(255) NULL DEFAULT NULL,
  `INFO` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`DBID`))ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `logistimo`.`BULLETINBOARD` (
  `BBID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `DID` BIGINT(20) NULL DEFAULT NULL,
  `DESC` VARCHAR(255) NULL DEFAULT NULL,
  `CONF` VARCHAR(2048) NULL DEFAULT NULL,
  `NAME` VARCHAR(255) NULL DEFAULT NULL,
  `MIN` BIGINT(20) NULL DEFAULT NULL,
  `MAX` BIGINT(20) NULL DEFAULT NULL,
  `UBY` VARCHAR(255) NULL DEFAULT NULL,
  `UON` DATETIME NULL DEFAULT NULL,
  `CBY` VARCHAR(255) NULL DEFAULT NULL,
  `CON` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`BBID`))ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

alter table USERTOKEN add column ACCESSKEY VARCHAR(255);
UPDATE TRANSACTION SET TOT = "os" WHERE TOT = "s";

ALTER TABLE `logistimo`.`ORDER` ADD CVT DATETIME NULL, ADD VVT DATETIME NULL, ADD PART BIGINT(20) NULL, ADD SART BIGINT(20) NULL, ADD TART BIGINT(20) NULL;
