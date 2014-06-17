
/* Drop Tables */
--connect as cirmdwschm
DROP TABLE CIRMDWSCHM.CIRM_CLASSIFICATION;
DROP TABLE CIRMDWSCHM.CIRM_SRREQ_SRACTOR;
DROP TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY;
DROP TABLE CIRMDWSCHM.CIRM_SR_STATUS_HISTORY;
DROP TABLE CIRMDWSCHM.CIRM_SR_REQUESTS;
DROP TABLE CIRMDWSCHM.CIRM_GIS_INFO;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY;
DROP TABLE CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY;
DROP TABLE CIRMDWSCHM.CIRM_IRI;
DROP TABLE CIRMDWSCHM.CIRM_IRI_TYPE;
DROP TABLE CIRMDWSCHM.CIRM_SR_ACTOR;
DROP TABLE CIRMDWSCHM.CIRM_MDC_ADDRESS;
DROP TABLE CIRMDWSCHM.CIRM_SR_TYPE_QUESTION;
DROP TABLE CIRMDWSCHM.CIRM_SR_TYPE;
DROP TABLE CIRMDWSCHM.CIRM_USER;
DROP TABLE CIRMDWSCHM.CIRM_ORG_UNIT;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_CLOB;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER;
DROP TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_STRING;
DROP TABLE CIRMDWSCHM.CIRM_SERVICE_ACTION;
DROP TABLE CIRMDWSCHM.CIRM_SERVICE_CALL;
DROP TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY_TYPE;
DROP TABLE CIRMDWSCHM.CIRM_SR_INTAKE_METHOD;
DROP TABLE CIRMDWSCHM.CIRM_SR_OUTCOME;
DROP TABLE CIRMDWSCHM.CIRM_SR_PRIORITY;
DROP TABLE CIRMDWSCHM.CIRM_SR_STATUS;
DROP TABLE CIRMDWSCHM.CIRM_SR_CHOICE_VALUE;
DROP TABLE CIRMDWSCHM.CIRM_OBSERVED_HOLIDAY;
DROP TABLE CIRMDWSCHM.CIRM_ORG_UNIT_OFFICIAL;
DROP TABLE CIRMDWSCHM.CIRM_GOVERNMENT_OFFICIAL;
DROP TABLE CIRMDWSCHM.CIRM_META_INDIVIDUAL;



/* Create Tables */

CREATE TABLE CIRMDWSCHM.CIRM_ORG_UNIT_OFFICIAL
(
	-- 2NF field taken from the SR_OWNER table.
	ORG_UNIT VARCHAR2(255) NOT NULL,
	OFFICAL_ID VARCHAR2(255) NOT NULL,
	START_DATE TIMESTAMP NOT NULL,
	END_DATE TIMESTAMP
);

CREATE INDEX CIRM_ORG_OFFICAL_IDX
	ON
	CIRMDWSCHM.CIRM_ORG_UNIT_OFFICIAL (ORG_UNIT, OFFICAL_ID, START_DATE);

ALTER TABLE CIRMDWSCHM.CIRM_ORG_UNIT_OFFICIAL
	ADD CONSTRAINT
	CIRM_ORG_OFFICAL_PK
	PRIMARY KEY (ORG_UNIT, OFFICAL_ID, START_DATE) USING INDEX CIRM_ORG_OFFICAL_IDX;

	
	
CREATE TABLE CIRMDWSCHM.CIRM_GOVERNMENT_OFFICIAL
(
	OFFICAL_ID VARCHAR2(255) NOT NULL,
	FULLNAME VARCHAR2(255)
);

CREATE INDEX CIRM_GOV_OFFICAL_IDX
	ON
	CIRMDWSCHM.CIRM_GOVERNMENT_OFFICIAL (OFFICAL_ID);

ALTER TABLE CIRMDWSCHM.GOVERNMENT_OFFICIAL
	ADD CONSTRAINT
	CIRM_GOV_OFFICAL_PK
	PRIMARY KEY (OFFICAL_ID) USING INDEX CIRM_GOV_OFFICAL_IDX;
	

CREATE TABLE CIRMDWSCHM.CIRM_META_INDIVIDUAL
(
	FRAGMENT VARCHAR2(255) NOT NULL,
	ENTITY_LABEL VARCHAR2(4000),
	OWLCLASS VARCHAR2(255),
	NAME VARCHAR2(255)
);

CREATE INDEX CIRM_META_INDIVIDUAL_IDX
	ON
	CIRMDWSCHM.CIRM_META_INDIVIDUAL (FRAGMENT);

ALTER TABLE CIRMDWSCHM.CIRM_META_INDIVIDUAL
	ADD CONSTRAINT
	CIRM_META_INDIVIDUAL_PK
	PRIMARY KEY (FRAGMENT) USING INDEX CIRM_META_INDIVIDUAL_IDX;

	
	
