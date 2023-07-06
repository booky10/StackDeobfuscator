package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import dev.booky.stackdeobf.util.MavenArtifactInfo;

public class YarnMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.yarn.repo-url", "https://maven.fabricmc.net");
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.yarn.mappings-artifact", "net.fabricmc:yarn:v2"));

    public YarnMappingProvider() {
        super("yarn", MAPPINGS_ARTIFACT);
    }
}
