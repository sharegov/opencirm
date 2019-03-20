drop table if exists cirm_iri cascade;
drop table if exists cirm_iri_type cascade;
drop table if exists cirm_owl_data_property cascade;
drop table if exists cirm_owl_object_property cascade;
drop table if exists cirm_classification cascade;
drop table if exists cirm_owl_data_val_string cascade;
drop table if exists cirm_owl_data_val_clob cascade;
drop table if exists cirm_owl_data_val_date cascade;
drop table if exists cirm_owl_data_val_double cascade;
drop table if exists cirm_owl_data_val_integer cascade;
drop table if exists cirm_bo_change cascade;
drop sequence if exists cirm_sequence;
drop sequence if exists cirm_user_friendly_sequence;
     
create table cirm_iri (
  id bigint not null,
  iri character varying(255),
  iri_type_id smallint not null,
  constraint cirm_iri_irintype_uq unique(iri,iri_type_id),
  constraint cirm_iri_pk primary key (id)
);
   
create table cirm_iri_type (
  id smallint not null,
  iri character varying(20) not null,
  constraint cirm_iri_type_iri unique(iri),
  constraint cirm_iri_type_pk primary key (id)
);

create table cirm_owl_data_property (
  subject bigint not null,
  predicate bigint not null,
  datatype_id bigint not null,
  value_id  bigint,
  from_date timestamp,
  to_date timestamp,
  constraint cirm_owl_data_property_pk primary key (subject, predicate, value_id, from_date)
);

create table cirm_owl_object_property (
  subject bigint not null,
  predicate bigint not null,
  object bigint not null,
  from_date timestamp,
  to_date timestamp,
  constraint cirm_owl_object_property_pk primary key (subject, predicate, object, from_date)
);
     
create table cirm_classification (
  subject bigint not null,
  owlclass bigint not null,
  from_date timestamp,
  to_date timestamp,
  constraint cirm_classification_pk primary key (subject, owlclass, from_date)
);
	
create table cirm_owl_data_val_string
(
  id bigint not null,
  value_varchar character varying(255),
  constraint cirm_owl_data_val_string_pk primary key (id),
  constraint value_string_unique unique (value_varchar)
);
    
create table cirm_owl_data_val_clob
(
 id bigint not null,
 value_hash character varying(28) not null,
 value_varchar character varying(4000),
 value_clob text,
 constraint cirm_owl_data_val_clob_pk primary key (id)
);
	
create table cirm_owl_data_val_date
(
 id bigint not null,
 value_date timestamp(3) not null,
 constraint cirm_owl_data_val_date_pk primary key (id),
 constraint value_date_unique unique (value_date) 
);
	
create table cirm_owl_data_val_double
(
 id bigint not null,
 value_double double precision not null,
 constraint cirm_owl_data_val_double_pk primary key (id),
 constraint value_double_unique unique (value_double)
);
	
create table cirm_owl_data_val_integer
(
 id bigint not null,
 value_integer integer not null,
 constraint cirm_owl_data_val_integer_pk primary key (id),
 constraint value_integer_unique unique (value_integer)
);
	
create table cirm_bo_change
(
  bo_id bigint not null,
  change_date timestamp,
  constraint cirm_bo_change_pk primary key (bo_id, change_date)
);
	
create sequence cirm_sequence
  start 1
  minvalue 1
  cache 300;

create sequence cirm_user_friendly_sequence
  start 1 
  minvalue 1 
  increment 1 
  cache 20;


--create index CIRM_IDX_DATA_VAL_STRING on CIRM_OWL_DATA_VAL_STRING(VALUE_HASH);
alter table cirm_iri add constraint cirmfk_iri_type foreign key (iri_type_id) references cirm_iri_type;
alter table cirm_owl_data_property add constraint cirmfk_owl_data_prop_subject foreign key (subject) references cirm_iri;
alter table cirm_owl_data_property add constraint cirmfk_owl_data_prop_predicate foreign key (predicate) references cirm_iri;
alter table cirm_owl_data_property add constraint cirmfk_owl_data_prop_datatype foreign key (datatype_id) references cirm_iri;

alter table cirm_owl_object_property add constraint cirmfk_owl_obj_prop_subject foreign key (subject) references cirm_iri;
alter table cirm_owl_object_property add constraint cirmfk_owl_obj_prop_predicate foreign key (predicate) references cirm_iri;
alter table cirm_owl_object_property add constraint cirmfk_owl_obj_prop_object foreign key (object) references cirm_iri;
alter table cirm_classification add constraint cirmfk_classification_subject foreign key (subject) references cirm_iri;
alter table cirm_classification add constraint cirmfk_classification_class foreign key (owlclass) references cirm_iri;


create index cirm_idx_class on cirm_classification(subject,to_date);
create index cirm_idx_data_prop on cirm_owl_data_property(subject,to_date);
create index cirm_idx_obj_prop on cirm_owl_object_property(subject,to_date);
create index cirm_idx_iri on cirm_iri(iri);
create index cirm_idx_data_clob_hash on cirm_owl_data_val_clob(value_hash);

