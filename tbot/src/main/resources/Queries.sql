
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

select sum(1) from events e;  -- 3883

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

select current_timestamp + make_interval(mins => -5) ,  current_timestamp;

-- ТОЛЬКО ФУТБОЛ
-- 1. В первом запросе отбираем только нужные события по типу, фильтрам.
select e.*--distinct e.event_id--,e.competitionname ,e.team1 ,e.team2 , e.timerseconds/60 as mins
from   events e 
left join score s on e.id = s.events_id
where
  e.skid = 1 and -- только футбол
  (s.team1coeff is not null or s.team2coeff is not null) and
   -- события сохранены в течение последних 5-ти загрузок (нет старых)
    e.fba_load_id in (
     select fl.id 
     from   fba_load fl 
     where  fl.ins_datetime between current_timestamp + make_interval(mins => -5) and  current_timestamp
    ) and
   -- игра ещё не закончилась
    not exists( 
    select 1
    from   events ei
    where  ei.event_id = e.event_id and 
           ei.timerseconds/60 >= 90    
   );
  
  



--delete from fba.events; 
-- 1. В первом запросе отбираем только нужные события по типу, фильтрам.
select e.event_id,e.competitionname ,e.team1 ,e.team2 , max(e.timerseconds)/60 as durr_mins
from events e 
left join score s on e.id = s.events_id
where  
    e.skid = 1 and -- только футбол
  (s.team1coeff is not null or s.team2coeff is not null) and  
   -- уже прошло 70 минут игры.
 --  e.timerseconds/60 > 70 and ----------- todo: он нужен
   -- игра ещё не закончилась
    not exists( 
    select 1
    from   events ei
    where  ei.event_id = e.event_id and 
           ei.timerseconds/60 >= 90    
   )
   -- есть события в близком интервале
   and 
   exists( 
		   select *
			from events ei
			where ei.event_id = e.event_id and 
			      ei.ins_datetime between timeofday()::TIMESTAMP + make_interval(mins => -3) and 
			                              timeofday()::TIMESTAMP
         )
         and e.competitionname ='Лига Европы УЕФА. Групповая стадия'
   group by e.event_id,e.competitionname ,e.team1 ,e.team2
   order by durr_mins desc
 ;

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
     -- событие существует в последней загрузке 
  --and 
   (s.team1coeff is not null or s.team2coeff is not null) 
    -- хоть по одной команде не 0
   -- and (s.team1score != '0' or s.team2score != '0')  
   and e.event_id in (
   -----------------------------------------------------------------
		   select distinct e.event_id
			from events e 
			left join score s on e.id = s.events_id
			where 
			    e.skid = 1 and -- только футбол
			  (s.team1coeff is not null or s.team2coeff is not null) and  
			   -- уже прошло 70 минут игры.
			 --  e.timerseconds/60 > 70 and ----------- todo: он нужен
			   -- игра ещё не закончилась
			    not exists( 
			    select 1
			    from   events ei
			    where  ei.event_id = e.event_id and 
			           ei.timerseconds/60 >= 90    
			   )
			   -- есть события в близком интервале
			   and 
			   exists( 
					   select *
						from events ei
						where ei.event_id = e.event_id and 
						      ei.ins_datetime between timeofday()::TIMESTAMP + make_interval(mins => -3) and 
						                              timeofday()::TIMESTAMP
			         )
			         and e.competitionname ='Лига Европы УЕФА. Групповая стадия'
   -----------------------------------------------------------------
   )
   
   ) ds 
   where ds.rn=1
order by competitionname,eventname, ins_datetime desc;


--=================================================================================================================================================


 
  -- Итоговый запрос на рекомендации. 

 
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
		       --e.timerseconds/60 as DurrMin,
		       (case 
		         when coalesce(s.team1coeff,0) between 1.1 and 1.4 or
		              coalesce(s.draw_coeff,0) between 1.1 and 1.4 or
		              coalesce(s.team2coeff,0) between 1.1 and 1.4 
		         then 1
		         else null::integer
		        end) as is_time,
		        90 - e.timerseconds/60 as rest_mis,
		        row_number() over(partition by e.event_id order by e.timerseconds/60 desc) as rn
		 --e.*,s.* 
		from events e 
		left join score s on e.id = s.events_id
		where -- ещё не сформирован совет
		      e.event_id not in (select a.event_id from fba.advice a) and
		      e.event_id =36089115 and
		     (s.team1coeff is not null or s.team2coeff is not null) 	     
   

  
  select * from fba.v_football;
  
insert into fba.advice(event_id,advice_text) values(28532, 
'<b>Рекомендация № 1</b> 
<u>Футбол (Италия. Серия А)</u> 
До конца матча <b>7</b> минут.
<pre>           Эмполи  -    Рома
  Коэфф.     1.35  9.0  12.0
  Счет          3  :    0 </pre>
<b>Совет</b> поставить на <b>1.35</b>
(дата рекомендации 13.09.2022 01:51:12 Мск.)
');

'<b>Рекомендация № fba.advice.id</b>
<u>skname (competitionname)</u>
До конца матча <b>rest_mis</b> минут.
<pre>eventname
     Коэфф.    team1coeff  draw_coeff  team2coeff
     Счет      team1score   :   team2score </pre>
<b>Совет</b> поставить на <b>min(team1coeff,draw_coeff,team2coeff)</b>
(дата рекомендации 13.09.2022 01:51:12 Мск.)'


                    event_id: Long,
                    skname: String,
                    competitionname: String,
                    eventname: String,
                    team1coeff: Double,
                    draw_coeff: Double,
                    team2coeff: Double,
                    team1score: String,
                    team2score: String,
                    rest_mis: Int

