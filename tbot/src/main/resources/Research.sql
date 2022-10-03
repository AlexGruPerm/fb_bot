

select to_char(a.ins_datetime,'dd.mm.yyyy'),
       a.ins_datetime
from advice a 

--create or replace view 
select 
       to_char(a.ins_datetime,'dd.mm.yyyy') as dt,
       min(a.ins_datetime)           as min_date,
       max(a.ins_datetime)           as max_date,
       sum(1)                        as adv_cnt,
       round(avg(a.advice_rest_mis)) as avg_rest_mis,
       round(avg(a.advice_coeff),2)  as avg_adv_coef,
       min(a.advice_rest_mis)        as min_rest_mis,
       max(a.advice_rest_mis)        as max_rest_mis,
       min(a.advice_coeff)           as min_coef, 
       max(a.advice_coeff)           as max_coef
from advice a 
group by to_char(a.ins_datetime,'dd.mm.yyyy')


select coef, 
       (round((1/coef)*100)) as cnt_wins,
       (100 - round((1/coef)*100)) as cnt_loss,
       (round((1/coef)*100))*50*(coef-1) as win_val,
       (100 - round((1/coef)*100))*50 as los_val,
       (round((1/coef)*100))*50*(coef-1) - (100 - round((1/coef)*100))*50 as profit 
 from
(select 1.20+generate_series*0.01 as coef from generate_series(1,20)) ds

select * from advice a order by id desc

update advice set advice_strategy = 'alfa';

select * from advice_sent as2 order by id desc;

select * from fba.v_advice_group

