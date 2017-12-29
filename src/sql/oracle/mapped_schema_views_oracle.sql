CREATE OR REPLACE FORCE VIEW "CIRMSCHM"."CIRM_MDC_ADDRESS_VIEW" ("ADDRESS_ID", "FULL_ADDRESS", "STREET_NUMBER", "STREET_NAME", "STREET_NAME_PREFIX", "STREET_NAME_SUFFIX", "UNIT", "XCOORDINATE", "YCOORDINATE", "CITY", "STATE", "ZIP", "CITY_SHORT", "STATE_SHORT")
AS
  SELECT A."ADDRESS_ID",
    A."FULL_ADDRESS",
    A."STREET_NUMBER",
    A."STREET_NAME",
    A."STREET_NAME_PREFIX",
    A."STREET_NAME_SUFFIX",
    A."UNIT",
    A."XCOORDINATE",
    A."YCOORDINATE",
    A."CITY",
    A."STATE",
    A."ZIP",
    REPLACE(IA.IRI, 'http://www.miamidade.gov/ontology#','') AS CITY_SHORT,
    REPLACE(IB.IRI, 'http://www.miamidade.gov/ontology#','') AS STATE_SHORT
  FROM CIRM_MDC_ADDRESS A,
    CIRM_IRI IA,
    CIRM_IRI IB
  WHERE A.CITY = IA.ID
  AND A.STATE  = IB.ID;
  
CREATE OR REPLACE VIEW CIRMSCHM.CIRM_SR_REQUESTS_VIEW AS
SELECT
SR.SR_REQUEST_ID,
CL.OWLCLASS AS SR_TYPE,
IRI.IRI AS SR_TYPE_IRI,
SR.SR_STATUS,
SR.CASE_NUMBER,
SR.SR_INTAKE_METHOD,
SR.SR_PRIORITY,
SR.SR_REQUEST_ADDRESS,
SR.GIS_INFO_ID,
SR.SR_XCOORDINATE,
SR.SR_YCOORDINATE,
SR.SR_DESCRIPTION,
SR.DUE_DATE,
SR.CREATED_BY,
SR.CREATED_DATE,
SR.UPDATED_BY,
SR.UPDATED_DATE,
SR.SR_FOLIO,
A.FULL_ADDRESS,
A.STREET_NUMBER,
A.STREET_NAME,
A.STREET_NAME_PREFIX,
A.STREET_NAME_SUFFIX,
A.UNIT,
A.CITY,
A.STATE,
A.ZIP,
A.XCOORDINATE,
A.YCOORDINATE,
A.LOCATION_NAME
FROM CIRM_SR_REQUESTS SR,
CIRM_MDC_ADDRESS A,
CIRM_CLASSIFICATION CL,
CIRM_IRI IRI
WHERE CL.SUBJECT = SR.SR_REQUEST_ID
AND CL.TO_DATE IS NULL
AND SR.SR_REQUEST_ADDRESS = A.ADDRESS_ID
AND IRI.ID = CL.OWLCLASS

  
CREATE OR REPLACE VIEW CIRM_SR_CLASSIFICATION_VIEW AS
SELECT
SR."SR_REQUEST_ID",
SR."SR_STATUS",
SR."CASE_NUMBER",
SR."SR_INTAKE_METHOD",
SR."SR_PRIORITY",
SR."SR_REQUEST_ADDRESS",
SR."SR_XCOORDINATE",
SR."SR_YCOORDINATE",
SR."SR_DESCRIPTION",
SR."SR_COMMENTS",
SR."DUE_DATE",
SR."CREATED_BY",
SR."CREATED_DATE",
SR."UPDATED_BY",
SR."UPDATED_DATE",
CLSV.SUBJECT_FRAGMENT AS SR_IRI,
CLSV.OWLCLASS_FRAGMENT AS SR_CLASS
FROM CIRM_SR_REQUESTS SR, CIRM_CLASSIFICATION_VIEW CLSV
WHERE SR.SR_REQUEST_ID = CLSV.SUBJECT_ID --TODO PROBLEM HERE
ORDER BY SR.SR_REQUEST_ID;

--Commented out by Syed ---This view is in this file twice??? 
--CREATE OR REPLACE VIEW CIRM_SR_REQUESTS_VIEW AS
--SELECT
--SR.SR_REQUEST_ID,
--SR.SR_STATUS,
--SR.CASE_NUMBER,
--SR.SR_INTAKE_METHOD,
--SR.SR_PRIORITY,
--SR.SR_XCOORDINATE,
--SR.SR_YCOORDINATE,
--SR.SR_REQUEST_ADDRESS,
--A.FULL_ADDRESS,
--A.STREET_NUMBER,
--A.STREET_NAME,
--A.CITY,
--A.STATE,
--A.ZIP,
--A.XCOORDINATE,
--A.YCOORDINATE
--FROM CIRM_SR_REQUESTS SR, CIRM_MDC_ADDRESS A, CIRM_IRI SRI, CIRM_IRI AI
--WHERE SR.SR_REQUEST_ADDRESS = A.ADDRESS_ID
--AND SR.SR_REQUEST_ID = SRI.ID
--AND A.ADDRESS_ID = AI.ID
--ORDER BY SR.SR_REQUEST_ID DESC;

CREATE OR REPLACE VIEW CIRM_ADVANCED_SEARCH_VIEW AS
SELECT a.SR_REQUEST_ID, i2.IRI AS TYPE, addr.FULL_ADDRESS, addr.ZIP, i1.IRI AS CITY, a.SR_STATUS, acts.COMPLETE_DATE, a.CREATED_DATE 
FROM CIRM_SR_REQUESTS a 
LEFT JOIN (SELECT b.SR_REQUEST_ID, MAX(b.COMPLETE_DATE) lastActivityDate FROM CIRM_SR_ACTIVITY b GROUP BY b.SR_REQUEST_ID ORDER BY b.SR_REQUEST_ID ) 
tempSRActivity on a.SR_REQUEST_ID = tempSRActivity.SR_REQUEST_ID 
LEFT JOIN CIRM_SR_ACTIVITY acts ON tempSRActivity.lastActivityDate = acts.COMPLETE_DATE
LEFT JOIN CIRM_MDC_ADDRESS addr ON a.SR_REQUEST_ADDRESS = addr.ADDRESS_ID
LEFT JOIN CIRM_IRI i1 ON addr.CITY = i1.ID 
LEFT JOIN CIRM_CLASSIFICATION cl ON cl.SUBJECT = a.SR_REQUEST_ID 
LEFT JOIN CIRM_IRI i2 ON cl.OWLCLASS = i2.ID 
WHERE cl.TO_DATE IS NULL