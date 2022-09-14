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


select * from fba.v_football;

select * from fba.advice;
delete from fba.advice;

delete from fba.advice;

select * from events e where e.id = 31201;


	