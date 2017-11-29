package controllers

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.TimeZone

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.mvc.Results
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionsCategoryControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()

  private val categoryId = BSONObjectID.generate()

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  private val emailObject = Future.successful(Some(UserInfo("test@knoldus.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, coreMember = false, superUser = true, BSONDateTime(date.getTime), 0, _id)))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val controller =
      new SessionsCategoryController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        categoriesRepository,
        dateTimeUtility,
        knolxControllerComponent
      )
    val categoriesRepository = mock[CategoriesRepository]
    val sessionsRepository = mock[SessionsRepository]
    val dateTimeUtility = mock[DateTimeUtility]


    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Sessions Category Controller" should {

    "render category page" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)

      val result = controller.renderCategoryPage()(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "add primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("Backend") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = "Backend"
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "not add when primary category is empty" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = ""
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when primary category already exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("Front End") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = "Front End"
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "add sub category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = "Front End"
      val subCategory = "Scala"
      val result = controller.addSubCategory(category, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "not add when sub category is empty" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = "Front End"
      val subCategory = ""
      val result = controller.addSubCategory(category, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when sub category already exists" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      val category = "Front End"
      val subCategory = "Angular JS"
      val result = controller.addSubCategory(category, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "modify primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "front end") returns updateWriteResult
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      sessionsRepository.updateCategoryOnChange("Front End", "front end") returns updateWriteResult
      categoriesRepository.getCategories returns  Future(categories)
      val result = controller.modifyPrimaryCategory(categoryId.stringify, "front end")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not modify primary category when it does not exist" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "backend") returns updateWriteResult
      val categories: List[CategoryInfo] = List(CategoryInfo("Backend",List("Angular JS","HTML"),categoryId))
      sessionsRepository.updateCategoryOnChange("Backend", "") returns updateWriteResult
      categoriesRepository.getCategories returns  Future(categories)
      val result = controller.modifyPrimaryCategory(categoryId.stringify, "backend")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "modify sub-category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.modifySubCategory("Front End", "HTML", "html") returns updateWriteResult
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      sessionsRepository.updateSubCategoryOnChange("HTML", "html") returns updateWriteResult
      categoriesRepository.getCategories returns  Future(categories)
      val result = controller.modifySubCategory("Front End", "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK

    }

    "get sub-category by primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)

      val result = controller.getSubCategoryByPrimaryCategory("Front End")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not get sub-category by primary category when is does not exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.getSubCategoryByPrimaryCategory("Backend")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "delete primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List(),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      sessionsRepository.updateCategoryOnChange("Front End", "") returns updateWriteResult
      categoriesRepository.deletePrimaryCategory(categoryId.stringify) returns updateWriteResult

      val result = controller.deletePrimaryCategory(categoryId.toString())(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not delete primary category when it does not exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List(),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      sessionsRepository.updateCategoryOnChange("Front End", "") returns updateWriteResult
      categoriesRepository.deletePrimaryCategory(categoryId.stringify) returns updateWriteResult

      val result = controller.deletePrimaryCategory(categoryId.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "delete sub-category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(Some("Front End"))
      sessionsRepository.updateSubCategoryOnChange("HTML", "") returns updateWriteResult
      categoriesRepository.deleteSubCategory("Front End","HTML") returns updateWriteResult

      val result = controller.deleteSubCategory("Front End","HTML")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not delete sub-category when it does not exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)
      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(Some("Front End"))
      sessionsRepository.updateSubCategoryOnChange("React", "") returns updateWriteResult
      categoriesRepository.deleteSubCategory("Front End","React") returns updateWriteResult

      val result = controller.deleteSubCategory("Front End","React")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "get topics by sub-category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val sessionInfo = List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
        1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, _id))
      sessionsRepository.getSessionByCategory("category", "subCategory") returns Future(sessionInfo)

      val result = controller.getTopicsBySubCategory("category", "subCategory")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "get all categories" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End",List("Angular JS","HTML"),categoryId))
      categoriesRepository.getCategories returns  Future(categories)

      val result = controller.getCategory()(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK

    }
  }

}
