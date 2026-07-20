-- Per-user preference overrides. Only deviations from defaults are stored:
-- a missing row means "use the default declared in the UserPreference enum".
-- pref_value is deliberately VARCHAR, not BOOLEAN: type-agnostic for future
-- prefs (cadence, font size...) and named pref_value because VALUE is a
-- reserved keyword in H2 (the test database).
CREATE TABLE user_preferences (
    user_id    INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pref_key   VARCHAR(50) NOT NULL,
    pref_value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, pref_key)
);