CREATE TABLE CIRMDWSCHM.CIRM_CLASSIFICATION
(
	SUBJECT NUMBER(19,0) NOT NULL,
	OWLCLASS NUMBER(19,0) NOT NULL,
	FROM_DATE TIMESTAMP NOT NULL,
	TO_DATE TIMESTAMP
);



CREATE INDEX 
	CIRM_CLASSIFICATION_PK_IDX
	ON 
	CIRMDWSCHM.CIRM_CLASSIFICATION(SUBJECT, OWLCLASS, FROM_DATE);

ALTER TABLE
	CIRMDWSCHM.CIRM_CLASSIFICATION 
	ADD CONSTRAINT 
	CIRM_CLASSIFICATION_PK
	PRIMARY KEY(SUBJECT, OWLCLASS, FROM_DATE) USING INDEX CIRM_CLASSIFICATION_PK_IDX;


CREATE TABLE CIRMDWSCHM.CIRM_GIS_INFO
(
	ID NUMBER(19,0) NOT NULL,
	HASH VARCHAR2(255),
	DATA LONG,
	GIS_SWRCDAY VARCHAR2(10),
	GIS_MUNICIP VARCHAR2(64),
	GIS_TEAMOFFC VARCHAR2(255),
	GIS_MIACODE VARCHAR2(255),
	GIS_MIAZONE FLOAT,
	GIS_MUNCODE VARCHAR2(10),
	GIS_TEAMNO NUMBER(19,0),
	GIS_DAY NUMBER(19,0),
	GIS_ROUTE NUMBER(19,0),
	GIS_MIACOMD NUMBER(19,0),
	GIS_MIACOM VARCHAR2(255),
	GIS_MIAGARB VARCHAR2(255),
	GIS_MIAGARB2 VARCHAR2(255),
	GIS_MIAMNT VARCHAR2(8),
	GIS_NETA NUMBER(19,0),
	GIS_MIARECY VARCHAR2(8),
	GIS_MIARCRTE NUMBER(19,0),
	GIS_MIATDAY VARCHAR2(8),
	GIS_CODENO NUMBER(19,0),
	GIS_COMDIST NUMBER(19,0),
	GIS_CMAINT VARCHAR2(8),
	GIS_STLGHT VARCHAR2(16),
	GIS_BULKSERV NUMBER(19,0),
	GIS_SWRECWK VARCHAR2(10),
	GIS_SWENFID VARCHAR2(10),
	GISX_FOLIO VARCHAR2(15)
);

