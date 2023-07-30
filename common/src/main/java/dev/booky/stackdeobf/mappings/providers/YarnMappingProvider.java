package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.format.MappingFormat;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.Set;

import static dev.booky.stackdeobf.util.VersionConstants.V19W34A;
import static dev.booky.stackdeobf.util.VersionConstants.V19W44A;
import static dev.booky.stackdeobf.util.VersionConstants.V19W45A;
import static dev.booky.stackdeobf.util.VersionConstants.V1_14_4;
import static dev.booky.stackdeobf.util.VersionConstants.V20W06A;

public class YarnMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.yarn.repo-url", "https://maven.fabricmc.net");

    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V1 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v1", "net.fabricmc:yarn"));
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V2 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v2", "net.fabricmc:yarn:v2"));

    public YarnMappingProvider(VersionData versionData) {
        super(versionData, "yarn", getArtifact(versionData), getHashType(versionData));
    }

    private static Set<QuirkFlag> getVersionQuirks(VersionData versionData) {
        Set<QuirkFlag> flags = EnumSet.noneOf(QuirkFlag.class);
        if (versionData.getWorldVersion() < V1_14_4) {
            // first actual tiny v2 build (with a checksum!) was at 1.14.4 build 15
            flags.add(QuirkFlag.NO_V2);
            // https://github.com/FabricMC/yarn/commit/426494576c3aa1e05deb2dadb90b3f0f1c7bc37b
            // this caused yarn to also publish sha256 and sha512 checksums, starting with yarn 1.14.4 build 16
            flags.add(QuirkFlag.NO_SHA512);
        } else // combat tests have a weird naming schema and don't have tiny-v2 mappings...
            if (StringUtils.containsIgnoreCase(versionData.getId(), "combat")
                    // I don't know why, but these versions also don't have v2 or
                    // sha512 checksums (from 19w34a build 1 till 19w44a build 8)
                    || (versionData.getWorldVersion() >= V19W34A && versionData.getWorldVersion() <= V19W44A)) {
                flags.add(QuirkFlag.NO_V2);
                flags.add(QuirkFlag.NO_SHA512);
            } else {
                // starting with 1.14_combat-3, v2 mappings with checksums appeared again;
                // but not with sha512, only with spigot guy + sha1
                //
                // they spawn in again starting with 20w06a build 1
                if (versionData.getWorldVersion() >= V19W45A && versionData.getWorldVersion() <= V20W06A) {
                    flags.add(QuirkFlag.NO_SHA512);
                }
            }
        return flags;
    }

    private static MavenArtifactInfo getArtifact(VersionData versionData) {
        return getVersionQuirks(versionData).contains(QuirkFlag.NO_V2)
                ? MAPPINGS_ARTIFACT_V1 : MAPPINGS_ARTIFACT_V2;
    }

    private static VerifiableUrl.HashType getHashType(VersionData versionData) {
        return getVersionQuirks(versionData).contains(QuirkFlag.NO_SHA512)
                ? VerifiableUrl.HashType.SHA1 : VerifiableUrl.HashType.SHA512;
    }

    @Override
    protected MappingFormat getMappingFormat() {
        return getVersionQuirks(this.versionData).contains(QuirkFlag.NO_V2)
                ? MappingFormat.TINY : MappingFormat.TINY_2;
    }

    private enum QuirkFlag {
        NO_V2, // or just no checksums for it
        NO_SHA512,
    }
}
