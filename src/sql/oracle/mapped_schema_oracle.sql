drop table CIRM_SRREQ_SRACTOR cascade constraints;
drop table CIRM_SR_ACTOR cascade constraints;
drop table CIRM_SR_ACTIVITY cascade constraints;
drop table CIRM_SR_REQUESTS cascade constraints;
drop table CIRM_MDC_ADDRESS cascade constraints;
drop table CIRM_SERVICE_ACTION cascade constraints;
drop table CIRM_SERVICE_CALL cascade constraints;
drop table CIRM_GIS_INFO cascade constraints;

create table CIRM_MDC_ADDRESS ( ADDRESS_ID number(19,0) not null,
								FULL_ADDRESS varchar2(255),
                                STREET_NUMBER number(19,0), 
                                STREET_NAME varchar2(255), 
                                STREET_NAME_PREFIX varchar2(255), 
                                STREET_NAME_SUFFIX varchar2(255), 
                                UNIT varchar2(255), 
                                CITY number(19,0), 
                                STATE number(19,0), 
                                ZIP number(19,0), 
                                XCOORDINATE FLOAT, 
                                YCOORDINATE FLOAT,
                                LOCATION_NAME varchar2(255),
                                constraint CIRM_MDC_ADDRESS_PK primary key (ADDRESS_ID)
							   	    USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
                                ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
                                
create table CIRM_SR_REQUESTS ( SR_REQUEST_ID number(19,0) not null, 
                                SR_STATUS varchar2(255), 
                                CASE_NUMBER varchar2(255), 
                                SR_INTAKE_METHOD varchar2(255),
                                SR_PRIORITY varchar2(255),
                                SR_REQUEST_ADDRESS number(19,0), 
                                GIS_INFO_ID number(19,0), 
                                SR_XCOORDINATE FLOAT, 
                                SR_YCOORDINATE FLOAT,
                                SR_FOLIO number(13,0),
						  		SR_DESCRIPTION varchar2(4000),
						  		SR_COMMENTS varchar2(4000),
                                DUE_DATE timestamp(3),
 						  		CREATED_BY varchar2(255),
						  		CREATED_DATE timestamp(3),
						  		UPDATED_BY varchar2(255),
						  		UPDATED_DATE timestamp(3),
						  		APPROVED_DATE timestamp(3),
                                constraint CIRM_SR_REQUESTS_PK primary key (SR_REQUEST_ID)
							   	    USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
                              ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
                                
create table CIRM_SR_ACTOR (SR_ACTOR_ID number(19,0) not null,
							SR_ACTOR_NAME varchar2(255),
							SR_ACTOR_LNAME varchar2(255),
							SR_ACTOR_INITIALS varchar2(255),
							SR_ACTOR_TITLE varchar2(255),
							SR_ACTOR_SUFFIX varchar2(255),
							SR_ACTOR_PHONE_NUMBER varchar2(255),
							SR_ACTOR_EMAIL varchar2(255),
							SR_ACTOR_CONTACT_METHOD varchar2(255),
							SR_ACTOR_TYPE varchar2(255),
							SR_ACTOR_ADDRESS number(19,0),
							SR_ACTOR_WORK_PHONE_NO varchar2(255),
							SR_ACTOR_CELL_PHONE_NO varchar2(255),
							SR_ACTOR_OTHER_PHONE_NO varchar2(255),
							SR_ACTOR_FAX_PHONE_NO varchar2(255),
							SR_ACTOR_NOTIFICATION_PREF varchar2(1),
							SR_ACTOR_USERNAME varchar2(10),
							SR_ACTOR_ORGANIZATION varchar2(150),
							SR_ACTOR_CHILD_ORG varchar2(150),
							CREATED_BY varchar2(255),
							CREATED_DATE timestamp(3),
							UPDATED_BY varchar2(255),
							UPDATED_DATE timestamp(3),
							--primary key (SR_ACTOR_ID)
   						    constraint CIRM_SR_ACTOR_PK primary key (SR_ACTOR_ID)
						   	    USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
							) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
							
create table CIRM_SRREQ_SRACTOR	( SR_REQUEST_ID number(19,0) not null,
								  SR_ACTOR_ID number(19,0) not null,
								  --primary key (SR_REQUEST_ID, SR_ACTOR_ID)
		  						  constraint CIRM_SRREQ_SRACTOR_PK primary key (SR_REQUEST_ID, SR_ACTOR_ID)
							   	    USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
								  ) pctfree 30 pctused 40 initrans 5 rowdependencies;


create table CIRM_SR_ACTIVITY ( ACTIVITY_ID number(19,0) not null,
								SR_REQUEST_ID number(19,0) not null,
								STAFF_ASSIGNED varchar2(255),
								ACTIVITY_CODE varchar2(255),
								OUTCOME_CODE varchar2(255),
								DETAILS varchar2(4000),
								DUE_DATE timestamp(3),
								COMPLETE_DATE timestamp(3),
								CREATED_BY varchar2(255),
								CREATED_DATE timestamp(3),
								UPDATED_BY varchar2(255),
								UPDATED_DATE timestamp(3),
								SYS_CREATED_DATE timestamp(3),
								constraint CIRMPK_SR_ACTIVITY_PK primary key (ACTIVITY_ID)
							   	  USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
								) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;

create table CIRM_SERVICE_CALL
(
   SERVICE_CALL_ID number(19,0) NOT NULL,
   AGENT varchar2(255) NOT NULL,
   START_TIME timestamp(3) NOT NULL,
   END_TIME timestamp(3) NOT NULL,
   FROM_PHONE varchar2(10),
   constraint CIRM_SERVICE_CALL_PK primary key (SERVICE_CALL_ID)
   	  USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;


create table CIRM_SERVICE_ACTION
(
   SERVICE_CALL_ID number(19,0) NOT NULL,
   NAME varchar2(255) NOT NULL,
   VALUE varchar2(255),
   AT_TIME timestamp(3) NOT NULL,
   constraint CIRM_SERVICE_ACTION_PK primary key (SERVICE_CALL_ID,AT_TIME)
   	  USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;


create table CIRM_GIS_INFO (
       ID number(19,0) not null,
       HASH varchar2(255),
       DATA clob,
	   GIS_SWRCDAY varchar2(10),
	   GIS_SWRECWK varchar2(10),
	   GIS_SWENFID varchar2(10),
	   GIS_MUNICIP varchar2(64),
	   GIS_TEAMOFFC varchar2(255),
	   GIS_MIACODE varchar2(255),
	   GIS_MIAZONE float(126),
	   GIS_MUNCODE varchar2(10),
	   GIS_TEAMNO number(19,0),
	   GIS_DAY number(19,0),
	   GIS_ROUTE number(19,0),
	   GIS_MIACOMD number(19,0),
	   GIS_MIACOM varchar2(255),
	   GIS_MIAGARB varchar2(255),
	   GIS_MIAGARB2 varchar2(255),
	   GIS_MIAMNT varchar2(8),
	   GIS_NETA number(19,0),
	   GIS_MIARECY varchar2(8),
	   GIS_MIARCRTE number(19,0),
	   GIS_MIATDAY varchar2(8),
	   GIS_CODENO number(19,0),
	   GIS_COMDIST number(19,0),
	   GIS_CMAINT varchar2(8),
	   GIS_STLGHT varchar2(16),
	   GIS_BULKSERV number(19,0),
	   GIS_NET_AREA_NAME VARCHAR2(50),
	   GIS_MIAMI_NEIGHBORHOOD VARCHAR2(40),
	   GIS_PW_MAINT_ZONE NUMBER(19,0),
	   GIS_FIRE_PREV_BUREAU NUMBER(19,0)
       --primary key (ID)
        constraint CIRM_GIS_INFO_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
 )  PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;

alter table CIRM_SR_REQUESTS add constraint CIRMFK_ON_SR_REQUEST_ADDRESS foreign key (SR_REQUEST_ADDRESS) references CIRM_MDC_ADDRESS;
alter table CIRM_SR_REQUESTS add constraint CIRMFK_ON_GIS_INFO_ID foreign key (GIS_INFO_ID) references CIRM_GIS_INFO;
alter table CIRM_SR_ACTOR add constraint CIRMFK_ON_SR_ACTOR_ADDRESS foreign key (SR_ACTOR_ADDRESS) references CIRM_MDC_ADDRESS;
alter table CIRM_SRREQ_SRACTOR add constraint CIRMFK_ON_SR_REQUESTS foreign key (SR_REQUEST_ID) references CIRM_SR_REQUESTS;
alter table CIRM_SRREQ_SRACTOR add constraint CIRMFK_ON_SR_ACTOR foreign key (SR_ACTOR_ID) references CIRM_SR_ACTOR;
alter table CIRM_SR_ACTIVITY add constraint CIRMFK_ON_SR_ACTIVITY foreign key (SR_REQUEST_ID) references CIRM_SR_REQUESTS;
alter table CIRM_SERVICE_ACTION add constraint CIRMFK_ON_SERVICE_ACTION foreign key (SERVICE_CALL_ID) references CIRM_SERVICE_CALL(SERVICE_CALL_ID);

create index CIRM_IDX_SR_ACTOR_TYPE on CIRM_SR_ACTOR(SR_ACTOR_TYPE) PCTFREE 30 INITRANS 100;
create index CIRM_IDX_SR_ACTOR_USERNAME on CIRM_SR_ACTOR(SR_ACTOR_USERNAME) PCTFREE 30 INITRANS 100;

create index CIRM_IDX_GIS_INFO_HASH on CIRM_GIS_INFO(HASH) INITRANS 100;
create index CIRM_IDX_ACTIVITY_CREATE_DT ON CIRM_SR_ACTIVITY(CREATED_DATE) INITRANS 100;-- COMPUTE STATISTICS ONLINE;
create index CIRM_IDX_ACTIVITY_UPDATE_DT ON CIRM_SR_ACTIVITY(UPDATED_DATE) INITRANS 100;-- COMPUTE STATISTICS ONLINE;
create index CIRM_IDX_ACTIVITY_DUE_DT ON CIRM_SR_ACTIVITY(DUE_DATE) INITRANS 100;-- COMPUTE STATISTICS ONLINE;
create index CIRM_IDX_ACTIVITY_CODE on CIRM_SR_ACTIVITY(ACTIVITY_CODE) PCTFREE 30 INITRANS 100;
create index CIRM_IDX_ACT_SR_CODE_OUT_COMPL ON CIRM_SR_ACTIVITY
(
  SR_REQUEST_ID,
  ACTIVITY_CODE,
  OUTCOME_CODE,
  COMPLETE_DATE
) PCTFREE 30 INITRANS 100;


--- MAPPED SCHEMA INDICES
create index CIRM_IDX_SRREQ_ACTORFK on CIRM_SRREQ_SRACTOR(SR_ACTOR_ID) INITRANS 100;
create index CIRM_IDX_SR_ACTOR_ADDFK on CIRM_SR_ACTOR(SR_ACTOR_ADDRESS) INITRANS 100;
create index CIRM_IDX_SR_REQUEST_GISFK on CIRM_SR_REQUESTS(GIS_INFO_ID) INITRANS 100;
create index CIRM_IDX_SR_REQUEST_ADDFK on CIRM_SR_REQUESTS(SR_REQUEST_ADDRESS) INITRANS 100;
--create index CIRM_IDX_MDC_ADDRESS_FULLADD_U on CIRM_MDC_ADDRESS(UPPER(FULL_ADDRESS)) INITRANS 100;
--06/19/2013 syed - removed function usage in index.
create index CIRM_IDX_MDC_ADDRESS_FULLADD on CIRM_MDC_ADDRESS(FULL_ADDRESS) INITRANS 100;
create index CIRM_IDX_MDC_ADDRESS_CITY on CIRM_MDC_ADDRESS(CITY) INITRANS 100;
create index CIRM_IDX_MDC_ADDRESS_ZIP on CIRM_MDC_ADDRESS(ZIP) INITRANS 100;
create index CIRM_IDX_MDC_ADDRESS_STNM on CIRM_MDC_ADDRESS(STREET_NAME) INITRANS 100;
create index CIRM_IDX_MDC_ADDRESS_STNU on CIRM_MDC_ADDRESS(STREET_NUMBER) INITRANS 100;

create index CIRM_IDX_SR_REQUEST_CREATE_DT on CIRM_SR_REQUESTS(CREATED_DATE) INITRANS 100;
create unique index CIRM_IDX_SR_REQUEST_CASE_NUM on CIRM_SR_REQUESTS(UPPER(CASE_NUMBER)) INITRANS 100;
create unique index CIRM_IDX_SR_REQUEST_CASE_NU2 on CIRM_SR_REQUESTS (CASE_NUMBER) INITRANS 100;
create index CIRM_IDX_SR_REQUEST_STATUS on CIRM_SR_REQUESTS(SR_STATUS) INITRANS 100;
