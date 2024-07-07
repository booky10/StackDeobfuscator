package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.format.MappingFormat;

import java.util.EnumSet;
import java.util.Set;

import static dev.booky.stackdeobf.util.VersionConstants.V19W02A;
import static dev.booky.stackdeobf.util.VersionConstants.V19W42A;
import static dev.booky.stackdeobf.util.VersionConstants.V1_14_4;
import static dev.booky.stackdeobf.util.VersionConstants.V1_15;
import static dev.booky.stackdeobf.util.VersionConstants.V1_15_COMBAT_1;

public class YarnMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.yarn.repo-url", "https://maven.fabricmc.net");

    // default to false as fabric's maven repository sometimes
    // returns invalid checksums after an update for maven-metadata.xml files;
    // this should work around https://github.com/booky10/StackDeobfuscator/issues/10
    private static final boolean VERIFY_YARN_METADATA_CHECKSUMS =
            Boolean.getBoolean("stackdeobf.yarn.verify-metadata-checksums");

    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V1 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v1", "net.fabricmc:yarn"));
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V2 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v2", "net.fabricmc:yarn:v2"));

    public YarnMappingProvider(VersionData versionData) {
        super(versionData, "yarn", getArtifact(versionData), getHashType(versionData));
    }

    private static Set<VersionFlag> getVersionFlags(VersionData versionData) {
        if (versionData.getWorldVersion() == V19W02A) {
            // I don't understand this...
            return EnumSet.allOf(VersionFlag.class);
        }

        // I gave up writing comments explaining this mess... please don't look at the file history
        Set<VersionFlag> flags = EnumSet.noneOf(VersionFlag.class);
        if (versionData.getWorldVersion() < V1_15
                && versionData.getWorldVersion() != V1_14_4) {
            flags.add(VersionFlag.NO_SHA256);

            if (versionData.getWorldVersion() <= V19W42A) {
                flags.add(VersionFlag.NO_V2);
            }
        }

        if (versionData.getWorldVersion() == V1_15_COMBAT_1) {
            flags.add(VersionFlag.NO_SHA256);
        }

        return flags;
    }

    private static MavenArtifactInfo getArtifact(VersionData versionData) {
        return getVersionFlags(versionData).contains(VersionFlag.NO_V2)
                ? MAPPINGS_ARTIFACT_V1 : MAPPINGS_ARTIFACT_V2;
    }

    private static VerifiableUrl.HashType getHashType(VersionData versionData) {
        return getVersionFlags(versionData).contains(VersionFlag.NO_SHA256)
                ? VerifiableUrl.HashType.SHA1 : VerifiableUrl.HashType.SHA256;
    }

    @Override
    public VerifiableUrl.HashType getMetaHashType() {
        if (!VERIFY_YARN_METADATA_CHECKSUMS) {
            return VerifiableUrl.HashType.DUMMY;
        }
        return super.getMetaHashType();
    }

    @Override
    protected MappingFormat getMappingFormat() {
        return getVersionFlags(this.versionData).contains(VersionFlag.NO_V2)
                ? MappingFormat.TINY_FILE : MappingFormat.TINY_2_FILE;
    }

    private enum VersionFlag {
        NO_V2, // or just no checksums for it
        NO_SHA256,
    }
}
