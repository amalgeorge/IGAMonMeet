package com.igamonmeet.audit;

/** Test bridge to the package-private CSV helper. */
public final class AuditControllerAccess {
    private AuditControllerAccess() {
    }

    public static String cell(String v) {
        return AuditController.cell(v);
    }
}
