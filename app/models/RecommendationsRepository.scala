package models

import java.util.Date
import javax.inject.Inject

import models.RecommendationsJsonFormats._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class RecommendationInfo(email: Option[String],
                              recommendation: String,
                              submissionDate: BSONDateTime,
                              updateDate: BSONDateTime,
                              approved: Boolean = false,
                              decline: Boolean = false,
                              pending: Boolean = true,
                              done: Boolean = false,
                              upVotes: Int = 0,
                              downVotes: Int = 0,
                              _id: BSONObjectID = BSONObjectID.generate())

object RecommendationsJsonFormats {

  import play.api.libs.json.Json

  implicit val recommendationsFormat = Json.format[RecommendationInfo]
}

class RecommendationsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("recommendations"))

  def insert(recommendationInfo: RecommendationInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(recommendationInfo))

  def approveRecommendation(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument("$set" -> BSONDocument("approved" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def declineRecommendation(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument("$set" -> BSONDocument("decline" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def getAllRecommendations(implicit ex: ExecutionContext): Future[List[RecommendationInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
          cursor[RecommendationInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[RecommendationInfo]]()))
  }

  def paginate(pageNumber: Int,
               filter: String = "all",
               pageSize: Int = 10)(implicit ex: ExecutionContext): Future[List[RecommendationInfo]] = {

    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    val condition = filter match {
      case "all"      => Json.obj()
      case "approved" => Json.obj("approved" -> true)
      case "decline"  => Json.obj("decline" -> true)
      case "pending"  => Json.obj("pending" -> true)
      case "done"     => Json.obj("done" -> true)
      case _          => Json.obj()
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .options(queryOptions)
          .cursor[RecommendationInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[RecommendationInfo]]()))
  }

  def updateDate(id: String, updateDate: Date)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument("updateDate" -> updateDate)

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def upVote(id: String, alreadyVoted: Boolean)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))

    val modifier =
      if (alreadyVoted) {
        BSONDocument("$inc" -> BSONDocument("upVotes" -> 1, "downVotes" -> -1))
      } else {
        BSONDocument("$inc" -> BSONDocument("upVotes" -> 1))
      }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def downVote(id: String, alreadyVoted: Boolean)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))

    val modifier =
      if (alreadyVoted) {
        BSONDocument("$inc" -> BSONDocument("upVotes" -> -1, "downVotes" -> 1))
      } else {
        BSONDocument("$inc" -> BSONDocument("downVotes" -> 1))
      }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

}