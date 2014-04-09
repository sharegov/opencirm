	drop table CIRM_IRI cascade constraints;
	drop table CIRM_IRI_TYPE cascade constraints;
   	drop table CIRM_OWL_DATA_PROPERTY cascade constraints;
   	drop table CIRM_OWL_OBJECT_PROPERTY cascade constraints;
   	drop table CIRM_CLASSIFICATION cascade constraints;
	drop table CIRM_OWL_DATA_VAL_STRING cascade constraints;
	drop table CIRM_OWL_DATA_VAL_CLOB cascade constraints;
	drop table CIRM_OWL_DATA_VAL_DATE cascade constraints;
	drop table CIRM_OWL_DATA_VAL_DOUBLE cascade constraints;
	drop table CIRM_OWL_DATA_VAL_INTEGER cascade constraints;
	drop table CIRM_BO_CHANGE cascade constraints;
	
   	drop sequence CIRM_SEQUENCE;
   	
   	drop sequence CIRM_USER_FRIENDLY_SEQUENCE;

   	--drop index CIRM_IDX_PROP_VAL_HASH;
   	--drop index CIRM_IDX_DATA_VAL_HASH;
   	--drop index CIRM_IDX_DATA_VAL_STRING;

   	--ORACLE DEFAULTS: TABLES: 
   	-- PCTFREE 10 (%) reserved space for updates per block
   	-- PCTUSED 40 (%) minimum used space per block
   	-- INITRANS 1 minimum used space per block
   	
   create table CIRM_IRI (
        ID number(19,0) not null,
        IRI varchar2(255),
        IRI_TYPE_ID SMALLINT not null,
		constraint CIRM_IRI_IRInTYPE_UQ unique(IRI,IRI_TYPE_ID) 
			USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
        constraint CIRM_IRI_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
    ) PCTFREE 30 INITRANS 100 ROWDEPENDENCIES;    

   create table CIRM_IRI_TYPE (
        ID SMALLINT not null,
   		IRI varchar2 (20) not null,
		constraint CIRM_IRI_TYPE_IRI unique(IRI) 
			USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
        constraint CIRM_IRI_TYPE_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
   ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;

    create table CIRM_OWL_DATA_PROPERTY (
        SUBJECT number(19,0) NOT NULL,
        PREDICATE number(19,0) NOT NULL,
        DATATYPE_ID number(19,0) NOT NULL,
        --DATATYPE varchar2(255),
	  	--LANG varchar2(255),
	    VALUE_ID  number(19,0),
        --VALUE_HASH varchar2(28),
	  	FROM_DATE timestamp,
        TO_DATE timestamp,
        --primary key (SUBJECT, PREDICATE, VALUE_ID, FROM_DATE)
        constraint CIRM_OWL_DATA_PROPERTY_PK primary key (SUBJECT, PREDICATE, VALUE_ID, FROM_DATE) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
    ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;

    create table CIRM_OWL_OBJECT_PROPERTY (
	  	SUBJECT number(19,0) not null,
	  	PREDICATE number(19,0) not null,
        OBJECT number(19,0) not null,
	  	FROM_DATE timestamp,
        TO_DATE timestamp,
        --primary key (SUBJECT, PREDICATE, OBJECT, FROM_DATE)
        constraint CIRM_OWL_OBJECT_PROPERTY_PK primary key (SUBJECT, PREDICATE, OBJECT, FROM_DATE) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
    ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
     
    create table CIRM_CLASSIFICATION (
		SUBJECT number(19,0) not null,
		OWLCLASS number(19,0) not null,
		FROM_DATE timestamp,
		TO_DATE timestamp,
		--primary key (SUBJECT, OWLCLASS, FROM_DATE)
		constraint CIRM_CLASSIFICATION_PK primary key (SUBJECT, OWLCLASS, FROM_DATE) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
    ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
    create table CIRM_OWL_DATA_VAL_STRING
    (
     	ID number(19,0) NOT NULL,
     	VALUE_VARCHAR varchar2(255),
     	--primary key(ID)
     	constraint CIRM_OWL_DATA_VAL_STRING_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
       constraint VALUE_STRING_UNIQUE unique (VALUE_VARCHAR) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
    ) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
    
	create table CIRM_OWL_DATA_VAL_CLOB
	(
	   ID number(19,0) NOT NULL,
	   VALUE_HASH varchar2(28) NOT NULL,
	   VALUE_VARCHAR varchar2(4000),
	   VALUE_CLOB clob,
	   --primary key (ID)
	   constraint CIRM_OWL_DATA_VAL_CLOB_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
	   --constraint varcharOrClobNotNull check(VALUE_VARCHAR not null or VALUE_CLOB not null)
	) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
	create table CIRM_OWL_DATA_VAL_DATE
	(
	   ID number(19,0) NOT NULL,
	   VALUE_DATE timestamp(3) NOT NULL,
	   --primary key (ID),
	   constraint CIRM_OWL_DATA_VAL_DATE_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
	   --unique(VALUE_DATE)
       constraint VALUE_DATE_UNIQUE unique (VALUE_DATE) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
	) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
	create table CIRM_OWL_DATA_VAL_DOUBLE
	(
	   ID number(19,0) NOT NULL,
	   VALUE_DOUBLE double precision NOT NULL,
	   --primary key (ID),
	   constraint CIRM_OWL_DATA_VAL_DOUBLE_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
   	   --unique(VALUE_DOUBLE)
       constraint VALUE_DOUBLE_UNIQUE unique (VALUE_DOUBLE) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
	) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
	create table CIRM_OWL_DATA_VAL_INTEGER
	(
	   ID number(19,0) NOT NULL,
	   VALUE_INTEGER INTEGER NOT NULL,
	   --primary key (ID),
	   constraint CIRM_OWL_DATA_VAL_INTEGER_PK primary key (ID) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255,
   	   --unique(VALUE_INTEGER)
       constraint VALUE_INTEGER_UNIQUE unique (VALUE_INTEGER) 
        	USING INDEX PCTFREE 30 INITRANS 100 MAXTRANS 255
	) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
	create table CIRM_BO_CHANGE
	(
	 	BO_ID number(19,0) NOT NULL,
	 	CHANGE_DATE timestamp,
	 	constraint CIRM_BO_CHANGE_PK primary key (BO_ID, CHANGE_DATE)
	 		using index PCTFREE 30 INITRANS 100 MAXTRANS 255
	) PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES;
	
    create sequence CIRM_SEQUENCE --START WITH 114000;
	  START WITH 1
	  MINVALUE 1
  	  NOCYCLE
  	  CACHE 300;

	CREATE SEQUENCE CIRM_USER_FRIENDLY_SEQUENCE
		start with 1 
		minvalue 1 
		increment by 1 
		cache 20
		order;


	--create index CIRM_IDX_DATA_VAL_STRING on CIRM_OWL_DATA_VAL_STRING(VALUE_HASH);
	alter table CIRM_IRI add constraint CIRMFK_IRI_TYPE foreign key (IRI_TYPE_ID) references CIRM_IRI_TYPE;
    alter table CIRM_OWL_DATA_PROPERTY add constraint CIRMFK_OWL_DATA_PROP_SUBJECT foreign key (SUBJECT) references CIRM_IRI;
	alter table CIRM_OWL_DATA_PROPERTY add constraint CIRMFK_OWL_DATA_PROP_PREDICATE foreign key (PREDICATE) references CIRM_IRI;
	alter table CIRM_OWL_DATA_PROPERTY add constraint CIRMFK_OWL_DATA_PROP_DATATYPE foreign key (DATATYPE_ID) references CIRM_IRI;
	--alter table CIRM_OWL_DATA_PROPERTY add constraint CIRM_OWL_DATA_VALUE foreign key (VALUE_ID) references CIRM_DATA_PROP_VALUE_ID_VIEW;
	--alter table CIRM_OWL_DATA_PROPERTY add constraint CIRM_OWL_DATA_VALUE_DATE foreign key (VALUE_ID) references CIRM_OWL_DATA_VAL_DATE;
	--alter table CIRM_OWL_DATA_PROPERTY add constraint CIRM_OWL_DATA_VALUE_DOUBLE foreign key (VALUE_ID) references CIRM_OWL_DATA_VAL_DOUBLE;
	--alter table CIRM_OWL_DATA_PROPERTY add constraint CIRM_OWL_DATA_VALUE_INTEGER foreign key (VALUE_ID) references CIRM_OWL_DATA_VAL_INTEGER;
	--alter table CIRM_OWL_DATA_PROPERTY add constraint CIRM_OWL_DATA_VALUE_STRING foreign key (VALUE_ID) references CIRM_OWL_DATA_VAL_STRING;
	alter table CIRM_OWL_OBJECT_PROPERTY add constraint CIRMFK_OWL_OBJ_PROP_SUBJECT foreign key (SUBJECT) references CIRM_IRI;
	alter table CIRM_OWL_OBJECT_PROPERTY add constraint CIRMFK_OWL_OBJ_PROP_PREDICATE foreign key (PREDICATE) references CIRM_IRI;
	alter table CIRM_OWL_OBJECT_PROPERTY add constraint CIRMFK_OWL_OBJ_PROP_OBJECT foreign key (OBJECT) references CIRM_IRI;
	alter table CIRM_CLASSIFICATION add constraint CIRMFK_CLASSIFICATION_SUBJECT foreign key (SUBJECT) references CIRM_IRI;
	alter table CIRM_CLASSIFICATION add constraint CIRMFK_CLASSIFICATION_CLASS foreign key (OWLCLASS) references CIRM_IRI;
	
	--create index CIRM_IDX_PROP_VAL_HASH on CIRM_OWL_DATA_PROPERTY(VALUE_HASH);
	--create index CIRM_IDX_DATA_VAL_HASH on CIRM_OWL_DATA_VALUE(VALUE_HASH);	
	
	create index CIRM_IDX_CLASS on CIRM_CLASSIFICATION(SUBJECT,TO_DATE) INITRANS 100;
	create index CIRM_IDX_DATA_PROP on CIRM_OWL_DATA_PROPERTY(SUBJECT,TO_DATE) INITRANS 100;
	create index CIRM_IDX_OBJ_PROP on CIRM_OWL_OBJECT_PROPERTY(SUBJECT,TO_DATE) INITRANS 100;
	create index CIRM_IDX_IRI on CIRM_IRI(IRI) INITRANS 100;
	create index CIRM_IDX_DATA_CLOB_HASH on CIRM_OWL_DATA_VAL_CLOB(VALUE_HASH) INITRANS 100 COMPUTE STATISTICS;
	
	--Missing indices
	create index CIRM_IDX_CLASSIFICATION_CLASS on CIRM_CLASSIFICATION(OWLCLASS) INITRANS 100;
	create index CIRM_IDX_IRI_TYPEFK on CIRM_IRI(IRI_TYPE_ID) INITRANS 100;
	create index CIRM_IDX_DATA_PROP_DTFK on CIRM_OWL_DATA_PROPERTY(DATATYPE_ID) INITRANS 100;
	create index CIRM_IDX_DATA_PROP_PREDFK on CIRM_OWL_DATA_PROPERTY(PREDICATE) INITRANS 100;
	create index CIRM_IDX_OBJ_PROP_OBJECTFK on CIRM_OWL_OBJECT_PROPERTY(OBJECT) INITRANS 100;
	create index CIRM_IDX_OBJ_PROP_PREDFK on CIRM_OWL_OBJECT_PROPERTY(PREDICATE) INITRANS 100;

	
