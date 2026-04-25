create table event_participants (role smallint check ((role between 0 and 2)), event_id uuid, user_id uuid not null, primary key (user_id));
create table event_tags (event_id uuid not null, tag varchar(100));
create table events (deleted boolean not null, duration numeric(21,0), created_at timestamp(6), start_time timestamp(6), created_by uuid, id uuid not null, interest_group_id uuid, description varchar(255) not null, name varchar(255) not null, primary key (id));
comment on column events.deleted is 'Soft-delete indicator';
create table reservations (guests integer not null, event_id uuid not null, user_id uuid not null, payload varchar(255), status varchar(255) check ((status in ('PENDING','ACCEPTED','WHITELIST','DENIED','WITHDREW'))), primary key (event_id, user_id));
alter table if exists event_participants add constraint FK2x391urx4up03f4jp2y9mdt5x foreign key (event_id) references events;
alter table if exists event_tags add constraint FKiwoyitw224ykom58m5xnoa9y6 foreign key (event_id) references events;
alter table if exists reservations add constraint FKcnr8finplwp8whntrr02jpvre foreign key (event_id) references events;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
