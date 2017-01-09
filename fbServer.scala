import spray.routing._
import akka.io._
import spray.can.Http
import akka.actor._
import spray.http._
import spray.json._
import MediaTypes._
import akka.pattern.ask
import akka.util.Timeout
import spray.httpx.SprayJsonSupport
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.{Success,Failure}
import DefaultJsonProtocol._
import spray.routing.RejectionHandler.Default
import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write,writePretty}

sealed trait serviceMessage

object fbServer extends App with SimpleRoutingApp{
  implicit val system = ActorSystem("fbServer")
  val service = system.actorOf(Props[SprayActor], "SprayActor")
  IO(Http) ! Http.Bind(service, interface = "localhost", port = 8000)
  
  println("Server Hosted")
}

case class createUserReceive(userName: String)
case class profileResponse(profile: Map[String,String])
case class friendListResponse(friendList: Map[String,String])
case class updateProfileReceive(detailName: String, detailValue: String)
case class addFriendReceive(userId:Int, friendId: Int)
case class createPostReceive(userId:Int, message: String)
case class updatePostReceive(postId:Int, message: String)
case class deletePostReceive(postId:Int)
case class getPostResponse(posts: Map[String,String])
case class getPageResponse(page: Map[String,String])
case class createPageReceive(pageName: String)
case class createPagePostReceive(pageId: Int, message: String)
case class followerListResponse(followerList: Map[String,String])
case class addPageFollowerReceive(userId: Int, pageId: Int)
object serviceProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val credsJson = jsonFormat1(createUserReceive.apply)
    implicit val credsJson3 = jsonFormat1(profileResponse.apply)
    implicit val credsJson4 = jsonFormat1(friendListResponse.apply)
    implicit val credsJson5 = jsonFormat2(updateProfileReceive.apply)
    implicit val credsJson6 = jsonFormat2(addFriendReceive.apply)
    implicit val credsJson7 = jsonFormat2(createPostReceive.apply)
    implicit val credsJson8 = jsonFormat2(updatePostReceive.apply)
    implicit val credsJson9 = jsonFormat1(deletePostReceive.apply)
    implicit val credsJson10 = jsonFormat1(getPostResponse.apply)
    implicit val credsJson11 = jsonFormat1(getPageResponse.apply)
    implicit val credsJson12 = jsonFormat1(createPageReceive.apply)
    implicit val credsJson13 = jsonFormat2(createPagePostReceive.apply)
    implicit val credsJson14 = jsonFormat1(followerListResponse.apply)
    implicit val credsJson15 = jsonFormat2(addPageFollowerReceive.apply)
}

class SprayActor extends Actor with SprayService{
  def actorRefFactory = context
  def receive = runRoute(
      //default ~ hello ~
                          SprayCreateUserRoute ~ 
                          SprayUpdateProfileRoute ~ SprayGetProfileRoute ~ 
                          SprayAddFriendRoute ~ SprayGetFriendListRoute ~ SprayCreatePostRoute ~
                          SprayGetPostsRoute ~ SprayUpdatePostRoute ~ SprayDeletePostRoute ~
                          SprayGetPostCountRoute ~ SprayGetPageRoute ~ SprayCreatePageRoute ~ 
                          SprayCreatePagePostRoute ~ SprayAddPageFollowerRoute ~ SprayGetPageFollowerListRoute)

}

trait SprayService extends HttpService {
  import serviceProtocol._
  import scala.concurrent.ExecutionContext.Implicits.global

  def default = {
      path("") {
      get {
        complete {
          "<h1>Server Is running</h1>"
        }
      }
    }
  }  
  def hello = {
      path("hello") {
      get {
        complete {
          "<h1>Say hello to spray</h1>"
        }
      }
    }
  }

  def SprayCreateUserRoute = {
    path("createUser"){
      post {
        entity(as[createUserReceive]) { createDetails =>
          val result = CreateUser(createDetails.userName)
          complete(result.toString)
        }
      }
    }
  }
  
