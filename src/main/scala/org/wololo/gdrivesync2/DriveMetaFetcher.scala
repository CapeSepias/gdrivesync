package org.wololo.gdrivesync2

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.ListBuffer

import com.google.api.client.auth.oauth2.Credential
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging

import Globals.JSON_FACTORY
import Globals.SYNC_STORE_DIR
import Globals.httpTransport

class DriveMetaFetcher(implicit drive: Drive) extends LazyLogging {
  
  def fetchRoot = {
    val driveRootId = drive.about.get.execute.getRootFolderId
    logger.info("Found Google Drive root with id " + driveRootId)
    val driveRoot = new File
    driveRoot.setId(driveRootId)
    var root = new SyncFile(new java.io.File(SYNC_STORE_DIR.getPath(), ""), driveRoot)
    fetchChildren(root)
  }

  def fetchChildren(root: SyncFile) = {
    val request = drive.files.list
    request.setMaxResults(200)
    val result = ListBuffer[File]()
    do {
      val files = request.execute
      val items = files.getItems
      logger.info("Fetched " + items.length + " items from Google Drive API")
      result ++= items
      request.setPageToken(files.getNextPageToken)
    } while (request.getPageToken != null && request.getPageToken.length > 0)

    logger.info("Fetched " + result.size + " from Google Drive API")

    var notOwned = result filter { _.getOwners.toList exists { !_.getIsAuthenticatedUser } }
    logger.info("Found " + notOwned.size + " items not owned by you")
    logger.info("Ignoring items not owned by you")
    result --= notOwned

    var trashed = result filter { _.getExplicitlyTrashed() != null }
    logger.info("Found " + trashed.size + " trashed items")
    logger.info("Ignoring trashed items")
    result --= trashed

    var multipleParents = result filter { _.getParents.length > 1 }
    logger.info("Found " + multipleParents.size + " items with multiple parents")
    logger.info("Ignoring items with multiple parents")
    result --= multipleParents

    var noParents = result filter { _.getParents.length == 0 }
    logger.info(noParents.size + " items with no parents")
    logger.info("Ignoring items with no parents")
    result --= noParents

    def findChildren(folder: SyncFile) {
      //logger.debug("Searching for children path: " + folder.path)
      val subresult = result.filter(_.getParents().get(0).getId() == folder.driveFile.getId())
      folder.children ++= subresult.map(driveFile => {
        val isFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"
        val localFile = new java.io.File(folder.path, driveFile.getTitle)
        //logger.debug("Found child with path: " + localFile)
        val syncFile = new SyncFile(localFile, driveFile)
        if (isFolder) findChildren(syncFile)
        syncFile
      })
    }
    findChildren(root)

    root
  }
}