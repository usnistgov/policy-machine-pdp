set resource operations ["read"]

create pc "pc1"
create oa "oa1" in ["pc1"]
create ua "ua1" in ["pc1"]
assign "u1" to ["ua1"]
create o "o1" in ["oa1"]

associate "ua1" and "oa1" with ["read"]