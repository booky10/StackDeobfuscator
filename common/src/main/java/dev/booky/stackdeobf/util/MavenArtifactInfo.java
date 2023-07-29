package dev.booky.stackdeobf.util;
// Created by booky10 in StackDeobfuscator (20:21 30.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MavenArtifactInfo {

    private final String repoUrl;
    private final String groupId;
    private final String artifactId;
    private final String classifier;

    public MavenArtifactInfo(String repoUrl, String groupId, String artifactId, @Nullable String classifier) {
        this.repoUrl = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
    }

    public static MavenArtifactInfo parse(String repoUrl, String info) {
        String[] split = StringUtils.split(info, ':');
        if (split.length != 2 && split.length != 3) {
            throw new IllegalArgumentException("Artifact info is invalid: " + info);
        }

        String groupId = split[0], artifactId = split[1];
        String classifier = split.length > 2 ? split[2] : null;

        return new MavenArtifactInfo(repoUrl, groupId, artifactId, classifier);
    }

    public CompletableFuture<VerifiableUrl> buildVerifiableMetaUrl(VerifiableUrl.HashType hashType, Executor executor) {
        return VerifiableUrl.resolve(this.buildMetaUrl(), hashType, executor);
    }

    public URI buildMetaUrl() {
        return URI.create(this.repoUrl + this.groupId.replace('.', '/') +
                "/" + this.artifactId + "/maven-metadata.xml");
    }

    public CompletableFuture<VerifiableUrl> buildVerifiableUrl(String version, String extension,
                                                               VerifiableUrl.HashType hashType, Executor executor) {
        return VerifiableUrl.resolve(this.buildUrl(version, extension), hashType, executor);
    }

    public URI buildUrl(String version, String extension) {
        String fileName = this.artifactId + "-" + version +
                (this.classifier != null ? "-" + this.classifier : "") + "." + extension;
        return URI.create(this.repoUrl + this.groupId.replace('.', '/') +
                "/" + this.artifactId + "/" + version + "/" + fileName);
    }

    public String getRepoUrl() {
        return this.repoUrl;
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public @Nullable String getClassifier() {
        return this.classifier;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MavenArtifactInfo that)) return false;
        if (!this.repoUrl.equals(that.repoUrl)) return false;
        if (!this.groupId.equals(that.groupId)) return false;
        if (!this.artifactId.equals(that.artifactId)) return false;
        return Objects.equals(this.classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        int result = this.repoUrl.hashCode();
        result = 31 * result + this.groupId.hashCode();
        result = 31 * result + this.artifactId.hashCode();
        result = 31 * result + (this.classifier != null ? this.classifier.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MavenArtifactInfo{repoUrl='" + this.repoUrl + '\'' + ", groupId='" + this.groupId + '\'' + ", artifactId='" + this.artifactId + '\'' + ", classifier='" + this.classifier + '\'' + '}';
    }
}
