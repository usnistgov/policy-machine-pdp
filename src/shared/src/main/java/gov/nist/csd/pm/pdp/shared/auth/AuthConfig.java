package gov.nist.csd.pm.pdp.shared.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pm.pdp.auth")
public class AuthConfig {

    /**
     * Authentication mode. Options: "none" (default, plain gRPC headers) or "jwt".
     */
    private String mode = "none";

    /**
     * JWT claim whose string value identifies the actor by username (-> NameUserContext).
     * Configure this or userAttrsClaim (or both) when mode is "jwt".
     */
    private String usernameClaim = "username";

    /**
     * JWT claim whose string or list value identifies the actor by user-attribute names
     * (-> AttributeNamesUserContext). Used when no usernameClaim is found for the actor.
     */
    private String userAttrsClaim = "user_attrs";

    /**
     * JWKS endpoint used to fetch the public signing key(s) for JWT verification.
     * Required when mode is "jwt".
     */
    private String jwksUri;

    /**
     * Expected JWT issuer ("iss" claim). When set, tokens from any other issuer are rejected.
     */
    private String issuer;

    /**
     * Expected JWT audience ("aud" claim). When set, tokens without this audience are rejected.
     */
    private String audience;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getUsernameClaim() {
        return usernameClaim;
    }

    public void setUsernameClaim(String usernameClaim) {
        this.usernameClaim = usernameClaim;
    }

    public String getUserAttrsClaim() {
        return userAttrsClaim;
    }

    public void setUserAttrsClaim(String userAttrsClaim) {
        this.userAttrsClaim = userAttrsClaim;
    }
}
