CREATE OR REPLACE FUNCTION GETNUMBUSINESSDAY
 (start_date in date, end_date in date)
 RETURN number
 IS
    count_days number := 0;/*
---------------------------------------------------------------------------
FUNCTION    GETNUMBUSINESSDAY
---------------------------------------------------------------------------
PROGRAMMER  Syed Abbas
---------------------------------------------------------------------------
Apr 14, 2005   Initial creation by Jose E Marin (Citistat).
Jan 28, 2014   Modified function for new cirm reporting schema 	Syed Abbas (Miami-Dade County)
---------------------------------------------------------------------------
FUNCTION DESCRIPTION
This function will retrieve the number of business day between two dates.
In case of an unexpected exception, the function returns (0).
see: http://asktom.oracle.com/pls/asktom/f?p=100:11:0::::P11_QUESTION_ID:185012348071
---------------------------------------------------------------------------
*/
 BEGIN
    IF start_date IS NULL OR end_date IS NULL THEN
      RETURN(0);
    END IF;
    IF (end_date - start_date <= 0) THEN
      RETURN(0);
    END IF;
    SELECT COUNT(*)
        INTO 
        count_days
      FROM ( SELECT ROWNUM rnum
               FROM cirm_iri
              WHERE ROWNUM <= end_date - start_date+1 )
     WHERE TO_CHAR( start_date+rnum-1, 'DY' ) 
                      NOT IN ( 'SAT', 'SUN' )
     AND NOT EXISTS 
        ( SELECT NULL FROM cirm_observed_holiday WHERE holiday_date =
                TRUNC(start_date+rnum-1) );
     RETURN (count_days);
EXCEPTION
    WHEN OTHERS THEN
      RETURN (0);
END;
/


CREATE OR REPLACE FUNCTION GETNEXTBUSINESSDAY(
  P_DATE IN  DATE
 ,P_ADD_NUM  IN  INTEGER
 ) RETURN DATE AS
  --
  V_CNT                            NUMBER;
  V_BUS_DAY                        DATE := TRUNC(P_DATE);
  --
/*
---------------------------------------------------------------------------
FUNCTION    GETNEXTBUSINESSDAY
---------------------------------------------------------------------------
PROGRAMMER  Syed Abbas
---------------------------------------------------------------------------
Apr 14, 2005   Initial creation. PROGRAMMER  Jose E Marin (Citistat)
Jan 28, 2014   Modified function for new cirm reporting schema 	Syed Abbas (Miami-Dade County)
---------------------------------------------------------------------------
FUNCTION DESCRIPTION
This function will retrieve the Next business day.
In case of an unexpected exception, the function returns NULL.
see http://asktom.oracle.com/pls/apex/f?p=100:11:0::::P11_QUESTION_ID:1450404471394
---------------------------------------------------------------------------
*/
BEGIN
  --
  SELECT MAX(RNUM)
  INTO   V_CNT
  FROM   (SELECT ROWNUM RNUM
          FROM   cirm_iri)
  WHERE  ROWNUM <= P_ADD_NUM
    AND  TO_CHAR(V_BUS_DAY + RNUM, 'DY' ) NOT IN ('SAT', 'SUN')
    AND  NOT EXISTS
      (  SELECT 1
         FROM   cirm_observed_holiday
         WHERE  holiday_date = V_BUS_DAY + RNUM );
  V_BUS_DAY := V_BUS_DAY + V_CNT;
  --
  RETURN V_BUS_DAY;
  --
END;
/


CREATE OR REPLACE FUNCTION COMGETSTANDARDSROVERDUE
 (Status_code IN VARCHAR2, Created_date in date,
    SRLastUpdate IN DATE,
    DurationsDays IN number,
    DptException IN VARCHAR2 DEFAULT 'none')
RETURN NUMBER
/*
---------------------------------------------------------------------------
FUNCTION    COMGETSTANDARDSROVERDUE
---------------------------------------------------------------------------
PROGRAMMER  Jose E Marin (Citistat)
---------------------------------------------------------------------------
Apr 14, 2005   Initial creation.
---------------------------------------------------------------------------
FUNCTION DESCRIPTION
This function will return 1 for overdue SR and 0 No overdue.
In case of an unexpected exception, the function returns 0.
---------------------------------------------------------------------------
*/
IS
    intDurationsDays NUMBER;
BEGIN
  intDurationsDays := DurationsDays;
  IF DptException <> 'none' THEN
    IF DptException = 'COMCE' AND Created_date BETWEEN '22-AUG-2005' AND '31-AUG-2005' THEN -- Hurricane Katrina.
      intDurationsDays := intDurationsDays + 5;
    END IF;
    IF DptException = 'COMCE' AND Created_date BETWEEN '17-OCT-2005' AND '2-NOV-2005' THEN -- Hurricane Wilma.
      intDurationsDays := intDurationsDays + 10;
    END IF;
  END IF;

  IF (Status_code = 'C-CLOSED' OR Status_code='C-DUP')  THEN
    IF GetNumBusinessDay(Created_date, to_date(SRLastUpdate)) > intDurationsDays THEN
      RETURN (1);
    ELSE
      RETURN (0);
    END IF;
  ELSE
    IF GetNumBusinessDay(Created_date, to_date(sysdate)) > intDurationsDays THEN
      RETURN (1);
    ELSE
      RETURN (0);
    END IF;
  END IF;
  EXCEPTION
  WHEN OTHERS THEN
    RETURN (0);
END;

