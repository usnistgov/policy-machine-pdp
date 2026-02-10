create pc "pc1"
create ua "ua1" in ["pc1"]
create oa "oa1" in ["pc1"]
create u "u1" in ["ua1"]
create o "o1" in ["oa1"]

set resource access rights ["read"]

associate "ua1" and "oa1" with ["read"]