--Missing indices
create index cirm_idx_classification_class on cirm_classification(owlclass);
create index cirm_idx_iri_typefk on cirm_iri(iri_type_id);
create index cirm_idx_data_prop_dtfk on cirm_owl_data_property(datatype_id);
create index cirm_idx_data_prop_predfk on cirm_owl_data_property(predicate);
create index cirm_idx_obj_prop_objectfk on cirm_owl_object_property(object);
create index cirm_idx_obj_prop_predfk on cirm_owl_object_property(predicate);

-- Views	

create or replace view cirm_iri_view as
select
i.id , i.iri, it.iri as iri_type
from cirm_iri i, cirm_iri_type it
where i.iri_type_id = it.id;

create or replace view cirm_classification_view ("subject_fragment", "owlclass_fragment", "subject_id")
                                                                  as
  select replace(ia.iri, 'http://www.miamidade.gov/ontology#','') as subject_fragment,
    replace(ib.iri, 'http://www.miamidade.gov/ontology#','')      as owlclass_fragment,
      c.subject as subject_id
  from cirm_classification c,
    cirm_iri ia,
    cirm_iri ib
  where c.subject = ia.id
  and c.owlclass  = ib.id
  and c.to_date  is null
  order by c.subject;
  
create or replace view cirm_data_prop_value_view as
select
dp.subject,
i.iri as predicate_iri,
dt.iri as datatype_iri,
dp.value_id,
vdat.value_date,
vd.value_double,
vi.value_integer,
vs.value_varchar,
vc.value_hash,
vc.value_varchar as value_varchar_long,
vc.value_clob,
dp.from_date,
dp.to_date
from cirm_owl_data_property dp
inner join cirm_iri i on (dp.predicate = i.id) 
inner join cirm_iri dt on (dp.datatype_id = dt.id)
left outer join cirm_owl_data_val_date vdat on (dp.value_id = vdat.id)
left outer join cirm_owl_data_val_double vd on (dp.value_id = vd.id)
left outer join cirm_owl_data_val_integer vi on (dp.value_id = vi.id)
left outer join cirm_owl_data_val_string vs on (dp.value_id = vs.id)
left outer join cirm_owl_data_val_clob vc on (dp.value_id = vc.id);

create or replace view cirm_data_property_view as
select
dp.subject as subject_id,
dp.predicate as predicate_id,
dp.value_id,
vdat.value_date,
vd.value_double,
vi.value_integer,
vs.value_varchar,
vc.value_hash,
vc.value_varchar as value_varchar_long,
vc.value_clob,
dp.from_date,
dp.to_date
from cirm_owl_data_property dp
left outer join cirm_owl_data_val_date vdat on (dp.value_id = vdat.id)
left outer join cirm_owl_data_val_double vd on (dp.value_id = vd.id)
left outer join cirm_owl_data_val_integer vi on (dp.value_id = vi.id)
left outer join cirm_owl_data_val_string vs on (dp.value_id = vs.id)
left outer join cirm_owl_data_val_clob vc on (dp.value_id = vc.id);

create or replace view cirm_entity_view ("id", "iri", "iri_type", "class_iri", "class_iri_type")
as
  select a.id,
    replace(a.iri, 'http://www.miamidade.gov/ontology#','') as iri ,
    a.iri_type_id,
    replace(c.iri, 'http://www.miamidade.gov/ontology#','') as class_iri,
    c.iri_type_id class_iri_type
  from cirm_iri a,
    cirm_classification b,
    cirm_iri c
  where a.id     = b.subject
  and b.owlclass = c.id
  and b.to_date is null
  order by a.id desc;

create or replace view cirm_iri_exclusive_to_mapped ("id", "iri", "iri_type")
as
  select "id",
    "iri",
    "iri_type_id"
  from cirm_iri iri
  where iri.id not in
    ( select subject from cirm_classification
    union
    select owlclass from cirm_classification
    union
    select subject from cirm_owl_data_property
    union
    select predicate from cirm_owl_data_property
    union
    select subject from cirm_owl_object_property
    union
    select predicate from cirm_owl_object_property
    union
    select object from cirm_owl_object_property
    );

  
create or replace view cirm_object_property_view as
	select
	a.id as p_id,
	replace(a.iri, 'http://www.miamidade.gov/ontology#','') as predicate,
	a.iri_type_id as predicate_type,
	c.id as s_id,
	replace(c.iri, 'http://www.miamidade.gov/ontology#','') as subject,
	c.iri_type_id as subject_iri_type,
	d.id as o_id,
	replace(d.iri, 'http://www.miamidade.gov/ontology#','') as object,
	d.iri_type_id as object_iri_type
	from cirm_iri a, cirm_owl_object_property b, cirm_iri c, cirm_iri d
	where a.id = b.predicate
	and b.subject = c.id
	and b.object = d.id
	and b.to_date is null
	order by s_id;

  
  