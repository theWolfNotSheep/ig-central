package co.uk.wolfnotsheep.platform.identity.config;

public final class IdentityPaths {

    private IdentityPaths() {}

    private static final String API    = "/api";
    private static final String AUTH   = API + "/auth";
    private static final String PUBLIC = API + "/public";
    private static final String ADMIN  = API + "/admin";
    private static final String USER   = API + "/user/me";

    // AUTH AND LOGIN — used in security config
    public static final String CFG_CSRF_TOKEN     = API + "/csrf-token";
    public static final String CFG_PUBLIC_AUTH    = AUTH + "/public/**";
    public static final String CFG_OAUTH2         = API + "/oauth2/**";

    // PROTECTED PATHS
    public static final String CFG_ADMIN_PATHS_ALL = ADMIN + "/**";

    // REGISTRATION & VERIFICATION
    public static final String VERIFY = USER + "/verify";
}
