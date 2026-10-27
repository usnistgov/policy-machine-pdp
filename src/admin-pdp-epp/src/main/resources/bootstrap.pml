set resource access rights ["read", "write", "web"]

// read operations
@ReqCap({
    require ["read"] on [project]
})
resourceop read_project(@Node string project)

@ReqCap({
    require ["read"] on [project]
})
resourceop list_merge_requests(@Node string project, string state?)

@ReqCap({
    require ["read"] on [project]
})
resourceop read_merge_request(@Node string project, string iid)

@ReqCap({
    require ["read"] on [project]
})
resourceop read_file(@Node string project, string path, string ref?)

// write operations
@ReqCap({
    require ["write"] on [project]
})
resourceop create_comment(@Node string project, string iid, string body)

@ReqCap({
    require ["write"] on [project]
})
resourceop create_merge_request(@Node string project, string source_branch, string target_branch, string title, string description?)

@ReqCap({
    require ["write"] on [project]
})
resourceop merge_merge_request(@Node string project, string iid, bool squash?, string merge_commit_message?)

// web operations are not project-scoped; gated on the gitlab_projects OA
@ReqCap({
    require ["web"] on ["gitlab_projects"]
})
resourceop web_scrape(string url, []string formats?)

@ReqCap({
    require ["web"] on ["gitlab_projects"]
})
resourceop web_search(string search_query, int64 limit?)

create pc "gitlab"

create ua "@bob" in ["gitlab"]
create u "bob" in ["@bob"]

create oa "gitlab_projects" in ["gitlab"]
create o "jnr6/ai-devsecops-test" in ["gitlab_projects"]

associate "@bob" to "gitlab_projects" with ["read"]
