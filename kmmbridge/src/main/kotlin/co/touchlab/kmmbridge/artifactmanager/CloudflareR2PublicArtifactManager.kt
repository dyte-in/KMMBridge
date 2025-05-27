package co.touchlab.kmmbridge.artifactmanager

import co.touchlab.kmmbridge.internal.kmmBridgeExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.util.UUID

class CloudflareR2PublicArtifactManager(
    accountId: String,
    private val bucketName: String,
    private val accessKeyId: Provider<String>,
    private val secretKey: Provider<String>,
    private val makeArtifactsPublic: Boolean,
    private val artifactPath: String?,
    private val customDomain: String = "",
) : ArtifactManager {

    private val baseUrl = "https://${accountId}.r2.cloudflarestorage.com"

    lateinit var frameworkName: String

    override fun configure(
        project: Project,
        version: String,
        uploadTask: TaskProvider<Task>,
        kmmPublishTask: TaskProvider<Task>
    ) {
        frameworkName = project.kmmBridgeExtension.frameworkName.get()
    }

    override fun deployArtifact(task: Task, zipFilePath: File, version: String): String {
        val fileName = obscureFileName(frameworkName, version)
        uploadArtifact(artifactPath, zipFilePath, fileName)
        return deployUrl(artifactPath, fileName)
    }

    private fun getArtifactPath(artifactPath: String?, zipFileName: String): String {
        return if (artifactPath.isNullOrEmpty()) {
            zipFileName
        } else {
            "$artifactPath/$zipFileName"
        }
    }

    /**
     * Compute the fully qualified URL for the artifact we just uploaded
     *
     * @see uploadArtifact
     */
    private fun deployUrl(artifactPath: String?, zipFileName: String): String {
        return "${customDomain.ifEmpty { baseUrl }}/${getArtifactPath(artifactPath, zipFileName)}"
    }

    /**
     * If the artifact doesn't already exist in remote storage, upload it.  Note: if there
     * is a problem determining if it exists it's assumed not to be there and will be
     * uploaded.
     */
    @Suppress("NAME_SHADOWING")
    private fun uploadArtifact(artifactPath: String?, zipFilePath: File, fileName: String) {
        val credentials = AwsBasicCredentials.create(accessKeyId.get(), secretKey.get())

        val serviceConfiguration = S3Configuration.builder().pathStyleAccessEnabled(true).build()

        val fileKey = getArtifactPath(artifactPath, fileName)

        val s3Client = S3Client.builder()
            .endpointOverride(URI.create(baseUrl))
            .serviceConfiguration(serviceConfiguration)
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()

        s3Client.use { s3Client ->

            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build()

            val exists = try {
                s3Client.headObject(headObjectRequest).sdkHttpResponse().isSuccessful
            } catch (e: Exception) {
                false
            }

            if (!exists) {
                val builder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)

                if (makeArtifactsPublic)
                    builder.acl("public-read")

                val putObjectRequest = builder.build()

                val requestBody = RequestBody.fromFile(zipFilePath)
                s3Client.putObject(putObjectRequest, requestBody)
            }
        }
    }
}

/**
 * Generate a file name that isn't guessable. Some artifact managers don't have auth guarding the urls.
 */
private fun obscureFileName(frameworkName: String, versionString: String): String {
    val randomId = UUID.randomUUID().toString()
    return "${frameworkName}-${versionString}-${randomId}.xcframework.zip"
}
