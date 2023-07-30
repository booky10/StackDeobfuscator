package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.format.MappingFormat;

public class YarnMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.yarn.repo-url", "https://maven.fabricmc.net");

    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V1 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v1", "net.fabricmc:yarn"));
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT_V2 = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact.v2", "net.fabricmc:yarn:v2"));

    public YarnMappingProvider(VersionData versionData) {
        super(versionData, "yarn", getArtifact(versionData), getHashType(versionData));
    }

    private static boolean hasV2Mappings(VersionData versionData) {
        // first actual v2 version (with a checksum!) was at 1.14.4 build 15
        return versionData.getWorldVersion() >= 1976;
    }

    private static MavenArtifactInfo getArtifact(VersionData versionData) {
        return hasV2Mappings(versionData) ? MAPPINGS_ARTIFACT_V2 : MAPPINGS_ARTIFACT_V1;
    }

    private static VerifiableUrl.HashType getHashType(VersionData versionData) {
        // https://github.com/FabricMC/yarn/commit/426494576c3aa1e05deb2dadb90b3f0f1c7bc37b
        // this caused yarn to also publish sha256 and sha512 checksums, started at yarn 1.14.4 build 16
        return versionData.getWorldVersion() >= 1976
                ? VerifiableUrl.HashType.SHA512 : VerifiableUrl.HashType.SHA1;
    }

    @Override
    protected MappingFormat getMappingFormat() {
        return hasV2Mappings(this.versionData)
                ? MappingFormat.TINY_2 : MappingFormat.TINY;
    }
}
