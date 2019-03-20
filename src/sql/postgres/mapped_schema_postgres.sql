drop table if exists CIRM_SRREQ_SRACTOR cascade;
drop table if exists CIRM_SR_ACTOR cascade;
drop table if exists CIRM_SR_ACTIVITY cascade;
drop table if exists CIRM_SR_REQUESTS cascade;
drop table if exists CIRM_MDC_ADDRESS cascade;
drop table if exists CIRM_SERVICE_ACTION cascade;
drop table if exists CIRM_SERVICE_CALL cascade;
drop table if exists CIRM_GIS_INFO cascade;

create table cirm_mdc_address ( 
address_id bigint not null,
full_address character varying(255),
street_number integer, 
street_name character varying(255), 
street_name_prefix character varying(255), 
street_name_suffix character varying(255), 
unit character varying(255), 
city bigint, 
state bigint, 
zip integer, 
xcoordinate real, 
ycoordinate real,
location_name character varying(255),
constraint cirm_mdc_address_pk primary key (address_id)
);
                                
create table cirm_sr_requests ( 
sr_request_id bigint not null, 
sr_status character varying(255), 
case_number character varying(255), 
sr_intake_method character varying(255),
sr_priority character varying(255),
sr_request_address bigint, 
gis_info_id bigint, 
sr_xcoordinate real, 
sr_ycoordinate real,
sr_description character varying(4000),
sr_comments character varying(4000),
due_date timestamp,
created_by character varying(255),
created_date timestamp,
updated_by character varying(255),
updated_date timestamp,
constraint cirm_sr_requests_pk primary key (sr_request_id));
                                
create table cirm_sr_actor (
sr_actor_id bigint not null,
sr_actor_name character varying(255),
sr_actor_lname character varying(255),
sr_actor_initials character varying(255),
sr_actor_title character varying(255),
sr_actor_suffix character varying(255),
sr_actor_phone_number character varying(255),
sr_actor_email character varying(255),
sr_actor_contact_method character varying(255),
sr_actor_type character varying(255),
sr_actor_address bigint,
sr_actor_work_phone_no character varying(255),
sr_actor_cell_phone_no character varying(255),
sr_actor_other_phone_no character varying(255),
sr_actor_fax_phone_no character varying(255),
created_by character varying(255),
created_date timestamp,
updated_by character varying(255),
updated_date timestamp,
constraint cirm_sr_actor_pk primary key (sr_actor_id));
							
create table cirm_srreq_sractor	( 
sr_request_id bigint not null,
sr_actor_id bigint not null,
constraint cirm_srreq_sractor_pk primary key (sr_request_id, sr_actor_id));


create table cirm_sr_activity ( 
activity_id bigint not null,
sr_request_id bigint not null,
staff_assigned character varying(255),
activity_code character varying(255),
outcome_code character varying(255),
details character varying(4000),
due_date timestamp,
complete_date timestamp,
created_by character varying(255),
created_date timestamp,
updated_by character varying(255),
updated_date timestamp,
constraint cirmpk_sr_activity_pk primary key (activity_id));

create table cirm_service_call (
service_call_id bigint not null,
agent character varying(255) not null,
start_time timestamp not null,
end_time timestamp not null,
from_phone character varying(10),
constraint cirm_service_call_pk primary key (service_call_id));


create table cirm_service_action(
service_call_id bigint not null,
name character varying(255) not null,
value character varying(255),
at_time timestamp not null,
constraint cirm_service_action_pk primary key (service_call_id,at_time));


create table cirm_gis_info (
id bigint not null,
hash character varying(255),
data text,
gis_swrcday character varying(10),
gis_swrecwk character varying(10),
gis_swenfid character varying(10),
gis_municip character varying(64),
gis_teamoffc character varying(255),
gis_miacode character varying(255),
gis_miazone character varying(126),
gis_muncode character varying(10),
gis_teamno bigint,
gis_day bigint,
gis_route bigint,
gis_miacomd bigint,
gis_miacom character varying(255),
gis_miagarb character varying(255),
gis_miagarb2 character varying(255),
gis_miamnt character varying(8),
gis_neta bigint,
gis_miarecy character varying(8),
gis_miarcrte bigint,
gis_miatday character varying(8),
gis_codeno bigint,
gis_comdist bigint,
gis_cmaint character varying(8),
gis_stlght character varying(17),
gis_bulkserv bigint,
gis_net_area_name character varying(50),
gis_miami_neighborhood character varying(40),
gis_pw_maint_zone bigint,
gis_fire_prev_bureau bigint
constraint cirm_gis_info_pk primary key (id));

