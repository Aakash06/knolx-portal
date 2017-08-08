package models

import javax.inject.Inject

import models.FeedbackFormsResponseFormat._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONDocumentWriter, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class QuestionResponse(question: String, options: List[String], response: String)

case class FeedbackFormsResponse(email: String,
                                 userId: String,
                                 sessionId: String,
                                 sessionTopic: String,
                                 feedbackResponse: List[QuestionResponse],
                                 responseDate: BSONDateTime,
                                 _id: BSONObjectID = BSONObjectID.generate)

object FeedbackFormsResponseFormat {

  import play.api.libs.json.Json

  implicit val questionResponseFormat = Json.format[QuestionResponse]
  implicit val feedbackFormResponseFormat = Json.format[FeedbackFormsResponse]

  implicit object QuestionWriter extends BSONDocumentWriter[QuestionResponse] {
    def write(questionResponse: QuestionResponse): BSONDocument = BSONDocument(
      "question" -> questionResponse.question,
      "options" -> questionResponse.options,
      "response" -> questionResponse.response)
  }

}

class FeedbackFormsResponseRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("feedbackformsresponse"))

  def upsert(feedbackFormsResponse: FeedbackFormsResponse)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("userId" -> feedbackFormsResponse.userId, "sessionId" -> feedbackFormsResponse.sessionId)

    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "email" -> feedbackFormsResponse.email,
          "userId" -> feedbackFormsResponse.userId,
          "sessionTopic" -> feedbackFormsResponse.sessionTopic,
          "sessionId" -> feedbackFormsResponse.sessionId,
          "feedbackResponse" -> feedbackFormsResponse.feedbackResponse,
          "responseDate" -> feedbackFormsResponse.responseDate
        ))

    collection.flatMap(_.update(selector, modifier, upsert = true))
  }

  def getByUsersSession(userId: String, SessionId: String): Future[Option[FeedbackFormsResponse]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("userId" -> userId, "sessionId" -> SessionId))
          .cursor[FeedbackFormsResponse](ReadPreference.Primary).headOption)


}