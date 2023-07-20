package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class ObjectActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "readObject" => read(request)
      case "createObject" => create(request)
      case "updateObject" => update(request)
      case "retireObject" => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "Object not found with identifier: " + node.getIdentifier)
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields,null, request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put("object", metadata)
    })
  }
  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    try {
      RequestUtil.restrictProperties(request)
      DataNode.create(request).map(node => {
        ResponseHandler.OK.put("identifier", node.getIdentifier).put("node_id", node.getIdentifier)
          .put("versionKey", node.getMetadata.get("versionKey"))
      })
    } catch {
      case e: Exception => throw new ClientException("SERVER_ERROR", "The schema does not exist for the provided object.")
    }
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    if (StringUtils.isBlank(request.getRequest.getOrDefault("versionKey", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_REQUEST", "Please Provide Version Key!")
    try {
      RequestUtil.restrictProperties(request)
      DataNode.update(request).map(node => {
        val identifier: String = node.getIdentifier.replace(".img", "")
        ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
          .put("versionKey", node.getMetadata.get("versionKey"))
      })
    } catch {
      case e: Exception => throw new ClientException("SERVER_ERROR", "The schema does not exist for the provided object.")
    }
  }

  @throws[Exception]
  private def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    try {
      DataNode.update(request).map(node => {
        ResponseHandler.OK.put("node_id", node.getIdentifier)
          .put("identifier", node.getIdentifier)
      })
    } catch {
      case e: Exception => throw new ClientException("SERVER_ERROR", "The schema does not exist for the provided object.")
    }
  }

}
