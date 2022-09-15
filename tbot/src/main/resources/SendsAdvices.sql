drop table fba.advice_sent;
drop table fba.advice;

CREATE TABLE fba.advice (
	id serial NOT NULL,
	event_id int4 NOT NULL,
	advice_text varchar(128) NOT NULL,
	ins_datetime timestamp NULL DEFAULT timeofday()::timestamp without time zone,
	sent_datetime timestamp,
	CONSTRAINT advice_pkey PRIMARY KEY (id),
	CONSTRAINT fk_advice_even FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

insert into fba.advice(event_id,advice_text) values(28532,'Рекомендация сделать ставку на событие: Футбол, Игра - Серия А, Команда: Напол, коэффициент 1.35. Счет 3:0');

select * from fba.advice a


select a.id as advice_id,
       t.groupid,
       a.advice_text 
from   fba.tgroup t,
       fba.advice a
where  t.is_blck_by_user_dt is null and 
       a.sent_datetime is null and 
       (a.id,t.groupid) not in (
         select ast.advice_id,ast.groupid
           from fba.advice_sent ast
       )

       
CREATE TABLE fba.advice_sent(
	id serial NOT NULL,
	advice_id int4 not null constraint fk_advice_sent_adv references fba.advice(id) ON DELETE cascade,
	groupid   int4 not null constraint fk_advice_sent_grp references fba.tgroup(groupid) ON DELETE cascade,
	sent_datetime timestamp NULL DEFAULT timeofday()::timestamp,
	constraint uk_advice_sent unique (advice_id,groupid)
);

insert into fba.advice_sent(advice_id,groupid) values(1,533534191);

select * from fba.advice_sent

select * from fba.tgroup t 

delete from fba.advice_sent;
update fba.tgroup set is_blck_by_user_dt = null;
where groupid = 5513964598;

select * from fba.tgroup t 

delete from fba.advice;

--https://core.telegram.org/bots/api#formatting-options
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

SELECT DISTINCT ei.event_id
                   FROM events ei
                     LEFT JOIN score s_1 ON ei.id = s_1.events_id
                  WHERE ei.skid = 1 AND 
                  (s_1.team1coeff IS NOT NULL OR s_1.team2coeff IS NOT NULL) AND 
                  (
                  ei.fba_load_id IN ( SELECT fl.id
                           FROM fba_load fl
                          WHERE fl.ins_datetime >= (CURRENT_TIMESTAMP + make_interval(mins => '-15'::integer)) AND fl.ins_datetime <= CURRENT_TIMESTAMP)
                          ) 
                          AND NOT (
                          EXISTS ( 
                          SELECT 1
                           FROM events eii
                          WHERE eii.event_id = ei.event_id 
                          AND (eii.timerseconds / 60) >= 90
                          )) AND (ei.timerseconds / 60) >= 55;

select sum(1) from events e; -- 8684

select sum(1) from score  s; -- 8684
                         
select * from fba.v_football;

select * from fba.advice; -- 1 row

select sum(1) from fba.advice_sent -- 



alter table fba.advice_sent add user_was_active integer;

select * from fba.tgroup t 

delete from fba.advice;
delete from fba.advice;

select * from events e where e.id = 31201;

response statusText    = OK
response code          = 200

response statusText    = Service temporarily unavailable
response code          = 503

select replace('Совет № XXX покупать','XXX',1234::text);


drop TABLE fba.advice;

-- old
CREATE TABLE fba.advice (
	id serial NOT NULL,
	event_id int4 NOT NULL,
	advice_text varchar(1024) NOT NULL,
	ins_datetime timestamp NULL DEFAULT timeofday()::timestamp without time zone,
	sent_datetime timestamp NULL,
	advice_koef numeric NULL,
	advice_rest_mis int4 NULL,
	CONSTRAINT advice_pkey PRIMARY KEY (id)
);

alter table fba.advice drop CONSTRAINT ch_advice_type;
alter table fba.advice add CONSTRAINT ch_advice_type CHECK ((advice_type = ANY (ARRAY['team1'::text, 'team2'::text, 'draw'::text, 'unknown'::text])));

insert into fba.advice(
					    event_id, 
					    team1coeff,
					    draw_coeff,
					    team2coeff,
					    team1score,
					    team2score,
						advice_coeff,
						advice_rest_mis,
						advice_type
					  )
   select  ad.event_id,
           ad.team1coeff,
           ad.draw_coeff,
           ad.team2coeff,
           ad.team1score,
           ad.team2score,
           least(team1coeff,team2coeff,draw_coeff) as advice_coeff,
           ad.rest_mis,
           (case
             when least(team1coeff,team2coeff,draw_coeff) = team1coeff then 'team1'::text
             when least(team1coeff,team2coeff,draw_coeff) = team2coeff then 'team2'::text
             when least(team1coeff,team2coeff,draw_coeff) = draw_coeff then 'draw'::text
             else 'unknown'::text
            end) as advice_type
	 from  fba.v_advices ad
	  ON CONFLICT ON CONSTRAINT uk_advice_event_id DO NOTHING RETURNING id;



	
	

-- NEW
CREATE TABLE fba.advice (
	id serial NOT NULL,
    event_id        int4 NOT NULL,     
    team1coeff      numeric,
    draw_coeff      numeric,
    team2coeff      numeric,
    team1score      text,
    team2score      text,
    rest_mis        int,
    ins_datetime    timestamp NULL DEFAULT timeofday()::timestamp without time zone,
	sent_datetime   timestamp,
	advice_coeff    numeric,
	advice_rest_mis int4,
    constraint advice_pkey primary key (id),
    constraint uk_advice_event_id unique(event_id)
);

alter table fba.advice add advice_type text not null;


alter table fba.advice add constraint ch_advice_type check(advice_type in ('team1','team2','draw'));

    event_id
    skname
    competitionname
    eventname
    team1coeff
    draw_coeff
    team2coeff
    team1score
    team2score
    rest_mis

select * from fba.advice; -- 1 row


select adv.* from fba.v_advices adv


DROP TABLE fba.advice_sent;

CREATE TABLE fba.advice_sent (
	id serial NOT NULL,
	advice_id int8 NOT NULL,
	groupid int8 NOT NULL,
	sent_datetime timestamp NULL DEFAULT timeofday()::timestamp without time zone,
	user_was_active int4 NULL,
	CONSTRAINT uk_advice_sent UNIQUE (advice_id, groupid),
	CONSTRAINT fk_advice_sent_adv FOREIGN KEY (advice_id) REFERENCES advice(id) ON DELETE CASCADE,
	CONSTRAINT fk_advice_sent_grp FOREIGN KEY (groupid) REFERENCES tgroup(groupid) ON DELETE CASCADE
);


select to_char(current_timestamp + make_interval(mins => 120),'dd.mm.yyyy HH24:MI:SS')  

select npa.get_npa_last_change_date(14548)

SET lc_messages TO 'en_US.UTF-8';

select 1/0;

select *
  from fba.advice a
  order by a.id desc
                                
  alter table advice add advice_koef numeric;
  alter table advice add advice_rest_mis integer;
 
 advice_koef,advice_rest_mis
 
 select * from fba.tgroup t
 
 update fba.tgroup set active_to = to_date('01.01.2024','dd.mm.yyyy') where groupid = 322134338;
 
 
 alter table fba.tgroup drop active_to;
 alter table fba.tgroup add active_to date not null default current_date + make_interval(months => 1); 

 
select
	a.id as advice_id,
	t.groupid,
	replace(replace(a.advice_text, 'XXX', a.id::text), 'YYY', t.groupid::text) as advice_text,
	(case when current_timestamp between t.ins_datetime and t.active_to then 1 else 0 end) as is_act_user
from
	fba.tgroup t,
	fba.advice a
where
	t.is_blck_by_user_dt is null
	and a.sent_datetime is null
	and (a.id,
	t.groupid) not in (
	select
		ast.advice_id,
		ast.groupid
	from
		fba.advice_sent ast )
 
 
 
 
 
 
 
 
 


	