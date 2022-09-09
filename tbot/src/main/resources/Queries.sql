
select pid as process_id, 
       usename as username, 
       datname as database_name, 
       client_addr as client_address, 
       application_name,
       backend_start,
       state,
       state_change
from pg_stat_activity;


delete from fba_load;


select * from fba_load;

select sum(1) from events e;  -- 2508

select e.fba_load_id ,sum(1) as cnt 
from events e
group by e.fba_load_id
order by 1 

select sum(1) from score s; -- 2508

select s.events_id ,sum(1) 
from score s
group by s.events_id

--------------

select * from events e order by e.ins_datetime desc;

select * from score s ;


--delete from fba.events; 
-- 1. � ������ ������� �������� ������ ������ ������� �� ����, ��������.
select e.event_id,e.competitionname ,e.team1 ,e.team2 , max(e.timerseconds)/60 as durr_mins
from events e 
left join score s on e.id = s.events_id
where  
    e.skid = 1 and -- ������ ������
  (s.team1coeff is not null or s.team2coeff is not null) and  
   -- ��� ������ 70 ����� ����.
 --  e.timerseconds/60 > 70 and ----------- todo: �� �����
   -- ���� ��� �� �����������
    not exists( 
    select 1
    from   events ei
    where  ei.event_id = e.event_id and 
           ei.timerseconds/60 >= 90    
   )
   -- ���� ������� � ������� ���������
   and 
   exists( 
		   select *
			from events ei
			where ei.event_id = e.event_id and 
			      ei.ins_datetime between timeofday()::TIMESTAMP + make_interval(mins => -3) and 
			                              timeofday()::TIMESTAMP
         )
         and e.competitionname ='���� ������ ����. ��������� ������'
   group by e.event_id,e.competitionname ,e.team1 ,e.team2
   order by durr_mins desc
 ;


   
-- ��������� ���� �� ���������� ��������
select 
       e.event_id,  
       e.skname,
       e.competitionname,
       --e.timerseconds,
       e.eventname,
       ----null as "coefficient:", 
       s.team1coeff, 
       -- round((1/s.team1coeff)*100,1) as team1prcnt, 
       s.draw_coeff,
       s.team2coeff,
       ----null as "scores",
       s.team1score, 
       s.team2score,
       e.timerseconds/60 as DurrMin,
       (case 
         when coalesce(s.team1coeff,0) between 1.25 and 1.35 OR
              coalesce(s.draw_coeff,0) between 1.25 and 1.35 or
              coalesce(s.team2coeff,0) between 1.25 and 1.35 
         then 1
         else null::integer
        end) as is_time
 --e.*,s.* 
from events e 
left join score s on e.id = s.events_id
where e.event_id = 35910462 and
     -- ������� ���������� � ��������� �������� 
  --and 
   (s.team1coeff is not null or s.team2coeff is not null) 
    -- ���� �� ����� ������� �� 0
   -- and (s.team1score != '0' or s.team2score != '0')   
order by e.competitionname,e.eventname, e.ins_datetime desc;





select  ds.* 
 from (
select row_number() over(partition by e.event_id order by e.timerseconds desc) as rn,
       e.event_id,  
       e.skname,
       e.competitionname,
       --e.timerseconds,
       e.eventname,
       ----null as "coefficient:", 
       s.team1coeff, 
       -- round((1/s.team1coeff)*100,1) as team1prcnt, 
       s.draw_coeff,
       s.team2coeff,
       ----null as "scores",
       s.team1score, 
       s.team2score,
       e.timerseconds/60 as DurrMin,
       (case 
         when coalesce(s.team1coeff,0) between 1.25 and 1.35 OR
              coalesce(s.draw_coeff,0) between 1.25 and 1.35 or
              coalesce(s.team2coeff,0) between 1.25 and 1.35 
         then 1
         else null::integer
        end) as is_time,
        ins_datetime
 --e.*,s.* 
from events e 
left join score s on e.id = s.events_id
where --e.event_id = 35910462 and
     -- ������� ���������� � ��������� �������� 
  --and 
   (s.team1coeff is not null or s.team2coeff is not null) 
    -- ���� �� ����� ������� �� 0
   -- and (s.team1score != '0' or s.team2score != '0')  
   and e.event_id in (
   -----------------------------------------------------------------
		   select distinct e.event_id
			from events e 
			left join score s on e.id = s.events_id
			where 
			    e.skid = 1 and -- ������ ������
			  (s.team1coeff is not null or s.team2coeff is not null) and  
			   -- ��� ������ 70 ����� ����.
			 --  e.timerseconds/60 > 70 and ----------- todo: �� �����
			   -- ���� ��� �� �����������
			    not exists( 
			    select 1
			    from   events ei
			    where  ei.event_id = e.event_id and 
			           ei.timerseconds/60 >= 90    
			   )
			   -- ���� ������� � ������� ���������
			   and 
			   exists( 
					   select *
						from events ei
						where ei.event_id = e.event_id and 
						      ei.ins_datetime between timeofday()::TIMESTAMP + make_interval(mins => -3) and 
						                              timeofday()::TIMESTAMP
			         )
			         and e.competitionname ='���� ������ ����. ��������� ������'
   -----------------------------------------------------------------
   )
   
   ) ds 
   where ds.rn=1
order by competitionname,eventname, ins_datetime desc;







