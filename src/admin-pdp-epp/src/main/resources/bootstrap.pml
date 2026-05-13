set resource access rights [
    "call",
    "tools/list",
    "email:send", "email:send_to", "email:read", "email:forward", "email:get_inbox", "email:delete", "email:update",
    "report:create", "report:delete", "report:get", "report:update"
]

// svc2svc operation
@ReqCap({
    require ["call"] on [dst]
})
resourceop call_service(@Node string dst)

// query operations
resourceop query_ems_incidents(string code, string description, string reason_code, string reason_description, string incident_date, string patient_home_zip, string patient_dob, string patient_sex)
resourceop query_opioid_dispenses(string drug_name, string drug_code, string reason_code, string reason_description, string start_date, string stop_date, string zip3, string birth_year, string sex)

// Email resource operations
@ReqCap({
    require ["email:send"] on [from_address + "_inbox"]
    require ["email:send_to"] on [to_address + "_inbox"]
})
@EventCtx(string id, from_address, to_address, subject, body, attachments)
resourceop send_email(@Node string from_address, @Node string to_address, string subject, string body, []string attachments)
create obligation "send_email_ok"
when any user
performs "send_email"
do(ctx) {
    create O ctx.args.id in [ctx.args.to_address + "_inbox"]
}

@ReqCap({
    require ["email:get_inbox"] on [address + "_inbox"]
})
resourceop get_inbox(@Node string address, string sort, string order, bool unread_only)

@ReqCap({
    require ["email:read"] on [id]
})
resourceop get_email(string address, @Node string id)

@ReqCap({
    require ["email:delete"] on [id]
})
resourceop delete_email(string address, @Node string id)
create obligation "delete_email_ok"
when any user
performs "delete_email"
do(ctx) {
    delete node ctx.args.id
}

@ReqCap({
    require ["email:update"] on [id]
})
resourceop update_email(string address, @Node string id)

@ReqCap({
    require ["email:send"] on [from_address + "_inbox"]
    require ["email:send_to"] on [to_address + "_inbox"]
    require ["email:send"] on [original_email_id]
})
resourceop forward_email(@Node string from_address, @Node string to_address, @Node string original_email_id, string original_address, string body)

resourceop get_current_user_email()

// Report resource operations
@ReqCap({
    require ["report:create"] on ["low_reports"]
})
@EventCtx(string id, string cls, name, version, abstract, text)
resourceop create_report_low(string name, string version, string abstract, string text)
create obligation "create_report_low_ok"
when any user
performs "create_report_low"
do(ctx) {
    create O ctx.args.id in ["low_reports"]
}

@ReqCap({
    require ["report:create"] on ["med_reports"]
})
@EventCtx(string id, string cls, name, version, abstract, text)
resourceop create_report_med(string name, string version, string abstract, string text)
create obligation "create_report_med_ok"
when any user
performs "create_report_med"
do(ctx) {
    create O ctx.args.id in ["med_reports"]
}

@ReqCap({
    require ["report:create"] on ["high_reports"]
})
@EventCtx(string id, string cls, name, version, abstract, text)
resourceop create_report_high(string name, string version, string abstract, string text)
create obligation "create_report_high_ok"
when any user
performs "create_report_high"
do(ctx) {
    create O ctx.args.id in ["high_reports"]
}


resourceop list_reports()
resourceop get_lowest_classification()

@ReqCap({
    require ["report:get"] on [id]
})
resourceop get_report(@Node string id)

@ReqCap({
    require ["report:delete"] on [id]
})
resourceop delete_report(@Node string id)
create obligation "delete_report_ok"
when any user
performs "delete_report"
do(ctx) {
    delete node ctx.args.id
}

data_agent := "spiffe://cluster.local/ns/demo-ns/sa/data-agent"
data_agent_ua := "@spiffe://cluster.local/ns/demo-ns/sa/data-agent"
data_agent_high := "spiffe://cluster.local/ns/demo-ns/sa/data-agent-high"
data_agent_high_ua := "@spiffe://cluster.local/ns/demo-ns/sa/data-agent-high"
email_agent := "spiffe://cluster.local/ns/demo-ns/sa/email-agent"
email_agent_ua := "@spiffe://cluster.local/ns/demo-ns/sa/email-agent"
mcp_ua := "@spiffe://cluster.local/ns/demo-ns/sa/mcp"
mcp := "spiffe://cluster.local/ns/demo-ns/sa/mcp"
demo_db_ua := "@spiffe://cluster.local/ns/demo-ns/sa/demo-db"
demo_db := "spiffe://cluster.local/ns/demo-ns/sa/demo-db"
app_api_ua := "@spiffe://cluster.local/ns/demo-ns/sa/app-api"
app_api := "spiffe://cluster.local/ns/demo-ns/sa/app-api"
gw_ua := "@spiffe://cluster.local/ns/istio-system/sa/istio-ingressgateway-service-account"
gw := "spiffe://cluster.local/ns/istio-system/sa/istio-ingressgateway-service-account"

create pc "svc2svc"
create ua "svcs" in ["svc2svc"]
create ua data_agent_ua in ["svcs"]
create ua data_agent_high_ua in ["svcs"]
create ua email_agent_ua in ["svcs"]
create ua mcp_ua in ["svcs"]
create ua demo_db_ua in ["svcs"]
create ua app_api_ua in ["svcs"]
create ua gw_ua in ["svcs"]

// s2s assocaitions
// GW -> data-agent, data-agent-high, email-agent, app-api
associate gw_ua to data_agent_ua with ["call"]
associate gw_ua to data_agent_high_ua with ["call"]
associate gw_ua to email_agent_ua with ["call"]
associate gw_ua to app_api_ua with ["call"]

// data-agent -> mcp, email-agent
associate data_agent_ua to mcp_ua with ["call"]
associate data_agent_ua to email_agent_ua with ["call"]

// data-agent-high -> mcp, email-agent
associate data_agent_high_ua to mcp_ua with ["call"]
associate data_agent_high_ua to email_agent_ua with ["call"]

// email-agent -> mcp, data-agent
associate email_agent_ua to mcp_ua with ["call"]
associate email_agent_ua to data_agent_ua with ["call"]


// mcp -> app-api, demo-db
associate mcp_ua to app_api_ua with ["call"]
associate mcp_ua to demo_db_ua with ["call"]

