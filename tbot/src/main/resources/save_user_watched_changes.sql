CREATE OR REPLACE PROCEDURE npa_src_data.save_user_watched_changes(
																	p_user_id     in integer,
																	c_zlog_id     in integer[],
																	c_change_code in text[]
																   )
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $procedure$
declare
  l_debug        integer  := 0;
  l_errm         text     := 'save_user_watched_changes ';
  ds record;
  l_message_text text;
  l_err_code     text;
  l_excp_context text;
begin	
   l_debug := 100;
   insert into npa.ref_user_npa_changes(zlog_id,change_code,user_id) 
   select  zlog_id,
   	       change_code,
	       p_user_id as user_id
	 from (
	 select unnest(c_zlog_id)     as zlog_id,
	        unnest(c_change_code) as change_code
	 ) ds
   ON CONFLICT ON CONSTRAINT idx_ref_user_npa_changes 
   DO NOTHING;
exception
when others then 
    GET STACKED DIAGNOSTICS
      l_message_text = MESSAGE_TEXT,
      l_err_code     = RETURNED_SQLSTATE,
      l_excp_context := PG_EXCEPTION_CONTEXT;  
    raise notice    'Exception - ERROR CODE: % MESSAGE TEXT: % CONTEXT: %', l_err_code, l_message_text, l_excp_context;
    raise exception 'Exception - ERROR CODE: % MESSAGE TEXT: % CONTEXT: %', l_err_code, l_message_text, l_excp_context;
 end;
$procedure$
;