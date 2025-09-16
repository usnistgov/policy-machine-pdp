set resource operations ["call_service", "POST"]

create pc "users"
create ua "@super" in ["users"]
create ua "@bob" in ["users"]
create ua "@alice" in ["users"]

create ua "service_users" in ["users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/openwebui" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/publisher" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/db-public" in ["service_users"]
create ua "@spiffe://cluster.local/ns/nist-chat/sa/db-sensitive" in ["service_users"]

// bootstrap user
assign "super" to ["@super"]
create u "bob" in ["@bob"]
create u "alice" in ["@alice"]
create u "spiffe://cluster.local/ns/nist-chat/sa/openwebui" in ["@spiffe://cluster.local/ns/nist-chat/sa/openwebui"]
create u "spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public" in ["@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public"]
create u "spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive" in ["@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive"]
create u "spiffe://cluster.local/ns/nist-chat/sa/publisher" in ["@spiffe://cluster.local/ns/nist-chat/sa/publisher"]
create u "spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent" in ["@spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent"]
create u "spiffe://cluster.local/ns/nist-chat/sa/db-public" in ["@spiffe://cluster.local/ns/nist-chat/sa/db-public"]
create u "spiffe://cluster.local/ns/nist-chat/sa/db-sensitive" in ["@spiffe://cluster.local/ns/nist-chat/sa/db-sensitive"]

associate "@super" and PM_ADMIN_BASE_OA with ["*"]
associate "@super" and "service_users" with ["*"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/openwebui" and "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public" with ["call_service"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/openwebui" and "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive" with ["call_service"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/openwebui" and "@spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent" with ["call_service"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/openwebui" and "@spiffe://cluster.local/ns/nist-chat/sa/publisher" with ["call_service"]

associate "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive" and  "@spiffe://cluster.local/ns/nist-chat/sa/db-public" with ["call_service"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive" and  "@spiffe://cluster.local/ns/nist-chat/sa/db-sensitive" with ["call_service"]
associate "@spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public" and  "@spiffe://cluster.local/ns/nist-chat/sa/db-public" with ["call_service"]

associate "@spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent" and  "@spiffe://cluster.local/ns/nist-chat/sa/publisher" with ["call_service"]

create pc "API"
create oa "apis" in ["API"]
create o "spiffe://cluster.local/ns/nist-chat/sa/publisher/publish" in ["apis"]
create o "spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent/summarize" in ["apis"]
create o "spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public/query" in ["apis"]
create o "spiffe://cluster.local/ns/nist-chat/sa/sql-agent-sensitive/query" in ["apis"]

associate "@super" and "apis" with ["*"]
associate "@bob" and "spiffe://cluster.local/ns/nist-chat/sa/publisher/publish" with ["POST"]
associate "@bob" and "spiffe://cluster.local/ns/nist-chat/sa/summarizer-agent/summarize" with ["POST"]
associate "@bob" and "spiffe://cluster.local/ns/nist-chat/sa/sql-agent-public/query" with ["POST"]
associate "@alice" and "apis" with ["POST"]