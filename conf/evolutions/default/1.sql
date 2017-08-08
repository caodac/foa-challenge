# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table participant (
  id                            uuid not null,
  created                       bigint,
  updated                       bigint,
  email                         varchar(255) not null,
  firstname                     varchar(255),
  lastname                      varchar(255),
  stage                         integer not null,
  version                       bigint not null,
  constraint uq_participant_email unique (email),
  constraint pk_participant primary key (id)
);

create table submission (
  id                            bigint auto_increment not null,
  created                       bigint,
  stage                         integer not null,
  participant_id                uuid,
  payload                       blob,
  psize                         integer,
  constraint pk_submission primary key (id)
);

alter table submission add constraint fk_submission_participant_id foreign key (participant_id) references participant (id) on delete restrict on update restrict;
create index ix_submission_participant_id on submission (participant_id);


# --- !Downs

alter table submission drop constraint if exists fk_submission_participant_id;
drop index if exists ix_submission_participant_id;

drop table if exists participant;

drop table if exists submission;

