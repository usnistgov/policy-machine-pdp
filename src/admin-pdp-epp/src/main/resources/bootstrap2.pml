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


create pc "users"
create ua "keycloak_users" in ["users"]
create ua "@bob@example.com" in ["keycloak_users"]
create ua "@alice@example.com" in ["keycloak_users"]
create ua "@charlie@example.com" in ["keycloak_users"]

create oa "inboxes" in ["users"]
create oa "bob@example.com_inbox" in ["inboxes"]
create oa "alice@example.com_inbox" in ["inboxes"]
create oa "charlie@example.com_inbox" in ["inboxes"]

associate "keycloak_users" to "inboxes" with ["email:send_to"]
associate "@bob@example.com" to "bob@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]
associate "@alice@example.com" to "alice@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]
associate "@charlie@example.com" to "charlie@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]
// allow email agent to manage user emails
associate email_agent_ua to "bob@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]
associate email_agent_ua to "alice@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]
associate email_agent_ua to "charlie@example.com_inbox" with ["email:read", "email:get_inbox", "email:send", "email:delete"]


create pc "MCP"
create oa "mcp_tools" in ["MCP"]
create oa "data_tools" in ["MCP"]
create oa "email_tools" in ["MCP"]

// email tools
create O "send_email" in ["mcp_tools", "email_tools"]
create O "get_inbox" in ["mcp_tools", "email_tools"]
create O "get_email" in ["mcp_tools", "email_tools"]
create O "delete_email" in ["mcp_tools", "email_tools"]
create O "update_email" in ["mcp_tools", "email_tools"]
create O "forward_email" in ["mcp_tools", "email_tools"]
create O "get_current_user_email" in ["mcp_tools", "email_tools"]

// report tools
create O "query_ems_incidents" in ["mcp_tools", "data_tools"]
create O "query_opioid_dispenses" in ["mcp_tools", "data_tools"]
create O "create_report_low" in ["mcp_tools", "data_tools"]
create O "create_report_med" in ["mcp_tools", "data_tools"]
create O "create_report_high" in ["mcp_tools", "data_tools"]
create O "get_report" in ["mcp_tools", "data_tools"]
create O "list_reports" in ["mcp_tools", "data_tools"]
create O "delete_report" in ["mcp_tools", "data_tools"]

associate "@spiffe://cluster.local/ns/demo-ns/sa/data-agent" to "data_tools" with ["tools/list"]
associate "@spiffe://cluster.local/ns/demo-ns/sa/data-agent-high" to "data_tools" with ["tools/list"]
associate "@spiffe://cluster.local/ns/demo-ns/sa/email-agent" to "email_tools" with ["tools/list"]

create PC "classification"
create OA "reports" in ["classification"]
create OA "high_reports" in ["reports"]
create OA "med_reports" in ["high_reports"]
create OA "low_reports" in ["med_reports"]

create UA "low_cls" in ["classification"]
create UA "med_cls" in ["low_cls"]
create UA "high_cls" in ["med_cls"]

// allow gw to call agents in classification pc
associate gw_ua to "low_cls" with ["call"]
associate "low_cls" to "low_reports" with ["report:create", "report:delete", "report:get", "report:update"]
associate "med_cls" to "med_reports" with ["report:create", "report:delete", "report:get", "report:update"]
associate "high_cls" to "high_reports" with ["report:create", "report:delete", "report:get", "report:update"]

// users
create u "bob@example.com" in ["@bob@example.com", "high_cls"]
create u "alice@example.com" in ["@alice@example.com", "med_cls"]
create u "charlie@example.com" in ["@charlie@example.com", "low_cls"]
create u data_agent in [data_agent_ua, "med_cls"]
create u data_agent_high in [data_agent_high_ua, "high_cls"]
create u email_agent in [email_agent_ua]
create u mcp in [mcp_ua]
create u demo_db in [demo_db_ua]
create u app_api in [app_api_ua]
create u gw in [gw_ua]

// obligations
create obligation "raise_ems_then_opioid"
when any user
performs "query_ems_incidents"
do(ctx) {
    // deny process from creating "low" report
    name := "deny_" + ctx.user + "," + ctx.process + "_create:report_on_low"
    delete if exists prohibition name
    create disj process prohibition name
        deny ctx.user process ctx.process
        arset ["report:create"]
        include ["low_reports"]

    name = "raise_cls_on_query_opioid_dispenses_for_" + ctx.user + "," + ctx.process
    delete if exists obligation name
    create obligation name
    when process ctx.process
    performs "query_opioid_dispenses"
    do(ctx1) {
        // deny the process from creating reports in med
        pro_name := "deny_" + ctx1.user + "," + ctx1.process + "_create:report_on_med"
        delete if exists prohibition pro_name
        create disj process prohibition pro_name
        deny ctx1.user process ctx1.process
        arset ["report:create"]
        include ["med_reports"]
    }
}


create obligation "raise_opioid_then_ems"
when any user
performs "query_opioid_dispenses"
do(ctx) {
    // deny process from creating "low" report
    name := "deny_" + ctx.user + "," + ctx.process + "_create:report_on_low"
    delete if exists prohibition name
    create disj process prohibition name
        deny ctx.user process ctx.process
        arset ["report:create"]
        include ["low_reports"]

    name = "raise_cls_on_query_ems_incidents_for_" + ctx.user + "," + ctx.process
    delete if exists obligation name
    create obligation name
    when process ctx.process
    performs "query_opioid_dispenses"
    do(ctx1) {
        // deny the process from creating reports in med
        pro_name := "deny_" + ctx1.user + "," + ctx1.process + "_create:report_on_med"
        delete if exists prohibition pro_name
        create disj process prohibition pro_name
        deny ctx1.user process ctx1.process
        arset ["report:create"]
        include ["med_reports"]
    }
}

// grant @super * on all adjacent ascendats of policy classes
associate "@super" to "svcs" with ["*"]
associate "@super" to "keycloak_users" with ["*"]
associate "@super" to "inboxes" with ["*"]
associate "@super" to "mcp_tools" with ["*"]
associate "@super" to "data_tools" with ["*"]
associate "@super" to "email_tools" with ["*"]
associate "@super" to "reports" with ["*"]
associate "@super" to "low_cls" with ["*"]