  def SprayUpdateProfileRoute = {
    path(Segment / "updateProfile"){ userId =>
      post {
        entity(as[updateProfileReceive]) { updateDetails =>
          var result = UpdateProfile(userId.toInt, updateDetails.detailName, updateDetails.detailValue)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetProfileRoute = {
    path(Segment / "getProfile"){ userId =>
      get { 
        var result = GetProfile(userId.toInt)
        complete(profileResponse(result))
      }
    }
  }
  def SprayAddFriendRoute = {
    path(Segment / "addFriend"){ userId =>
      post {
        entity(as[addFriendReceive]) { frndDetails =>
          var result = AddFriend(frndDetails.userId, frndDetails.friendId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetFriendListRoute = {
    path(Segment / "getFriendList"){ userId =>
      get { 
        var result = GetFriendList(userId.toInt)
        complete(friendListResponse(result))
      }
    }
  }
  def SprayCreatePostRoute = {
    path(Segment / "createPost"){ userId =>
      post {
        entity(as[createPostReceive]) { createPostDetails =>
          var result = CreatePost(createPostDetails.userId, createPostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPostsRoute = {
    path(Segment / "getPosts"){ userId =>
      get { 
        var result = GetPosts(userId.toInt)
        complete(getPostResponse(result))
      }
    }
  }
  def SprayUpdatePostRoute = {
    path(Segment / "updatePost"){ userId =>
      post {
        entity(as[updatePostReceive]) { updatePostDetails =>
          var result = UpdatePost(updatePostDetails.postId, updatePostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  def SprayDeletePostRoute = {
    path(Segment / "deletePost"){ userId =>
      post {
        entity(as[deletePostReceive]) { deletePostDetails =>
          var result = DeletePost(deletePostDetails.postId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPostCountRoute = {
    path(Segment / "getPostCount"){ userId =>
      get { 
        var result = GetPostCount(userId.toInt)
        complete(result.toString)
      }
    }
  }
  def SprayGetPageRoute = {
    path(Segment / "getPage"){ pageId =>
      get { 
        var result = GetPage(pageId.toInt)
        complete(getPageResponse(result))
      }
    }
  }
  def SprayCreatePageRoute = {
    path("createPage"){
      post {
        entity(as[createPageReceive]) { createPageDetails =>
          val result = CreatePage(createPageDetails.pageName)
          complete(result.toString)
        }
      }
    }
  }
  
  def SprayCreatePagePostRoute = {
    path(Segment / "createPagePost"){ pageId =>
      post {
        entity(as[createPagePostReceive]) { createPagePostDetails =>
          var result = CreatePagePost(createPagePostDetails.pageId, createPagePostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  
  def SprayAddPageFollowerRoute = {
    path(Segment / "addPageFollower"){ userId =>
      post {
        entity(as[addPageFollowerReceive]) { addPageFollowerDetails =>
          var result = AddPageFollower(addPageFollowerDetails.userId, addPageFollowerDetails.pageId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPageFollowerListRoute = {
    path(Segment / "getFollowerList"){ pageId =>
      get { 
        var result = GetPageFollowerList(pageId.toInt)
        complete(followerListResponse(result))
      }
    }
  }
  
  // Helper functions for retrieving User information
  def CreateUser(userName:String): Int = {
    Global.userIdCounter+= 10
    var userId = Global.userIdCounter 
    var newUser = new UserInfo(userId)
    Global.userdata += (newUser.userId -> newUser)
    newUser.UpdateProfile("UserName", userName)
    return  newUser.userId
  }
  def UpdateProfile(id: Int, detailName: String, detailValue: String) = {
    Global.userdata(id).UpdateProfile(detailName, detailValue)
  }
  def GetProfile(id: Int): Map[String, String] = {
    return Global.userdata(id).GetProfile()
  }
  def AddFriend(id: Int, frndId: Int) = {
    if(Global.userdata(id).friendList.contains(frndId.toString) || id == frndId){
      // Do nothing
    }else{
      Global.userdata(id).AddFriend(frndId)
      Global.userdata(frndId).AddFriend(id)
      println(Global.userdata(id).profile("UserName") + " is friend of " + Global.userdata(frndId).profile("UserName"))
    }
  }
  def GetFriendList(id: Int): Map[String, String] = {
    return Global.userdata(id).GetFriendList()
  }
  /*
  def GetFriendListDetailed(id: Int): ArrayBuffer[UserInfo] = {
    var frndList = Global.userdata(id).GetFriendList()
    var frndDetail = Map[Int, String]()
    frndList.foreach(i => frndDetail += (Global.userdata(i).userId -> Global.userdata(i).profile("userName")))
  }
  */
  def CreatePost(id:Int, msg: String) = {
      Global2.postIdCounter += 10
      var user = Global.userdata(id)
      var post = new post(Global2.postIdCounter, id, id, msg)
      Global2.postdata += (post.postId -> post)
      user.CreatePost(post.postId, post)
  }
  def GetPosts(id:Int): Map[String, String] = {
    var user = Global.userdata(id)
    return user.GetPosts()
  }
  def UpdatePost(postId: Int, msg: String) = {
    Global2.postdata(postId).message = msg
  }
  def DeletePost(postId: Int) = {
    var post = Global2.postdata(postId)
    var userId = post.sharedToWallOf
    
    Global.userdata(userId).DeletePost(postId)
    //Global2.postdata -= (postId -> post)
  }
  def GetPostCount(id: Int): Int = {
    return Global.userdata(id).PostCount()
  }
  def CreatePage(pageName:String): Int = {
    Global3.pageIdCounter += 10
    var pageId = Global3.pageIdCounter 
    var newPage = new PageInfo(pageId, pageName)
    Global3.pagedata += (newPage.pageId -> newPage)
    return  newPage.pageId
  }
  def CreatePagePost(id:Int, msg: String) = {
      Global2.postIdCounter += 10
      var page = Global3.pagedata(id)
      var post = new post(Global2.postIdCounter, id, id, msg)
      Global2.postdata += (post.postId -> post)
      page.CreatePost(post.postId, post)
  }
  def GetPage(id: Int): Map[String,String] = {
    var page = Global3.pagedata(id)
    return page.GetPosts()
  }
  def AddPageFollower(id: Int, pId: Int) = {
    Global3.pagedata(pId).AddFollower(id)
  }
  def GetPageFollowerList(id: Int): Map[String, String] = {
    return Global3.pagedata(id).GetFollowerList()
  }
}

class UserInfo(id:Int){													// class for users information
  import serviceProtocol._
  
	var userId : Int = id
	var posts = Map[String,post]()
	var profile = Map[String,String]()
  var friendList = Map[String,String]()
  var pages = Map[String,String]()

  def AddFriend(frndId:Int) = {
    var frnd = Global.userdata(frndId)
    friendList += (frndId.toString -> frnd.profile("UserName"))

    
    for ((postId, post) <- posts){
      post.UpdateShare(frndId)
    }
  }
  def GetFriendList():Map[String,String] = {
    return friendList
  }
  def UpdateProfile(detailName: String, detail: String) = {
    //UserName, Gender, Age, Email, MobileNumber
    /*
    profile += ("UserName" -> userName)
    profile += ("Gender" -> "Male")
    profile += ("Age" -> "24")
    profile += ("Email" -> (userName + "@facebook.com"))
    profile += ("MobileNumber" -> "99999")  
    */
//    if(detailName == "UserName" || detailName == "Gender" || 
//        detailName == "Age" || detailName == "Email" || detailName == "MobileNumber"){
    println("user : "+ userId + "; profile update : " + detailName + " : " + detail)
      profile += (detailName -> detail)
  //  }
  } 
  def GetProfile():Map[String, String] = {
    return profile
  }
  def CreatePost(id: Int, pst: post) = {
    posts += (id.toString -> pst)
    println("post Created : " + id + "; Message : " + pst.message)
    friendList foreach{
      case(i, frnd) => pst.UpdateShare(i.toInt) 
    }
  }
  def GetPosts(): Map[String,String] = {
    var postdata = Map[String,String]()
    posts foreach{
      case (postId, post) => postdata += (postId.toString() -> post.message)
    }
    return postdata
  }
  def UpdatePost(id: String, msg: String) = {
    posts(id).UpdatePost(msg)
  }
  def DeletePost(id: Int) = {
    //remove(posts(id))
    posts -= id.toString
  }
  def PostCount(): Int = {
    return posts.size
  }
  def GetAllPost(): Map[String, post] = {
    return posts
  }
  def FollowPage(pageId: Int) = {
    pages += (pageId.toString -> Global3.pagedata(pageId).pageName)
  }
}

class post(id: Int, by: Int, to: Int, msg: String){
  var message = msg
  var postId: Int = id
	var sharedBy: Int = by
	var sharedToWallOf = to
	var sharedTo = new ArrayBuffer[Int]
  
  def UpdateShare(userId: Int) = {
    sharedTo += userId
  }
  def UpdatePost(msg: String) = {
    message = msg
  }
}


class PageInfo(id:Int, name: String){													// class for Pages information
  import serviceProtocol._
  
	var pageId : Int = id
	var pageName : String = name
	var posts = Map[String,post]()
  var followerList = Map[String,String]() 

  def AddFollower(flwId:Int) = {
    var flw = Global.userdata(flwId)
    followerList += (flwId.toString -> flw.profile("UserName"))
    println("User " + flw.profile("UserName") + " is following page " + pageName )
    flw.FollowPage(pageId)
  }
  def CreatePost(id: Int, pst: post) = {
    posts += (id.toString -> pst)
    followerList foreach{
      case(i, frnd) => pst.UpdateShare(i.toInt) 
    }
    println("Page Post Created : " + id + " - " + pst.message)
  }
  def GetFollowerList():Map[String,String] = {
    return followerList
  }
  def GetPosts(): Map[String,String] = {
    var postdata = Map[String,String]()
    posts foreach{
      case (postId, post) => postdata += (postId.toString() -> post.message)
    }
    
    return postdata
  }
  def UpdatePost(id: String, msg: String) = {
    posts(id).UpdatePost(msg)
  }
  def DeletePost(id: Int) = {
    //remove(posts(id))
    posts -= id.toString
  }
  
}


object Global {
	var userdata = scala.collection.mutable.Map[Int,UserInfo]()
	var userIdCounter: Int = 1
}

object Global2 {
  var postdata = scala.collection.mutable.Map[Int,post]()
	var postIdCounter: Int = 5
}

object Global3 {
	var pagedata = scala.collection.mutable.Map[Int,PageInfo]()
	var pageIdCounter: Int = 9
}