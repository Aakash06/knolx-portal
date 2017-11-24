package actors

import javax.inject.Inject

import akka.actor.Actor
import com.google.api.services.youtube.model.VideoCategory
import services.YoutubeService


object ConfiguredYouTubeDetailsActor {

  trait Factory {
    def apply(): Actor
  }

}

case object Categories

case class VideoDetails(videoId: String,
                        title: String,
                        description: Option[String],
                        tags: List[String],
                        status: String,
                        category: String)

class YouTubeDetailsActor @Inject()(youtubeService: YoutubeService) extends Actor {

  override def receive: Receive = {
    case Categories                 => sender() ! returnCategoryList
    case videoDetails: VideoDetails => sender() ! update(videoDetails)
  }

  def returnCategoryList: List[VideoCategory] = {
    youtubeService.getCategoryList
  }

  def update(videoDetails: VideoDetails): String = youtubeService.update(videoDetails)

}