alter table cirm_sr_requests add constraint cirmfk_on_sr_request_address foreign key (sr_request_address) references cirm_mdc_address;
alter table cirm_sr_requests add constraint cirmfk_on_gis_info_id foreign key (gis_info_id) references cirm_gis_info;
alter table cirm_sr_actor add constraint cirmfk_on_sr_actor_address foreign key (sr_actor_address) references cirm_mdc_address;
alter table cirm_srreq_sractor add constraint cirmfk_on_sr_requests foreign key (sr_request_id) references cirm_sr_requests;
alter table cirm_srreq_sractor add constraint cirmfk_on_sr_actor foreign key (sr_actor_id) references cirm_sr_actor;
alter table cirm_sr_activity add constraint cirmfk_on_sr_activity foreign key (sr_request_id) references cirm_sr_requests;
alter table cirm_service_action add constraint cirmfk_on_service_action foreign key (service_call_id) references cirm_service_call(service_call_id);

create index cirm_idx_gis_info_hash on cirm_gis_info(hash);
create index cirm_idx_activity_sr_id on cirm_sr_activity(sr_request_id);

create index cirm_idx_srreq_actorfk on cirm_srreq_sractor(sr_actor_id) ;
create index cirm_idx_sr_actor_addfk on cirm_sr_actor(sr_actor_address) ;
create index cirm_idx_sr_request_gisfk on cirm_sr_requests(gis_info_id) ;
create index cirm_idx_sr_request_addfk on cirm_sr_requests(sr_request_address) ;
create index cirm_idx_mdc_address_fulladd on cirm_mdc_address(full_address) ;
create index cirm_idx_sr_request_create_dt on cirm_sr_requests(created_date) ;
create index cirm_idx_sr_request_case_num on cirm_sr_requests(upper(case_number)) ;

-- views 

create or replace view cirm_mdc_address_view ("address_id", "full_address", "street_number", "street_name", "street_name_prefix", "street_name_suffix", "unit", "xcoordinate", "ycoordinate", "city", "state", "zip", "city_short", "state_short")
as select a.address_id,
    a.full_address,
    a.street_number,
    a.street_name,
    a.street_name_prefix,
    a.street_name_suffix,
    a.unit,
    a.xcoordinate,
    a.ycoordinate,
    a.city,
    a.state,
    a.zip,
    replace(ia.iri, 'http://www.miamidade.gov/ontology#','') as city_short,
    replace(ib.iri, 'http://www.miamidade.gov/ontology#','') as state_short
  from cirm_mdc_address as a,
    cirm_iri as ia,
    cirm_iri as ib
  where a.city = ia.id
  and a.state  = ib.id;
  
create or replace view cirm_sr_requests_view ("sr_request_id", "sr_type", "sr_status", "case_number",  "sr_intake_method", "sr_priority", "sr_xcoordinate", "sr_ycoordinate", "sr_request_address","created_date", "full_address", "street_number", "street_name","street_name_prefix","street_name_suffix", "unit" , "city", "state", "zip", "xcoordinate", "ycoordinate", "location_name")
as
  select sr.sr_request_id,
    cl.owlclass as sr_type,
    sr.sr_status,
    sr.case_number,
    sr.sr_intake_method,
    sr.sr_priority,
    sr.sr_xcoordinate,
    sr.sr_ycoordinate,
    sr.sr_request_address,
    sr.created_date,
      a.full_address,
    a.street_number,
    a.street_name,
    a.street_name_prefix,
    a.street_name_suffix,
    a.unit,
    a.city,
    a.state,
    a.zip,
    a.xcoordinate,
    a.ycoordinate,
    a.location_name
  from cirm_sr_requests sr,
    cirm_mdc_address a,
    cirm_classification cl  
  where 
  cl.subject = sr.sr_request_id
  and cl.to_date is null
  and sr.sr_request_address = a.address_id;
 -- and sr.sr_request_id        = sri.id
 -- and a.address_id            = ai.id
--  order by sr.sr_request_id desc

  
create or replace view cirm_sr_classification_view as
select
sr.sr_request_id,
sr.sr_status,
sr.case_number,
sr.sr_intake_method,
sr.sr_priority,
sr.sr_request_address,
sr.sr_xcoordinate,
sr.sr_ycoordinate,
sr.sr_description,
sr.sr_comments,
sr.due_date,
sr.created_by,
sr.created_date,
sr.updated_by,
sr.updated_date,
clsv.subject_fragment as sr_iri,
clsv.owlclass_fragment as sr_class
from cirm_sr_requests sr, cirm_classification_view clsv
where sr.sr_request_id = clsv.subject_id
order by sr.sr_request_id;

create or replace view cirm_advanced_search_view as
select a.sr_request_id, i2.iri as type, addr.full_address, addr.zip, i1.iri as city, a.sr_status, acts.complete_date, a.created_date 
from cirm_sr_requests a 
left join (select b.sr_request_id, max(b.complete_date) lastactivitydate from cirm_sr_activity b group by b.sr_request_id order by b.sr_request_id ) 
tempsractivity on a.sr_request_id = tempsractivity.sr_request_id 
left join cirm_sr_activity acts on tempsractivity.lastactivitydate = acts.complete_date
left join cirm_mdc_address addr on a.sr_request_address = addr.address_id
left join cirm_iri i1 on addr.city = i1.id 
left join cirm_classification cl on cl.subject = a.sr_request_id 
left join cirm_iri i2 on cl.owlclass = i2.id 
where cl.to_date is null