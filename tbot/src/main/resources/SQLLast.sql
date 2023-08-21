
delete from fba.fba_load;
delete from fba.advice;
delete from fba.events;
delete from fba.tgroup;

select sum(1) from fba.fba_load;--1271

select * from fba.fba_load;
select * from fba.advice a order by a.id  desc;
select * from fba.events e order by e.id desc;
select * from fba.tgroup t;

