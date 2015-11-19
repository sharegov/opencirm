CREATE OR REPLACE VIEW CIRM_IRI_VIEW AS
SELECT
I.ID , I.IRI, IT.IRI AS IRI_TYPE
FROM CIRM_IRI I, CIRM_IRI_TYPE IT
WHERE I.IRI_TYPE_ID = IT.ID;

CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_CLASSIFICATION_VIEW" ("SUBJECT_FRAGMENT", "OWLCLASS_FRAGMENT", "SUBJECT_ID")
                                                                  AS
  SELECT REPLACE(IA.IRI, 'http://www.miamidade.gov/ontology#','') AS SUBJECT_FRAGMENT,
    REPLACE(IB.IRI, 'http://www.miamidade.gov/ontology#','')      AS OWLCLASS_FRAGMENT,
      C.SUBJECT AS SUBJECT_ID
  FROM CIRM_CLASSIFICATION C,
    CIRM_IRI IA,
    CIRM_IRI IB
  WHERE C.SUBJECT = IA.ID
  AND C.OWLCLASS  = IB.ID
  AND C.TO_DATE  IS NULL
  ORDER BY C.SUBJECT;
  
 
CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_DATA_PROP_VALUE_VIEW" AS
SELECT
DP.SUBJECT,
I.IRI AS PREDICATE_IRI,
DT.IRI AS DATATYPE_IRI,
DP.VALUE_ID,
VDAT.VALUE_DATE,
VD.VALUE_DOUBLE,
VI.VALUE_INTEGER,
VS.VALUE_VARCHAR,
VC.VALUE_HASH,
VC.VALUE_VARCHAR AS VALUE_VARCHAR_LONG,
VC.VALUE_CLOB,
DP.FROM_DATE,
DP.TO_DATE
FROM CIRM_OWL_DATA_PROPERTY DP
INNER JOIN CIRM_IRI I ON (DP.PREDICATE = I.ID) 
INNER JOIN CIRM_IRI DT ON (DP.DATATYPE_ID = DT.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_DATE VDAT ON (DP.VALUE_ID = VDAT.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_DOUBLE VD ON (DP.VALUE_ID = VD.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_INTEGER VI ON (DP.VALUE_ID = VI.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_STRING VS ON (DP.VALUE_ID = VS.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_CLOB VC ON (DP.VALUE_ID = VC.ID);
  
-- Shows data properties and any of their values in one view.
-- Essential for Querytranslator
CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_DATA_PROPERTY_VIEW" AS
SELECT
DP.SUBJECT AS SUBJECT_ID,
DP.PREDICATE AS PREDICATE_ID,
DP.VALUE_ID,
VDAT.VALUE_DATE,
VD.VALUE_DOUBLE,
VI.VALUE_INTEGER,
VS.VALUE_VARCHAR,
VC.VALUE_HASH,
VC.VALUE_VARCHAR AS VALUE_VARCHAR_LONG,
VC.VALUE_CLOB,
DP.FROM_DATE,
DP.TO_DATE
FROM CIRM_OWL_DATA_PROPERTY DP
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_DATE VDAT ON (DP.VALUE_ID = VDAT.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_DOUBLE VD ON (DP.VALUE_ID = VD.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_INTEGER VI ON (DP.VALUE_ID = VI.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_STRING VS ON (DP.VALUE_ID = VS.ID)
LEFT OUTER JOIN CIRM_OWL_DATA_VAL_CLOB VC ON (DP.VALUE_ID = VC.ID);

  
CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_ENTITY_VIEW" ("ID", "IRI", "IRI_TYPE", "CLASS_IRI", "CLASS_IRI_TYPE")
AS
  SELECT A.ID,
    REPLACE(A.IRI, 'http://www.miamidade.gov/ontology#','') AS IRI ,
    A.IRI_TYPE,
    REPLACE(C.IRI, 'http://www.miamidade.gov/ontology#','') AS CLASS_IRI,
    C.IRI_TYPE CLASS_IRI_TYPE
  FROM CIRM_IRI A,
    CIRM_CLASSIFICATION B,
    CIRM_IRI C
  WHERE A.ID     = B.SUBJECT
  AND B.OWLCLASS = C.ID
  AND B.TO_DATE IS NULL
  ORDER BY A.ID DESC;
  
  
  
  CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_IRI_EXCLUSIVE_TO_MAPPED" ("ID", "IRI", "IRI_TYPE")
AS
  SELECT "ID",
    "IRI",
    "IRI_TYPE"
  FROM CIRM_IRI IRI
  WHERE IRI.ID NOT IN
    ( SELECT SUBJECT FROM CIRM_CLASSIFICATION
    UNION
    SELECT OWLCLASS FROM CIRM_CLASSIFICATION
    UNION
    SELECT SUBJECT FROM CIRM_OWL_DATA_PROPERTY
    UNION
    SELECT PREDICATE FROM CIRM_OWL_DATA_PROPERTY
    UNION
    SELECT SUBJECT FROM CIRM_OWL_OBJECT_PROPERTY
    UNION
    SELECT PREDICATE FROM CIRM_OWL_OBJECT_PROPERTY
    UNION
    SELECT OBJECT FROM CIRM_OWL_OBJECT_PROPERTY
    );
    
	CREATE OR REPLACE VIEW CIRM_OBJECT_PROPERTY_VIEW AS
	SELECT
	A.ID AS P_ID,
	REPLACE(A.IRI, 'http://www.miamidade.gov/ontology#','') AS PREDICATE,
	A.IRI_TYPE_ID AS PREDICATE_TYPE,
	C.ID AS S_ID,
	REPLACE(C.IRI, 'http://www.miamidade.gov/ontology#','') AS SUBJECT,
	C.IRI_TYPE_ID AS SUBJECT_IRI_TYPE,
	D.ID AS O_ID,
	REPLACE(D.IRI, 'http://www.miamidade.gov/ontology#','') AS OBJECT,
	D.IRI_TYPE_ID AS OBJECT_IRI_TYPE
	FROM CIRM_IRI A, CIRM_OWL_OBJECT_PROPERTY B, CIRM_IRI C, CIRM_IRI D
	WHERE A.ID = B.PREDICATE
	AND B.SUBJECT = C.ID
	AND B.OBJECT = D.ID
	AND B.TO_DATE IS NULL
	ORDER BY S_ID;

  
  