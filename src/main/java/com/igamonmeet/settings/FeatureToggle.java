package com.igamonmeet.settings;

public enum FeatureToggle {
    /** People can create their own account from the sign-in screen. */
    SELF_REGISTRATION,
    /** New accounts wait for Admin approval. Turning this off lets anyone who can reach the app in. */
    REGISTRATION_APPROVAL,
    /** "Forgot password?" sends a reset request to the Admin queue. */
    FORGOT_PASSWORD,
    /** Count of waiting items on the Admin queue tab. */
    QUEUE_BADGE,
    /** An agenda slide after the title slide, with links between slides (docs/spec-v3.md §5). */
    AGENDA_SLIDE
}
