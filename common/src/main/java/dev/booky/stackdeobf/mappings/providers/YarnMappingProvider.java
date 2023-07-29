package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;

public class YarnMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.yarn.repo-url", "https://maven.fabricmc.net");
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact", "net.fabricmc:yarn:v2"));

    public YarnMappingProvider(VersionData versionData) {
        super(versionData, "yarn", MAPPINGS_ARTIFACT, versionData.getWorldVersion() >= 2225
                // https://github.com/FabricMC/yarn/commit/426494576c3aa1e05deb2dadb90b3f0f1c7bc37b
                // this caused more hash sums to be published to the maven, starting at yarn 1.15 build 2
                ? VerifiableUrl.HashType.SHA512 : VerifiableUrl.HashType.SHA1);
    }
}
