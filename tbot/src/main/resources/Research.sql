

select 
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
