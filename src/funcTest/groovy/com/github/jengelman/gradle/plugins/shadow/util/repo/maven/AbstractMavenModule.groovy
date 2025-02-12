package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import com.github.jengelman.gradle.plugins.shadow.util.repo.AbstractModule
import groovy.xml.MarkupBuilder
import groovy.xml.XmlParser

import java.text.SimpleDateFormat

abstract class AbstractMavenModule extends AbstractModule implements MavenModule {
    protected static final String MAVEN_METADATA_FILE = "maven-metadata.xml"
    final File moduleDir
    final String groupId
    final String artifactId
    final String version
    String parentPomSection
    String type = 'jar'
    String packaging
    int publishCount = 1
    private final List dependencies = []
    private final List artifacts = []
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")

    AbstractMavenModule(File moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    abstract boolean getUniqueSnapshots()

    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${getUniqueSnapshotVersion()}"
        }
        return version
    }

    private String getUniqueSnapshotVersion() {
        assert uniqueSnapshots && version.endsWith('-SNAPSHOT')
        if (metaDataFile.isFile()) {
            def metaData = new XmlParser().parse(metaDataFile)
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            return "${timestamp}-${build}"
        }
        return "${timestampFormat.format(publishTimestamp)}-${publishCount}"
    }

    MavenModule dependsOn(String... dependencyArtifactIds) {
        for (String id : dependencyArtifactIds) {
            dependsOn(groupId, id, '1.0')
        }
        return this
    }

    @Override
    MavenModule dependsOn(String group, String artifactId, String version) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version, type: type]
        return this
    }

    String getPackaging() {
        return packaging
    }

    List getDependencies() {
        return dependencies
    }

    @Override
    File getPomFile() {
        return moduleDir.resolve("$artifactId-${publishArtifactVersion}.pom")
    }

    @Override
    File getMetaDataFile() {
        moduleDir.resolve(MAVEN_METADATA_FILE)
    }

    File getRootMetaDataFile() {
        moduleDir.parentFile.resolve(MAVEN_METADATA_FILE)
    }

    File artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return moduleDir.resolve(fileName)
    }

    protected Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    @Override
    MavenModule publishPom() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()

        updateRootMavenMetaData(rootMavenMetaData)

        if (publishesMetaDataFile()) {
            publish(metaDataFile) { Writer writer ->
                writer << getMetaDataFileContent()
            }
        }

        publish(pomFile) { Writer writer ->
            def pomPackaging = packaging ?: type
            writer << """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <!-- ${getArtifactContent()} -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>$groupId</groupId>
              <artifactId>$artifactId</artifactId>
              <packaging>$pomPackaging</packaging>
              <version>$version</version>
              <description>Published on $publishTimestamp</description>
            """.stripIndent()

            if (parentPomSection) {
                writer << "\n$parentPomSection\n"
            }

            if (!dependencies.empty) {
                writer << "<dependencies>"
            }

            dependencies.each { dependency ->
                def typeAttribute = dependency['type'] == null ? "" : "<type>$dependency.type</type>"
                writer << """
                <dependency>
                  <groupId>$dependency.groupId</groupId>
                  <artifactId>$dependency.artifactId</artifactId>
                  <version>$dependency.version</version>
                  $typeAttribute
                </dependency>""".stripIndent()
            }

            if (!dependencies.empty) {
                writer << "</dependencies>"
            }

            writer << "\n</project>"
        }
        return this
    }

    private void updateRootMavenMetaData(File rootMavenMetaData) {
        def allVersions = rootMavenMetaData.exists() ? new XmlParser().parseText(rootMavenMetaData.text).versioning.versions.version*.value().flatten() : []
        allVersions << version
        publish(rootMavenMetaData) { Writer writer ->
            def builder = new MarkupBuilder(writer)
            builder.metadata {
                groupId(groupId)
                artifactId(artifactId)
                version(allVersions.max())
                versioning {
                    if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
                        snapshot {
                            timestamp(timestampFormat.format(publishTimestamp))
                            buildNumber(publishCount)
                            lastUpdated(updateFormat.format(publishTimestamp))
                        }
                    } else {
                        versions {
                            allVersions.each { currVersion ->
                                version(currVersion)
                            }
                        }
                    }
                }
            }
        }
    }

    abstract String getMetaDataFileContent()

    @Override
    MavenModule publish() {

        publishPom()
        artifacts.each { artifact ->
            publishArtifact(artifact as Map<String, ?>)
        }
        publishArtifact([:])
        return this
    }

    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publish(artifactFile) { Writer writer ->
            writer << "${artifactFile.name} : $artifactContent"
        }
        return artifactFile
    }

    protected String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    protected abstract boolean publishesMetaDataFile()
}
