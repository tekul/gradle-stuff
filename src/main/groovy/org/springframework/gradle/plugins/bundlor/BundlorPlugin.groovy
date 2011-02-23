package org.springframework.gradle.plugins.bundlor

import com.springsource.bundlor.ClassPath
import com.springsource.bundlor.ManifestGenerator
import com.springsource.bundlor.ManifestWriter
import com.springsource.bundlor.blint.ManifestValidator
import com.springsource.bundlor.blint.support.DefaultManifestValidatorContributorsFactory
import com.springsource.bundlor.blint.support.StandardManifestValidator
import com.springsource.bundlor.support.DefaultManifestGeneratorContributorsFactory
import com.springsource.bundlor.support.StandardManifestGenerator
import com.springsource.bundlor.support.classpath.StandardClassPathFactory
import com.springsource.bundlor.support.manifestwriter.StandardManifestWriterFactory
import com.springsource.bundlor.support.properties.EmptyPropertiesSource
import com.springsource.bundlor.support.properties.FileSystemPropertiesSource
import com.springsource.bundlor.support.properties.PropertiesPropertiesSource
import com.springsource.bundlor.support.properties.PropertiesSource
import com.springsource.bundlor.util.BundleManifestUtils
import com.springsource.util.parser.manifest.ManifestContents
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * @author Luke Taylor
 */
class BundlorPlugin implements Plugin<Project> {
    void apply(Project project) {
        Task bundlor = project.tasks.add('bundlor', Bundlor.class)
        bundlor.setDescription('Generates OSGi manifest using bundlor tool')
        project.jar.dependsOn bundlor
    }
}

public class Bundlor extends DefaultTask {
    @InputFile
    File manifestTemplate

    @OutputDirectory
    File bundlorDir = new File("${project.buildDir}/bundlor")

    @OutputFile
    File manifest = project.file("${bundlorDir}/META-INF/MANIFEST.MF")

    @Input
    Map<String,String> expansions = [:]

    @InputFile
    @Optional
    File osgiProfile

    @Input
    boolean failOnWarnings = false

    Bundlor() {
        manifestTemplate = new File(project.projectDir, 'template.mf')
        inputs.files project.sourceSets.main.runtimeClasspath
        project.jar.manifest.from manifest
        project.jar.inputs.files manifest
    }

    @TaskAction
    void createManifest() {
        logging.captureStandardOutput(LogLevel.INFO)

        project.mkdir(bundlorDir)

        String inputPath = project.sourceSets.main.classesDir

        ClassPath inputClassPath = new StandardClassPathFactory().create(inputPath);
        ManifestWriter manifestWriter = new StandardManifestWriterFactory().create(inputPath, bundlorDir.absolutePath);
        ManifestContents mfTemplate = BundleManifestUtils.getManifest(manifestTemplate);

        // Must be a better way of doing this...
        Properties p = new Properties()
        expansions.each {entry ->
            p.setProperty(entry.key, entry.value as String)
        }

        PropertiesSource expansionProps = new PropertiesPropertiesSource(p)
        PropertiesSource osgiProfileProps = osgiProfile == null ? new EmptyPropertiesSource() :
            new FileSystemPropertiesSource(osgiProfile);

        ManifestGenerator manifestGenerator = new StandardManifestGenerator(
                DefaultManifestGeneratorContributorsFactory.create(expansionProps, osgiProfileProps));

        ManifestContents mf = manifestGenerator.generate(mfTemplate, inputClassPath);

        try {
            manifestWriter.write(mf);
        } finally {
            manifestWriter.close();
        }

        ManifestValidator manifestValidator = new StandardManifestValidator(DefaultManifestValidatorContributorsFactory.create());

        List<String> warnings = manifestValidator.validate(mf);

        if (warnings.isEmpty()) {
            return
        }

        logger.warn("Bundlor Warnings:");
        for (String warning : warnings) {
            logger.warn("    " + warning);
        }

        if (failOnWarnings) {
            throw new GradleException("Bundlor returned warnings. Please fix manifest template at " + manifestTemplate.absolutePath + " and try again.")
        }

    }
}
