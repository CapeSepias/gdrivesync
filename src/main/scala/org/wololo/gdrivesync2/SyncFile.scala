package org.wololo.gdrivesync2

import com.google.api.services.drive.model.File
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import scala.collection.mutable.ListBuffer
import com.google.api.services.drive.Drive
import com.typesafe.scalalogging.slf4j.LazyLogging

import Globals._
import SyncFile._

object SyncFile {
  def allChildren(children: ListBuffer[SyncFile]) : ListBuffer[SyncFile] = {
	if (children.size > 0) {
	  children ++ allChildren(children.flatMap(child => child.children))
	} else {
	  children
	}
  }
}

class SyncFile(var path: String, val driveFile: File) extends LazyLogging {
  val localFile = new java.io.File(SYNC_STORE_DIR.getPath(), path)

  def localMD5 = DigestUtils.md5Hex(new FileInputStream(localFile))
  def isIdentical = localMD5 == driveFile.getMd5Checksum()
  def isRemoteFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"

  val wasSynced = false
  
  val children = ListBuffer[SyncFile]()
  
  def sync = {
    logger.debug("All children: " + allChildren(children).size)
    allChildren(children).filter(_.isRemoteFolder).foreach(_.createLocalFolder)
  }
  
  def createLocalFolder = {
    logger.debug("Creating local folder at: " + localFile.getPath())
    localFile.mkdir()
  }
}