package upload

import java.nio.file.Path

case class FileMeta(
  id: String,
  path: Path,
  dispositionType: String,
  contentType: Option[String],
  fileName: String,
  fileSize: Long
)