CREATE INDEX 
	CIRM_GIS_INFO_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_GIS_INFO(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_GIS_INFO 
	ADD CONSTRAINT 
	CIRM_GIS_INFO_PK
	PRIMARY KEY(ID) USING INDEX CIRM_GIS_INFO_PK_IDX;

CREATE TABLE CIRMDWSCHM.CIRM_IRI
(
	ID NUMBER(19,0) NOT NULL,
	IRI VARCHAR2(255),
	IRI_TYPE_ID NUMBER NOT NULL,
	IRI_LABEL VARCHAR2(255)
);

CREATE INDEX 
	CIRM_IRI_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_IRI(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_IRI
	ADD CONSTRAINT 
	CIRM_IRI_PK
	PRIMARY KEY(ID) USING INDEX CIRM_IRI_PK_IDX;

CREATE TABLE CIRMDWSCHM.CIRM_IRI_TYPE
(
	ID NUMBER NOT NULL,
	IRI VARCHAR2(20) NOT NULL
);

CREATE INDEX 
	CIRM_IRI_TYPE_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_IRI_TYPE(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_IRI_TYPE
	ADD CONSTRAINT 
	CIRM_IRI_TYPE_PK
	PRIMARY KEY(ID) USING INDEX CIRM_IRI_TYPE_PK_IDX;

CREATE INDEX  
	CIRM_IRI_TYPE_UQ_IDX 
	ON 
	CIRMDWSCHM.CIRM_IRI_TYPE(IRI);

ALTER TABLE
	CIRMDWSCHM.CIRM_IRI_TYPE
	ADD CONSTRAINT 
	CIRM_IRI_TYPE_UQ
	UNIQUE(IRI) USING INDEX CIRM_IRI_TYPE_UQ_IDX;


CREATE TABLE CIRMDWSCHM.CIRM_MDC_ADDRESS
(
	ADDRESS_ID NUMBER(19,0) NOT NULL,
	-- 2NF field taken from the MDC_ADDRESS table.
	FULL_ADDRESS VARCHAR2(255),
	STREET_NUMBER NUMBER(19,0),
	STREET_NAME VARCHAR2(255),
	STREET_NAME_PREFIX VARCHAR2(255),
	STREET_NAME_SUFFIX VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	UNIT VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	CITY VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	STATE VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	ZIP NUMBER(19,0),
	XCOORDINATE FLOAT,
	YCOORDINATE FLOAT,
	-- 2NF field taken from the MDC_ADDRESS table.
	LOCATION_NAME VARCHAR2(255)
);

CREATE INDEX 
	CIRMPK_MDC_ADDRESS_IDX 
	ON 
	CIRMDWSCHM.CIRM_MDC_ADDRESS(ADDRESS_ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_MDC_ADDRESS
	ADD CONSTRAINT 
	CIRMPK_MDC_ADDRESS
	PRIMARY KEY(ADDRESS_ID) USING INDEX CIRMPK_MDC_ADDRESS_IDX;

CREATE TABLE CIRMDWSCHM.CIRM_ORG_UNIT
(
	-- 2NF field taken from the SR_OWNER table.
	ORG_UNIT VARCHAR2(255) NOT NULL,
	-- 2NF field taken from the ORG_UNIT table.
	ORG_UNIT_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRMPK_ORG_UNIT_IDX 
	ON 
	CIRMDWSCHM.CIRM_ORG_UNIT(ORG_UNIT);

ALTER TABLE
	CIRMDWSCHM.CIRM_ORG_UNIT
	ADD CONSTRAINT 
	CIRMPK_ORG_UNIT
	PRIMARY KEY(ORG_UNIT) USING INDEX CIRMPK_ORG_UNIT_IDX;


CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY
(
	SUBJECT NUMBER(19,0) NOT NULL,
	PREDICATE NUMBER(19,0) NOT NULL,
	DATATYPE_ID NUMBER(19,0) NOT NULL,
	VALUE_ID NUMBER(19,0) NOT NULL,
	FROM_DATE TIMESTAMP NOT NULL,
	TO_DATE TIMESTAMP
);

CREATE INDEX 
	CIRM_OWL_DATA_PROPERTY_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY(SUBJECT, PREDICATE, VALUE_ID, FROM_DATE);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY
	ADD CONSTRAINT 
	CIRM_OWL_DATA_PROPERTY_PK
	PRIMARY KEY (SUBJECT, PREDICATE, VALUE_ID, FROM_DATE) USING INDEX CIRM_OWL_DATA_PROPERTY_PK_IDX;

CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_CLOB
(
	ID NUMBER(19,0) NOT NULL,
	VALUE_HASH VARCHAR2(28) NOT NULL,
	VALUE_VARCHAR VARCHAR2(4000),
	VALUE_CLOB CLOB
);

CREATE INDEX 
	CIRM_OWL_DATA_VAL_CLOB_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_CLOB(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_CLOB
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_CLOB_PK
	PRIMARY KEY (ID) USING INDEX CIRM_OWL_DATA_VAL_CLOB_PK_IDX;


CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE
(
	ID NUMBER(19,0) NOT NULL,
	VALUE_DATE TIMESTAMP(3) NOT NULL
);

CREATE INDEX 
	CIRM_OWL_DATA_VAL_DATE_PK_IDX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_DATE_PK
	PRIMARY KEY (ID) USING INDEX CIRM_OWL_DATA_VAL_DATE_PK_IDX;

CREATE INDEX  
	CIRM_OWL_DATA_VAL_DATE_UQ_IDX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE(VALUE_DATE);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DATE
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_DATE_UQ
	UNIQUE(VALUE_DATE) USING INDEX CIRM_OWL_DATA_VAL_DATE_UQ_IDX;
	
CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE
(
	ID NUMBER(19,0) NOT NULL,
	VALUE_DOUBLE FLOAT NOT NULL
);

CREATE INDEX 
	CIRM_OWL_DATA_VAL_DOUBLE_PK_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_DOUBLE_PK
	PRIMARY KEY (ID) USING INDEX CIRM_OWL_DATA_VAL_DOUBLE_PK_IX;

CREATE INDEX  
	CIRM_OWL_DATA_VAL_DOUBLE_UQ_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE(VALUE_DOUBLE);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_DOUBLE
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_DOUBLE_UQ
	UNIQUE(VALUE_DOUBLE) USING INDEX CIRM_OWL_DATA_VAL_DOUBLE_UQ_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER
(
	ID NUMBER(19,0) NOT NULL,
	VALUE_INTEGER NUMBER NOT NULL
);

CREATE INDEX 
	CIRM_OWL_DATA_VAL_INTEGER_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_INTEGER_PK
	PRIMARY KEY (ID) USING INDEX CIRM_OWL_DATA_VAL_INTEGER_IX;
	
CREATE INDEX  
	CIRM_OWL_DATA_VAL_INT_UQ_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER(VALUE_INTEGER);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_INTEGER
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_INTEGER_UQ
	UNIQUE(VALUE_INTEGER) USING INDEX CIRM_OWL_DATA_VAL_INT_UQ_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_OWL_DATA_VAL_STRING
(
	ID NUMBER(19,0) NOT NULL,
	VALUE_VARCHAR VARCHAR2(255)
);

CREATE INDEX 
	CIRM_OWL_DATA_VAL_STRING_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_STRING(ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_DATA_VAL_STRING
	ADD CONSTRAINT 
	CIRM_OWL_DATA_VAL_STRING_PK
	PRIMARY KEY (ID) USING INDEX CIRM_OWL_DATA_VAL_STRING_IX;


CREATE TABLE CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY
(
	SUBJECT NUMBER(19,0) NOT NULL,
	PREDICATE NUMBER(19,0) NOT NULL,
	OBJECT NUMBER(19,0) NOT NULL,
	FROM_DATE TIMESTAMP NOT NULL,
	TO_DATE TIMESTAMP
);

CREATE INDEX 
	CIRM_OWL_OBJECT_PROPERTY_IX 
	ON 
	CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY (SUBJECT, PREDICATE, OBJECT, FROM_DATE);

ALTER TABLE
	CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY
	ADD CONSTRAINT 
	CIRM_OWL_OBJECT_PROPERTY_PK
	PRIMARY KEY (SUBJECT, PREDICATE, OBJECT, FROM_DATE) USING INDEX CIRM_OWL_OBJECT_PROPERTY_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_SERVICE_ACTION
(
	SERVICE_CALL_ID NUMBER(19,0) NOT NULL,
	NAME VARCHAR2(255) NOT NULL,
	VALUE VARCHAR2(255),
	AT_TIME TIMESTAMP NOT NULL
);

CREATE INDEX 
	CIRM_SERVICE_ACTION_IX 
	ON 
	CIRMDWSCHM.CIRM_SERVICE_ACTION (SERVICE_CALL_ID, AT_TIME);

ALTER TABLE
	CIRMDWSCHM.CIRM_SERVICE_ACTION
	ADD CONSTRAINT 
	CIRM_SERVICE_ACTION_PK
	PRIMARY KEY (SERVICE_CALL_ID, AT_TIME) USING INDEX CIRM_SERVICE_ACTION_IX;
	

CREATE TABLE CIRMDWSCHM.CIRM_SERVICE_CALL
(
	SERVICE_CALL_ID NUMBER(19,0) NOT NULL,
	AGENT VARCHAR2(255) NOT NULL,
	START_TIME TIMESTAMP NOT NULL,
	END_TIME TIMESTAMP NOT NULL,
	FROM_PHONE VARCHAR2(10)
);

CREATE INDEX 
	CIRM_SERVICE_CALL_IX 
	ON 
	CIRMDWSCHM.CIRM_SERVICE_CALL (SERVICE_CALL_ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_SERVICE_CALL
	ADD CONSTRAINT 
	CIRM_SERVICE_CALL_PK
	PRIMARY KEY (SERVICE_CALL_ID) USING INDEX CIRM_SERVICE_CALL_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SRREQ_SRACTOR
(
	SR_REQUEST_ID NUMBER(19,0) NOT NULL,
	SR_ACTOR_ID NUMBER(19,0) NOT NULL
);

CREATE INDEX 
	CIRM_SRREQ_SRACTOR_IX 
	ON 
	CIRMDWSCHM.CIRM_SRREQ_SRACTOR (SR_REQUEST_ID, SR_ACTOR_ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_SRREQ_SRACTOR
	ADD CONSTRAINT 
	CIRM_SRREQ_SRACTOR_PK
	PRIMARY KEY (SR_REQUEST_ID, SR_ACTOR_ID) USING INDEX CIRM_SRREQ_SRACTOR_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY
(
	ACTIVITY_ID NUMBER(19,0) NOT NULL,
	SR_REQUEST_ID NUMBER(19,0) NOT NULL,
	-- 2NF column taken from SR TYPE table.
	SR_TYPE VARCHAR2(255),
	-- 2NF column taken from type table
	SR_TYPE_DESCRIPTION VARCHAR2(255),
	STAFF_ASSIGNED VARCHAR2(255),
	STAFF_ASSIGNED_NAME VARCHAR2(255),
	ACTIVITY_CODE VARCHAR2(255) NOT NULL,
	-- 2NF column taken from activity.
	ACTIVITY_DESCRIPTION VARCHAR2(255),
	OUTCOME_CODE VARCHAR2(255),
	DETAILS VARCHAR2(4000),
	DUE_DATE TIMESTAMP,
	COMPLETE_DATE TIMESTAMP,
	CREATED_BY VARCHAR2(255),
	CREATED_DATE TIMESTAMP,
	UPDATED_BY VARCHAR2(255),
	UPDATED_DATE TIMESTAMP
);

CREATE INDEX 
	CIRM_SR_ACTIVITY_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_ACTIVITY (ACTIVITY_ID);

ALTER TABLE
	CIRMDWSCHM.CIRM_SR_ACTIVITY
	ADD CONSTRAINT 
	CIRM_SR_ACTIVITY_PK
	PRIMARY KEY (ACTIVITY_ID) USING INDEX CIRM_SR_ACTIVITY_IX;


CREATE TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY_TYPE
(
	ACTIVITY_CODE VARCHAR2(255) NOT NULL,
	-- 2NF field taken from the ACTIVITY_TYPE table.
	ACTIVITY_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_ACTIVITY_TYPE_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_ACTIVITY_TYPE (ACTIVITY_CODE);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_ACTIVITY_TYPE
	ADD CONSTRAINT 
	CIRM_SR_ACTIVITY_TYPE_PK
	PRIMARY KEY (ACTIVITY_CODE) USING INDEX CIRM_SR_ACTIVITY_TYPE_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_ACTOR
(
	SR_ACTOR_ID NUMBER(19,0) NOT NULL,
	SR_ACTOR_NAME VARCHAR2(255),
	SR_ACTOR_LNAME VARCHAR2(255),
	SR_ACTOR_INITIALS VARCHAR2(255),
	SR_ACTOR_TITLE VARCHAR2(255),
	SR_ACTOR_SUFFIX VARCHAR2(255),
	SR_ACTOR_PHONE_NUMBER VARCHAR2(255),
	SR_ACTOR_EMAIL VARCHAR2(255),
	SR_ACTOR_CONTACT_METHOD VARCHAR2(255),
	SR_ACTOR_TYPE VARCHAR2(255),
	SR_ACTOR_ADDRESS NUMBER(19,0),
	SR_ACTOR_WORK_PHONE_NO VARCHAR2(255),
	SR_ACTOR_CELL_PHONE_NO VARCHAR2(255),
	SR_ACTOR_FAX_PHONE_NO VARCHAR2(255),
	CREATED_BY VARCHAR2(255),
	CREATED_DATE TIMESTAMP,
	UPDATED_BY VARCHAR2(255),
	UPDATED_DATE TIMESTAMP
);

CREATE INDEX 
	CIRM_SR_ACTOR_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_ACTOR(SR_ACTOR_ID);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_ACTOR
	ADD CONSTRAINT 
	CIRM_SR_ACTOR_PK
	PRIMARY KEY (SR_ACTOR_ID) USING INDEX CIRM_SR_ACTOR_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_INTAKE_METHOD
(
	SR_INTAKE_METHOD VARCHAR2(255) NOT NULL,
	SR_INTAKE_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_INTAKE_METHOD_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_INTAKE_METHOD(SR_INTAKE_METHOD);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_INTAKE_METHOD
	ADD CONSTRAINT 
	CIRM_SR_INTAKE_METHOD_PK
	PRIMARY KEY (SR_INTAKE_METHOD) USING INDEX CIRM_SR_INTAKE_METHOD_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_OUTCOME
(
	OUTCOME_CODE VARCHAR2(255) NOT NULL,
	OUTCOME_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_OUTCOME_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_OUTCOME(OUTCOME_CODE);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_OUTCOME
	ADD CONSTRAINT 
	CIRM_SR_OUTCOME_PK
	PRIMARY KEY (OUTCOME_CODE) USING INDEX CIRM_SR_OUTCOME_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_SR_PRIORITY
(
	SR_PRIORITY VARCHAR2(255) NOT NULL,
	SR_PRIORITY_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_PRIORITY_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_PRIORITY(SR_PRIORITY);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_PRIORITY
	ADD CONSTRAINT 
	CIRM_SR_PRIORITY_PK
	PRIMARY KEY (SR_PRIORITY) USING INDEX CIRM_SR_PRIORITY_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
(
	SR_REQUEST_ID NUMBER(19,0) NOT NULL,
	SR_TYPE VARCHAR2(255),
	-- 2NF field taken from the SR_TYPE table.
	SR_TYPE_DESCRIPTION VARCHAR2(255),
	SR_STATUS VARCHAR2(255),
	CASE_NUMBER VARCHAR2(255),
	SR_INTAKE_METHOD VARCHAR2(255),
	SR_PRIORITY VARCHAR2(255),
	SR_REQUEST_ADDRESS NUMBER(19,0),
	GIS_INFO_ID NUMBER(19,0),
	SR_XCOORDINATE FLOAT,
	SR_YCOORDINATE FLOAT,
	SR_DESCRIPTION VARCHAR2(4000),
	SR_COMMENTS VARCHAR2(4000),
	DUE_DATE TIMESTAMP,
	CREATED_BY VARCHAR2(255),
	CREATED_BY_NAME VARCHAR2(255),
	CREATED_DATE TIMESTAMP,
	UPDATED_BY VARCHAR2(255),
	UPDATED_BY_NAME VARCHAR2(255),
	UPDATED_DATE TIMESTAMP,
	-- 2NF field taken from the MDC_ADDRESS table.
	FULL_ADDRESS VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	UNIT VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	CITY VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	STATE VARCHAR2(255),
	-- 2NF field taken from the MDC_ADDRESS table.
	ZIP NUMBER(19,0),
	-- 2NF field taken from the MDC_ADDRESS table.
	LOCATION_NAME VARCHAR2(255),
	-- 2NF field taken from the SR_OWNER table.
	SR_OWNER VARCHAR2(255),
	-- 2NF field taken from the ORG_UNIT table.
	SR_OWNER_DESCRIPTION VARCHAR2(255),
	DURATION_DAYS NUMBER,
	CLOSED_DATE TIMESTAMP
);

CREATE INDEX 
	CIRM_SR_REQUESTS_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_REQUESTS(SR_REQUEST_ID);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD CONSTRAINT 
	CIRM_SR_REQUESTS_PK
	PRIMARY KEY (SR_REQUEST_ID) USING INDEX CIRM_SR_REQUESTS_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_STATUS
(
	SR_STATUS VARCHAR2(255) NOT NULL,
	SR_STATUS_DESCRIPTION VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_STATUS_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_STATUS(SR_STATUS);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_STATUS
	ADD CONSTRAINT 
	CIRM_SR_STATUS_PK
	PRIMARY KEY (SR_STATUS) USING INDEX CIRM_SR_STATUS_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_STATUS_HISTORY
(
	SR_REQUEST_ID NUMBER(19,0) NOT NULL,
	SR_STATUS VARCHAR2(255) NOT NULL,
	SR_STATUS_DATE TIMESTAMP NOT NULL
);

CREATE INDEX 
	CIRM_SR_STATUS_HISTORY_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_STATUS_HISTORY(SR_REQUEST_ID, SR_STATUS, SR_STATUS_DATE);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_STATUS_HISTORY
	ADD CONSTRAINT 
	CIRM_SR_STATUS_HISTORY_PK
	PRIMARY KEY (SR_REQUEST_ID, SR_STATUS, SR_STATUS_DATE) USING INDEX CIRM_SR_STATUS_HISTORY_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_SR_TYPE
(
	SR_TYPE VARCHAR2(255) NOT NULL,
	-- 2NF field taken from the SR_TYPE table.
	SR_TYPE_DESCRIPTION VARCHAR2(255),
	-- 2NF field taken from the ORG_UNIT table.
	SR_OWNER VARCHAR2(255),
	DURATION_DAYS NUMBER
);

CREATE INDEX 
	CIRM_SR_TYPE_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_TYPE (SR_TYPE);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_TYPE
	ADD CONSTRAINT 
	CIRM_SR_TYPE_PK
	PRIMARY KEY  (SR_TYPE) USING INDEX CIRM_SR_TYPE_IX;

CREATE TABLE CIRMDWSCHM.CIRM_SR_TYPE_QUESTION
(
	QUESTION_CODE VARCHAR2(255) NOT NULL,
	QUESTION_TEXT VARCHAR2(510),
	SR_TYPE VARCHAR2(255) NOT NULL,
	DATATYPE VARCHAR2(255),
	ORDER_BY NUMBER,
	QUESTION_INDEX NUMBER(19)
);

CREATE INDEX 
	CIRM_SR_TYPE_QUESTION_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_TYPE_QUESTION (QUESTION_CODE);

ALTER TABLE 
	CIRMDWSCHM.CIRM_SR_TYPE_QUESTION
	ADD CONSTRAINT 
	CIRM_SR_TYPE_QUESTION_PK
	PRIMARY KEY  (QUESTION_CODE) USING INDEX CIRM_SR_TYPE_QUESTION_IX;
	
CREATE TABLE CIRMDWSCHM.CIRM_USER
(
	USER_ID VARCHAR2(255) NOT NULL,
	FULLNAME VARCHAR2(255),
	-- 2NF field taken from the SR_OWNER table.
	ORG_UNIT VARCHAR2(255)
);

CREATE INDEX 
	CIRM_USER_IX 
	ON 
	CIRMDWSCHM.CIRM_USER (USER_ID);

ALTER TABLE 
	CIRMDWSCHM.CIRM_USER
	ADD CONSTRAINT 
	CIRM_USER_PK
	PRIMARY KEY  (USER_ID) USING INDEX CIRM_USER_IX;
	
	
CREATE TABLE CIRM_SR_CHOICE_VALUE
(
	QUESTION_CODE VARCHAR2(255) NOT NULL,
	CHOICE_VALUE_IRI VARCHAR2(255) NOT NULL,
	-- The IRI ID of the IRI which represents the individual choice value in the ontology.
	CHOICE_VALUE_ID NUMBER(19),
	CHOICE_VALUE_LABEL VARCHAR2(255)
);

CREATE INDEX 
	CIRM_SR_CHOICE_VALUE_IX 
	ON 
	CIRMDWSCHM.CIRM_SR_CHOICE_VALUE (QUESTION_CODE, CHOICE_VALUE_IRI);

ALTER TABLE
	CIRMDWSCHM.CIRM_SR_CHOICE_VALUE
	ADD CONSTRAINT 
	CIRM_SR_CHOICE_VALUE_PK
	PRIMARY KEY  (QUESTION_CODE, CHOICE_VALUE_IRI) USING INDEX CIRM_SR_CHOICE_VALUE_IX;

CREATE TABLE CIRMDWSCHM.CIRM_OBSERVED_HOLIDAY
(
	-- Fragment used in ontology to identify an observed holiday.
	HOLIDAY_CODE VARCHAR2(255) NOT NULL,
	HOLIDAY_LABEL VARCHAR2(255) NOT NULL,
	HOLIDAY_DATE TIMESTAMP NOT NULL
);

CREATE INDEX 
	CIRM_OBSERVED_HOLIDAY_IX 
	ON 
	CIRMDWSCHM.CIRM_OBSERVED_HOLIDAY (HOLIDAY_CODE, HOLIDAY_DATE);
	
ALTER TABLE
	CIRMDWSCHM.CIRM_OBSERVED_HOLIDAY
	ADD CONSTRAINT 
	CIRM_OBSERVED_HOLIDAY_PK
	PRIMARY KEY  (HOLIDAY_CODE, HOLIDAY_DATE) USING INDEX CIRM_OBSERVED_HOLIDAY_IX;
	
/* Create Foreign Keys */

ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD CONSTRAINT CIRMFK_SR_GIS_INFO FOREIGN KEY (GIS_INFO_ID)
	REFERENCES CIRMDWSCHM.CIRM_GIS_INFO (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_CLASSIFICATION
	ADD CONSTRAINT CIRMFK_CLASSIFICATION_SUBJECT FOREIGN KEY (SUBJECT)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_CLASSIFICATION
	ADD CONSTRAINT CIRMFK_CLASSIFICATION_CLASS FOREIGN KEY (OWLCLASS)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_DATA_PROP_DATATYPE FOREIGN KEY (DATATYPE_ID)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_DATA_PROP_SUBJECT FOREIGN KEY (SUBJECT)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_DATA_PROP_PREDICATE FOREIGN KEY (PREDICATE)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_OBJ_PROP_PREDICATE FOREIGN KEY (PREDICATE)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_OBJ_PROP_SUBJECT FOREIGN KEY (SUBJECT)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY
	ADD CONSTRAINT CIRMFK_OWL_OBJ_PROP_OBJECT FOREIGN KEY (OBJECT)
	REFERENCES CIRMDWSCHM.CIRM_IRI (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_IRI
	ADD CONSTRAINT CIRMFK_IRI_TYPE FOREIGN KEY (IRI_TYPE_ID)
	REFERENCES CIRMDWSCHM.CIRM_IRI_TYPE (ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_ACTOR
	ADD FOREIGN KEY (SR_ACTOR_ADDRESS)
	REFERENCES CIRMDWSCHM.CIRM_MDC_ADDRESS (ADDRESS_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD CONSTRAINT CIRMFK_SR_REQUEST_ADDRESS FOREIGN KEY (SR_REQUEST_ADDRESS)
	REFERENCES CIRMDWSCHM.CIRM_MDC_ADDRESS (ADDRESS_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (SR_OWNER)
	REFERENCES CIRMDWSCHM.CIRM_ORG_UNIT (ORG_UNIT)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_TYPE
	ADD FOREIGN KEY (SR_OWNER)
	REFERENCES CIRMDWSCHM.CIRM_ORG_UNIT (ORG_UNIT)
;


ALTER TABLE CIRMDWSCHM.CIRM_USER
	ADD FOREIGN KEY (ORG_UNIT)
	REFERENCES CIRMDWSCHM.CIRM_ORG_UNIT (ORG_UNIT)
;


ALTER TABLE CIRMDWSCHM.CIRM_SERVICE_ACTION
	ADD CONSTRAINT CIRMFK_ON_SERVICE_ACTION FOREIGN KEY (SERVICE_CALL_ID)
	REFERENCES CIRMDWSCHM.CIRM_SERVICE_CALL (SERVICE_CALL_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY
	ADD CONSTRAINT CIRMFK_SR_ACTIVITY_TYPE FOREIGN KEY (ACTIVITY_CODE)
	REFERENCES CIRMDWSCHM.CIRM_SR_ACTIVITY_TYPE (ACTIVITY_CODE)
;


ALTER TABLE CIRMDWSCHM.CIRM_SRREQ_SRACTOR
	ADD CONSTRAINT CIRMFK_ON_SR_ACTOR FOREIGN KEY (SR_ACTOR_ID)
	REFERENCES CIRMDWSCHM.CIRM_SR_ACTOR (SR_ACTOR_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (SR_INTAKE_METHOD)
	REFERENCES CIRMDWSCHM.CIRM_SR_INTAKE_METHOD (SR_INTAKE_METHOD)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY
	ADD CONSTRAINT CIRMFK_SR_ACT_OUTCOME FOREIGN KEY (OUTCOME_CODE)
	REFERENCES CIRMDWSCHM.CIRM_SR_OUTCOME (OUTCOME_CODE)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD CONSTRAINT CIRMFK_SR_PRIORITY FOREIGN KEY (SR_PRIORITY)
	REFERENCES CIRMDWSCHM.CIRM_SR_PRIORITY (SR_PRIORITY)
;


ALTER TABLE CIRMDWSCHM.CIRM_SRREQ_SRACTOR
	ADD CONSTRAINT CIRMFK_ON_SR_REQ FOREIGN KEY (SR_REQUEST_ID)
	REFERENCES CIRMDWSCHM.CIRM_SR_REQUESTS (SR_REQUEST_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY
	ADD FOREIGN KEY (SR_REQUEST_ID)
	REFERENCES CIRMDWSCHM.CIRM_SR_REQUESTS (SR_REQUEST_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_STATUS_HISTORY
	ADD FOREIGN KEY (SR_REQUEST_ID)
	REFERENCES CIRMDWSCHM.CIRM_SR_REQUESTS (SR_REQUEST_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (SR_STATUS)
	REFERENCES CIRMDWSCHM.CIRM_SR_STATUS (SR_STATUS)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_STATUS_HISTORY
	ADD FOREIGN KEY (SR_STATUS)
	REFERENCES CIRMDWSCHM.CIRM_SR_STATUS (SR_STATUS)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (SR_TYPE)
	REFERENCES CIRMDWSCHM.CIRM_SR_TYPE (SR_TYPE)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_TYPE_QUESTION
	ADD FOREIGN KEY (SR_TYPE)
	REFERENCES CIRMDWSCHM.CIRM_SR_TYPE (SR_TYPE)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_ACTIVITY
	ADD FOREIGN KEY (STAFF_ASSIGNED)
	REFERENCES CIRMDWSCHM.CIRM_USER (USER_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (CREATED_BY)
	REFERENCES CIRMDWSCHM.CIRM_USER (USER_ID)
;


ALTER TABLE CIRMDWSCHM.CIRM_SR_REQUESTS
	ADD FOREIGN KEY (UPDATED_BY)
	REFERENCES CIRMDWSCHM.CIRM_USER (USER_ID)
;

ALTER TABLE CIRMDWSCHM.CIRM_SR_CHOICE_VALUE
	ADD CONSTRAINT CIRMFK_SR_TYPE_QUESTION FOREIGN KEY (QUESTION_CODE)
	REFERENCES CIRMDWSCHM.CIRM_SR_TYPE_QUESTION (QUESTION_CODE)
;


/* Create Indexes */

CREATE INDEX CIRM_IDX_CLASS ON CIRMDWSCHM.CIRM_CLASSIFICATION (SUBJECT, TO_DATE);
CREATE INDEX CIRM_IDX_GIS_INFO_HASH ON CIRMDWSCHM.CIRM_GIS_INFO (HASH);
CREATE UNIQUE INDEX CIRM_IRI_IRINTYPE_UQ ON CIRMDWSCHM.CIRM_IRI (IRI, IRI_TYPE_ID);
CREATE INDEX CIRM_IDX_IRI ON CIRMDWSCHM.CIRM_IRI (IRI);
CREATE INDEX CIRM_IDX_DATA_PROP ON CIRMDWSCHM.CIRM_OWL_DATA_PROPERTY (SUBJECT, TO_DATE);
CREATE INDEX CIRM_IDX_OBJ_PROP ON CIRMDWSCHM.CIRM_OWL_OBJECT_PROPERTY (SUBJECT, TO_DATE);
CREATE UNIQUE INDEX CIRMIDX_SR_ACTOR_ID ON CIRMDWSCHM.CIRM_SRREQ_SRACTOR (SR_ACTOR_ID);


