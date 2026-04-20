package com.tfassbender.loreweave.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Root configuration mapping for the {@code loreweave.*} property namespace.
 *
 * <p>Empty strings for string-valued properties (the defaults in
 * {@code application.properties}) are observable via {@link Optional#isEmpty()}
 * when the underlying value is blank; callers should treat blank and missing
 * values identically.
 */
@ConfigMapping(prefix = "loreweave")
public interface LoreWeaveConfig {

    Vault vault();

    Sync sync();

    Auth auth();

    Logging logging();

    interface Vault {
        /** Remote git URL of the Obsidian vault. Empty means "use local-path as-is". */
        Optional<String> remote();

        /** Where the vault is cloned / read from. Defaults to {@code ./vault} relative to the working dir. */
        @WithDefault("./vault")
        Path localPath();
    }

    interface Sync {
        /** Periodic pull interval. Defaults to 5 minutes. */
        @WithDefault("5M")
        Duration interval();
    }

    interface Auth {
        /** Bearer token. Empty means no token configured; all authed endpoints will reject. */
        Optional<String> token();
    }

    interface Logging {
        /**
         * Directory for rotating log files. Only read by Quarkus itself through
         * {@code ${loreweave.logging.path}} interpolation in {@code quarkus.log.file.path};
         * the binding lives here so SmallRye Config's strict validation is satisfied.
         */
        @WithDefault("./logs")
        Path path();
    }
}
