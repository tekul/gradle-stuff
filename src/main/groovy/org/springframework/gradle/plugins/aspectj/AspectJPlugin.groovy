package org.springframework.gradle.plugins.aspectj

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.TaskAction
import org.gradle.api.logging.LogLevel
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException

/**
 *
 * @author Luke Taylor
 */
class AspectJPlugin implements Plugin<Project> {

    void apply(Project project) {
        if (!project.hasProperty('aspectjVersion')) {
            throw new GradleException("You must set the property 'aspectjVersion' before applying the aspectj plugin")
        }

        if (project.configurations.findByName('ajtools') == null) {
            project.configurations.add('ajtools')
            project.dependencies {
                ajtools "org.aspectj:aspectjtools:${project.aspectjVersion}"
                compile "org.aspectj:aspectjrt:${project.aspectjVersion}"
            }
        }

        if (project.configurations.findByName('aspectpath') == null) {
            project.configurations.add('aspectpath')
        }

        project.tasks.add(name: 'compileJava', overwrite: true, description: 'Compiles AspectJ Source', type: Ajc) {
            dependsOn project.processResources
            sourceSet = project.sourceSets.main
            outputs.dir(sourceSet.classesDir)
            aspectPath = project.configurations.aspectpath
        }

        project.tasks.add(name: 'compileTestJava', overwrite: true, description: 'Compiles AspectJ Test Source', type: Ajc) {
            dependsOn project.processTestResources, project.compileJava, project.jar
            sourceSet = project.sourceSets.test
            outputs.dir(sourceSet.classesDir)
            aspectPath = project.files(project.configurations.aspectpath, project.jar.archivePath)
        }
    }
}

class Ajc extends DefaultTask {
    SourceSet sourceSet
    FileCollection aspectPath

    Ajc() {
        logging.captureStandardOutput(LogLevel.INFO)
    }

    @TaskAction
    def compile() {
        logger.info("Running ajc ...")
        ant.taskdef(resource: "org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties", classpath: project.configurations.ajtools.asPath)
        ant.iajc(classpath: sourceSet.compileClasspath.asPath, fork: 'true', destDir: sourceSet.classesDir.absolutePath,
                source: project.convention.plugins.java.sourceCompatibility,
                target: project.convention.plugins.java.targetCompatibility,
                aspectPath: aspectPath.asPath, sourceRootCopyFilter: '**/*.java', showWeaveInfo: 'true') {
            sourceroots {
                sourceSet.java.srcDirs.each {
                    pathelement(location: it.absolutePath)
                }
            }
        }
    }
}
