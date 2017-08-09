# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table participant (
  id                            varchar(40) not null,
  created                       bigint,
  updated                       bigint,
  email                         varchar(128) not null,
  name                          varchar(128),
  stage                         integer not null,
  version                       bigint not null,
  constraint uq_participant_email unique (email),
  constraint pk_participant primary key (id)
);

create table submission (
  id                            varchar(40) not null,
  created                       bigint,
  stage                         integer not null,
  participant_id                varchar(40),
  payload                       longblob,
  psize                         integer,
  constraint pk_submission primary key (id)
);

alter table submission add constraint fk_submission_participant_id foreign key (participant_id) references participant (id) on delete restrict on update restrict;
create index ix_submission_participant_id on submission (participant_id);


# --- !Downs

alter table submission drop foreign key fk_submission_participant_id;
drop index ix_submission_participant_id on submission;

drop table if exists participant;

drop table if exists submission;

