import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils
import sbt.MavenRepository

object Dependencies {

  def detectIntellijArtifactVersionAndRepository(intellijVersion: String): (String, MavenRepository) = {
    val locationDescriptor = IntellijVersionUtils.detectArtifactLocation(BuildInfo(intellijVersion, IdeaCommunity), ".zip")
    val artifactVersion = locationDescriptor.artifactVersion
    val artifactUrl = locationDescriptor.url
    //println(s"""[detectIntellijArtifactVersionAndRepository] build number: $intellijVersion, artifact version: $artifactVersion, artifact url: $artifactUrl""")
    (artifactVersion, locationDescriptor.repository)
  }
}
